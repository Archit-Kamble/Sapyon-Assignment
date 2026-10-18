package com.agencydesk.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.agencydesk.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs before every /api request except login and invite acceptance.
 * Turns the Bearer token into request attributes; the Actor is only set once a membership is chosen.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String TOKEN = "auth.token";
    static final String USER_ID = "auth.userId";
    static final String ACTOR = "auth.actor";

    private final SessionRepository sessions;

    public AuthInterceptor(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("missing token");
        }
        String token = header.substring("Bearer ".length());
        var session = sessions.findValid(token)
                .orElseThrow(() -> ApiException.unauthorized("invalid or expired session"));
        request.setAttribute(TOKEN, token);
        request.setAttribute(USER_ID, session.userId());
        if (session.membershipId() != null) {
            // Re-checked on every request: a deactivated membership loses access immediately.
            sessions.actorFor(session.userId(), session.membershipId())
                    .ifPresent(actor -> request.setAttribute(ACTOR, actor));
        }
        return true;
    }
}
