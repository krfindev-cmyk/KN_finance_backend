package com.fcms.service;

import com.fcms.dto.LoginRequest;
import com.fcms.dto.LoginResponse;
import com.fcms.model.AppUser;
import com.fcms.model.Role;
import com.fcms.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;

    // Simple in-memory session store: issued token -> username. Good enough for this
    // single-instance app; on restart all tokens are invalidated (users must re-login).
    private final ConcurrentHashMap<String, String> tokenToUsername = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public Optional<LoginResponse> login(LoginRequest req) {
        return appUserRepository.findByUsername(req.getUsername())
                .filter(u -> u.getPassword().equals(req.getPassword()))
                .map(u -> {
                    String token = Base64.getEncoder().encodeToString(
                            (u.getUsername() + ":" + System.currentTimeMillis()).getBytes());
                    tokenToUsername.put(token, u.getUsername());
                    return new LoginResponse(u, token);
                });
    }

    /**
     * Resolves the authenticated user for a bearer token, as issued by {@link #login}.
     * Used by controllers to enforce that only Admins can perform destructive/edit actions
     * (customer update/delete, payment edit) — this is real server-side enforcement, not
     * just a UI-level restriction.
     */
    public Optional<AppUser> resolveToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String username = tokenToUsername.get(token);
        if (username == null) return Optional.empty();
        return appUserRepository.findByUsername(username);
    }

    public boolean isAdmin(String token) {
        return resolveToken(token).map(u -> u.getRole() == Role.Admin).orElse(false);
    }
}
