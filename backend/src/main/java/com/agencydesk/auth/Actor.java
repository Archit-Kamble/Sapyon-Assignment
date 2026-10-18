package com.agencydesk.auth;

/**
 * Who is making this request, resolved from the session token.
 * Every service method takes an Actor; agencyId comes ONLY from here, never from the request body or URL.
 */
public record Actor(long userId, long membershipId, long agencyId, Role role, Long clientId) {

    public boolean isAdmin() {
        return role == Role.agency_admin;
    }

    public boolean isClient() {
        return role == Role.client_user;
    }

    public boolean isStaff() {
        return role != Role.client_user;
    }
}
