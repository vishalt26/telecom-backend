package com.telecom.service.service;

import com.telecom.service.domain.Role;
import com.telecom.service.domain.User;
import com.telecom.service.dto.Dtos;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.repo.NotificationRepository;
import com.telecom.service.repo.TicketHistoryRepository;
import com.telecom.service.repo.TicketRepository;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for customers and employees. Admin accounts are never exposed through these methods. */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final UserService userService;
    private final TicketRepository tickets;
    private final TicketHistoryRepository history;
    private final NotificationRepository notifications;

    public AdminUserService(UserRepository users, UserService userService, TicketRepository tickets,
                            TicketHistoryRepository history, NotificationRepository notifications) {
        this.users = users;
        this.userService = userService;
        this.tickets = tickets;
        this.history = history;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        return PageResponse.of(users.search(role, query, Dtos.pageable(page, size)).map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Role role, Long id) {
        return UserResponse.from(find(role, id));
    }

    @Transactional
    public UserResponse create(Role role, RegisterRequest request) {
        return UserResponse.from(userService.create(request, role));
    }

    @Transactional
    public UserResponse update(Role role, Long id, AdminUpdateUserRequest r) {
        User u = find(role, id);
        userService.updateProfile(u, r.name(), r.phone());
        if (r.enabled() != null) {
            u.setEnabled(r.enabled());
            if (r.enabled()) {
                u.setFailedAttempts(0);
                u.setLockedUntil(null);
            }
        }
        if (r.newPassword() != null && !r.newPassword().isBlank()) {
            userService.resetPassword(u, r.newPassword());
        }
        return UserResponse.from(users.save(u));
    }

    /** Deleting a customer erases their tickets and history (right to erasure); employees' tickets become unassigned. */
    @Transactional
    public void delete(Role role, Long id) {
        User u = find(role, id);
        if (role == Role.CUSTOMER) {
            history.deleteForCustomer(id);
            notifications.deleteForCustomerTickets(id);
            tickets.deleteByCustomerId(id);
        } else {
            tickets.unassignEmployee(id);
        }
        notifications.deleteByUserId(id);
        users.delete(u);
    }

    private User find(Role role, Long id) {
        return users.findByIdAndRole(id, role)
                .orElseThrow(() -> ApiException.notFound(role == Role.CUSTOMER ? "Customer not found" : "Employee not found"));
    }
}
