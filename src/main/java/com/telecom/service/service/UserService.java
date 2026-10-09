package com.telecom.service.service;

import com.telecom.service.domain.Role;
import com.telecom.service.domain.User;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account creation and profile changes shared by public registration, admin tools and the profile page. */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public User create(RegisterRequest r, Role role) {
        String email = r.email().trim().toLowerCase();
        String phone = r.phone().trim();
        if (users.existsByEmail(email) || users.existsByPhone(phone)) {
            throw ApiException.conflict("An account with this email or phone already exists");
        }
        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(role);
        try {
            return users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) { // lost a race with a concurrent signup
            throw ApiException.conflict("An account with this email or phone already exists");
        }
    }

    @Transactional
    public User updateProfile(User user, String name, String phone) {
        String cleanPhone = phone.trim();
        if (!cleanPhone.equals(user.getPhone()) && users.existsByPhone(cleanPhone)) {
            throw ApiException.conflict("This phone number is already in use");
        }
        user.setName(name.trim());
        user.setPhone(cleanPhone);
        return users.save(user);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    @Transactional
    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
    }
}
