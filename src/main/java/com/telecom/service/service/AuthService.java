package com.telecom.service.service;

import com.telecom.service.domain.Role;
import com.telecom.service.domain.User;
import com.telecom.service.dto.Dtos.*;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.security.JwtService;
import com.telecom.service.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, UserService userService, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.userService = userService;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    public UserResponse register(RegisterRequest request) {
        return UserResponse.from(userService.create(request, Role.CUSTOMER));
    }

    /**
     * Deliberately NOT @Transactional: failed-attempt counters must be saved even though we throw afterwards.
     * Every failure returns the same message so accounts cannot be enumerated.
     */
    public AuthResponse login(LoginRequest request) {
        String id = request.emailOrPhone().trim();
        Optional<User> found = id.contains("@") ? users.findByEmail(id.toLowerCase()) : users.findByPhone(id);

        if (found.isEmpty()) {
            encoder.matches(request.password(), dummyHash); // keep timing similar to a real check
            throw invalid();
        }
        User user = found.get();
        Instant now = Instant.now();

        if (!user.isEnabled() || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw invalid();
        }
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                attempts = 0;
            }
            user.setFailedAttempts(attempts);
            users.save(user);
            throw invalid();
        }
        if (user.getFailedAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            users.save(user);
        }
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), UserResponse.from(user));
    }

    private ApiException invalid() {
        return ApiException.unauthorized("Invalid credentials, or the account is temporarily locked");
    }
}
