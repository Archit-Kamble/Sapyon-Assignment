package com.agencydesk.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record UserRow(long id, String email, String name, String passwordHash) {}

    public record SessionRow(String token, long userId, Long membershipId) {}

    /** A membership plus labels, for the "pick an agency" screen. */
    public record MembershipView(long membershipId, long agencyId, String agencyName, String role,
                                 Long clientId, String clientName) {}

    public Optional<UserRow> findUserByEmail(String email) {
        return jdbc.sql("SELECT id, email, name, password_hash FROM users WHERE email = ?")
                .param(email)
                .query(UserRow.class)
                .optional();
    }

    public List<MembershipView> activeMemberships(long userId) {
        return jdbc.sql("""
                SELECT m.id AS membership_id, m.agency_id, a.name AS agency_name, m.role,
                       m.client_id, c.name AS client_name
                FROM memberships m
                JOIN agencies a ON a.id = m.agency_id
                LEFT JOIN clients c ON c.id = m.client_id AND c.agency_id = m.agency_id
                WHERE m.user_id = ? AND m.active
                ORDER BY a.name
                """)
                .param(userId)
                .query(MembershipView.class)
                .list();
    }

    public void create(String token, long userId, OffsetDateTime expiresAt) {
        jdbc.sql("INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)")
                .params(token, userId, expiresAt)
                .update();
    }

    public Optional<SessionRow> findValid(String token) {
        return jdbc.sql("SELECT token, user_id, membership_id FROM sessions WHERE token = ? AND expires_at > now()")
                .param(token)
                .query(SessionRow.class)
                .optional();
    }

    public void selectMembership(String token, long membershipId) {
        jdbc.sql("UPDATE sessions SET membership_id = ? WHERE token = ?")
                .params(membershipId, token)
                .update();
    }

    public void delete(String token) {
        jdbc.sql("DELETE FROM sessions WHERE token = ?").param(token).update();
    }

    /** Builds the Actor for a session's chosen membership; empty if the membership was deactivated. */
    public Optional<Actor> actorFor(long userId, long membershipId) {
        return jdbc.sql("""
                SELECT user_id, id AS membership_id, agency_id, role, client_id
                FROM memberships
                WHERE id = ? AND user_id = ? AND active
                """)
                .params(membershipId, userId)
                .query((rs, n) -> new Actor(
                        rs.getLong("user_id"),
                        rs.getLong("membership_id"),
                        rs.getLong("agency_id"),
                        Role.valueOf(rs.getString("role")),
                        (Long) rs.getObject("client_id")))
                .optional();
    }
}
