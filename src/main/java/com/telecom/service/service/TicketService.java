package com.telecom.service.service;

import com.telecom.service.domain.*;
import com.telecom.service.dto.Dtos;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.repo.NotificationRepository;
import com.telecom.service.repo.TicketHistoryRepository;
import com.telecom.service.repo.TicketRepository;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Complaints and service requests share one lifecycle. Visibility rules (enforced here, not in the UI):
 * customers see their own, employees see tickets assigned to them, admins see all.
 * Tickets a caller may not see are reported as 404 so their existence is not leaked.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final TicketHistoryRepository history;
    private final NotificationRepository notificationRepo;
    private final UserRepository users;
    private final NotificationService notifications;

    public TicketService(TicketRepository tickets, TicketHistoryRepository history,
                         NotificationRepository notificationRepo, UserRepository users,
                         NotificationService notifications) {
        this.tickets = tickets;
        this.history = history;
        this.notificationRepo = notificationRepo;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public TicketResponse create(TicketType type, TicketRequest r, User actor) {
        Ticket t = new Ticket();
        t.setType(type);
        t.setCustomer(actor);
        t.setStatus(TicketStatus.PENDING);
        apply(t, r);
        tickets.save(t);
        record(t, null, TicketStatus.PENDING, "Submitted", actor);
        return toResponse(t, actor, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> list(TicketType type, TicketStatus status, User viewer, int page, int size) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("type"), type));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            switch (viewer.getRole()) {
                case CUSTOMER -> p.add(cb.equal(root.get("customer").get("id"), viewer.getId()));
                case EMPLOYEE -> p.add(cb.equal(root.get("assignee").get("id"), viewer.getId()));
                case ADMIN -> { }
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(tickets.findAll(spec, Dtos.pageable(page, size)).map(t -> toResponse(t, viewer, false)));
    }

    @Transactional
    public TicketResponse get(TicketType type, Long id, User viewer) {
        Ticket t = visible(type, id, viewer);
        notifications.markTicketRead(viewer.getId(), t.getId());
        return toResponse(t, viewer, true);
    }

    @Transactional
    public TicketResponse update(TicketType type, Long id, TicketRequest r, User actor) {
        Ticket t = visible(type, id, actor);
        if (actor.getRole() != Role.CUSTOMER) {
            throw ApiException.forbidden("Only the customer who raised this can edit its details");
        }
        if (t.getStatus() != TicketStatus.PENDING) {
            throw ApiException.conflict("Only pending items can be edited");
        }
        apply(t, r);
        tickets.save(t);
        return toResponse(t, actor, true);
    }

    @Transactional
    public TicketResponse changeStatus(TicketType type, Long id, StatusRequest r, User actor) {
        Ticket t = visible(type, id, actor);
        TicketStatus from = t.getStatus();
        TicketStatus to = r.status();

        if (actor.getRole() == Role.CUSTOMER
                && !(from == TicketStatus.RESOLVED && (to == TicketStatus.COMPLETED || to == TicketStatus.IN_PROGRESS))) {
            throw ApiException.forbidden("Customers can only confirm completion or reopen a resolved item");
        }
        if (!from.canMoveTo(to)) {
            throw ApiException.conflict("Cannot move from " + from.label() + " to " + to.label());
        }
        t.setStatus(to);
        if (to == TicketStatus.RESOLVED) {
            t.setResolvedAt(Instant.now());
        } else if (to == TicketStatus.IN_PROGRESS) {
            t.setResolvedAt(null);
        }
        tickets.save(t);
        record(t, from, to, clean(r.note()), actor);

        String what = t.getType().label() + " #" + t.getId() + " is now " + to.label().toLowerCase();
        if (actor.getRole() == Role.CUSTOMER) {
            if (t.getAssignee() != null) {
                notifications.send(t.getAssignee().getId(), what, t.getId(), t.getType());
            }
        } else {
            notifications.send(t.getCustomer().getId(),
                    "Your " + what.substring(0, 1).toLowerCase() + what.substring(1), t.getId(), t.getType());
        }
        return toResponse(t, actor, true);
    }

    @Transactional
    public TicketResponse assign(TicketType type, Long id, Long employeeId, User actor) {
        Ticket t = visible(type, id, actor);
        User employee = users.findByIdAndRole(employeeId, Role.EMPLOYEE)
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("Employee not found or disabled"));
        if (t.getStatus() == TicketStatus.COMPLETED) {
            throw ApiException.conflict("Completed items cannot be reassigned");
        }
        t.setAssignee(employee);
        tickets.save(t);
        record(t, t.getStatus(), t.getStatus(), "Assigned to " + employee.getName(), actor);
        notifications.send(employee.getId(), t.getType().label() + " #" + t.getId() + " has been assigned to you", t.getId(), t.getType());
        notifications.send(t.getCustomer().getId(),
                "Your " + t.getType().label().toLowerCase() + " #" + t.getId() + " has been assigned to a support agent", t.getId(), t.getType());
        return toResponse(t, actor, true);
    }

    @Transactional
    public void delete(TicketType type, Long id, User actor) {
        Ticket t = visible(type, id, actor);
        if (actor.getRole() == Role.CUSTOMER && t.getStatus() != TicketStatus.PENDING) {
            throw ApiException.conflict("Only pending items can be deleted");
        }
        if (actor.getRole() == Role.EMPLOYEE) {
            throw ApiException.forbidden("Employees cannot delete items");
        }
        history.deleteByTicketId(t.getId());
        notificationRepo.deleteByTicketId(t.getId());
        tickets.delete(t);
    }

    // ---- helpers ----

    private Ticket visible(TicketType type, Long id, User viewer) {
        Ticket t = tickets.findById(id)
                .filter(x -> x.getType() == type)
                .orElseThrow(() -> ApiException.notFound(type.label() + " not found"));
        boolean allowed = switch (viewer.getRole()) {
            case ADMIN -> true;
            case CUSTOMER -> t.getCustomer().getId().equals(viewer.getId());
            case EMPLOYEE -> t.getAssignee() != null && t.getAssignee().getId().equals(viewer.getId());
        };
        if (!allowed) {
            throw ApiException.notFound(type.label() + " not found");
        }
        return t;
    }

    private void apply(Ticket t, TicketRequest r) {
        t.setCategory(r.category());
        t.setSubject(r.subject().strip());
        t.setDescription(r.description().strip());
        t.setPriority(r.priority() != null ? r.priority() : Priority.MEDIUM);
        t.setPreferredDate(t.getType() == TicketType.SERVICE_REQUEST ? r.preferredDate() : null);
    }

    private void record(Ticket t, TicketStatus from, TicketStatus to, String note, User actor) {
        TicketHistory h = new TicketHistory();
        h.setTicketId(t.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(note);
        h.setChangedById(actor.getId());
        h.setChangedByName(actor.getName());
        history.save(h);
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    /** Customers get only the agent's name, never staff contact details; staff see customer contact info. */
    private TicketResponse toResponse(Ticket t, User viewer, boolean withHistory) {
        boolean staff = viewer.getRole() != Role.CUSTOMER;
        User c = t.getCustomer();
        User a = t.getAssignee();
        UserSummary customer = new UserSummary(c.getId(), c.getName(), staff ? c.getEmail() : null, staff ? c.getPhone() : null);
        UserSummary assignee = a == null ? null
                : new UserSummary(a.getId(), a.getName(), staff ? a.getEmail() : null, null);
        List<HistoryEntry> entries = withHistory
                ? history.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId()).stream().map(HistoryEntry::from).toList()
                : null;
        return new TicketResponse(t.getId(), t.getType(), t.getCategory(), t.getSubject(), t.getDescription(),
                t.getPriority(), t.getStatus(), t.getPreferredDate(), customer, assignee,
                t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(), entries);
    }
}
