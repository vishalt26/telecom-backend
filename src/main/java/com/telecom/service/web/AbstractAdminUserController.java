package com.telecom.service.web;

import com.telecom.service.domain.Role;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Admin-only CRUD (route access is restricted to ROLE_ADMIN in SecurityConfig). */
public abstract class AbstractAdminUserController {

    private final AdminUserService admin;

    protected AbstractAdminUserController(AdminUserService admin) {
        this.admin = admin;
    }

    protected abstract Role role();

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return admin.list(role(), q, page, size);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return admin.get(role(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody RegisterRequest request) {
        return admin.create(role(), request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return admin.update(role(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        admin.delete(role(), id);
    }
}
