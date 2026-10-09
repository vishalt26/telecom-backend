package com.telecom.service.security;

import com.telecom.service.domain.User;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the authenticated user from the DB on every call, so disabled/deleted accounts lose access immediately. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw ApiException.unauthorized("Authentication required");
        }
        long id;
        try {
            id = Long.parseLong(token.getToken().getSubject());
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized("Authentication required");
        }
        return users.findById(id)
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized("Account is not available"));
    }
}
