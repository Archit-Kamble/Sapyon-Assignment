package com.agencydesk.auth;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.agencydesk.auth.SessionRepository.MembershipView;
import com.agencydesk.common.ApiException;

/**
 * Login is two steps because one person can belong to several agencies:
 * 1) email + password proves WHO you are (users table, global);
 * 2) choosing a membership decides WHICH agency and role this session acts as.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionRepository sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long sessionHours;

    public AuthService(SessionRepository sessions, @Value("${agencydesk.session-hours}") long sessionHours) {
        this.sessions = sessions;
        this.sessionHours = sessionHours;
    }

    public record LoginResult(String token, String name, List<MembershipView> memberships) {}

    public LoginResult login(String email, String password) {
        var user = sessions.findUserByEmail(email.trim().toLowerCase())
                .filter(u -> encoder.matches(password, u.passwordHash()))
                .orElseThrow(() -> ApiException.unauthorized("invalid email or password"));
        var memberships = sessions.activeMemberships(user.id());
        if (memberships.isEmpty()) {
            throw ApiException.forbidden("no active membership");
        }
        String token = newToken();
        sessions.create(token, user.id(), OffsetDateTime.now().plusHours(sessionHours));
        // Only one agency: select it straight away.
        if (memberships.size() == 1) {
            sessions.selectMembership(token, memberships.get(0).membershipId());
        }
        return new LoginResult(token, user.name(), memberships);
    }

    public MembershipView select(String token, long userId, long membershipId) {
        // The membership must belong to THIS user, otherwise a token could be pointed at someone else's agency.
        var chosen = sessions.activeMemberships(userId).stream()
                .filter(m -> m.membershipId() == membershipId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("membership"));
        sessions.selectMembership(token, membershipId);
        return chosen;
    }

    public List<MembershipView> memberships(long userId) {
        return sessions.activeMemberships(userId);
    }

    public void logout(String token) {
        sessions.delete(token);
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
