package com.telecom.service.web;

import com.telecom.service.dto.Dtos.*;
import com.telecom.service.security.CurrentUser;
import com.telecom.service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final CurrentUser currentUser;
    private final UserService users;

    public ProfileController(CurrentUser currentUser, UserService users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping
    public UserResponse get() {
        return UserResponse.from(currentUser.get());
    }

    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest r) {
        return UserResponse.from(users.updateProfile(currentUser.get(), r.name(), r.phone()));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest r) {
        users.changePassword(currentUser.get(), r.currentPassword(), r.newPassword());
    }
}
