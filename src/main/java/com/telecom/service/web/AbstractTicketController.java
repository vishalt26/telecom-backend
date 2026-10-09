package com.telecom.service.web;

import com.telecom.service.domain.TicketStatus;
import com.telecom.service.domain.TicketType;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.security.CurrentUser;
import com.telecom.service.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Shared endpoints for complaints and service requests; subclasses only fix the type and base path. */
public abstract class AbstractTicketController {

    private final TicketService tickets;
    private final CurrentUser currentUser;

    protected AbstractTicketController(TicketService tickets, CurrentUser currentUser) {
        this.tickets = tickets;
        this.currentUser = currentUser;
    }

    protected abstract TicketType type();

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse create(@Valid @RequestBody TicketRequest request) {
        return tickets.create(type(), request, currentUser.get());
    }

    @GetMapping
    public PageResponse<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return tickets.list(type(), status, currentUser.get(), page, size);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return tickets.get(type(), id, currentUser.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody TicketRequest request) {
        return tickets.update(type(), id, request, currentUser.get());
    }

    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return tickets.changeStatus(type(), id, request, currentUser.get());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return tickets.assign(type(), id, request.employeeId(), currentUser.get());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public void delete(@PathVariable Long id) {
        tickets.delete(type(), id, currentUser.get());
    }
}
