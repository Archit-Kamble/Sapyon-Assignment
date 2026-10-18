package com.agencydesk.invite;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.auth.AuthService;
import com.agencydesk.common.ApiException;

/**
 * Invite races, handled by the database rather than by "check then insert" code:
 *  - Resend:        partial UNIQUE index (agency_id, email) WHERE status = 'pending'
 *                   -> INSERT ... ON CONFLICT DO NOTHING, then return the one pending invite.
 *  - Double accept: one UPDATE ... WHERE status = 'pending'. Postgres locks the row, so of two
 *                   concurrent accepts exactly one sees 'pending'; the other updates 0 rows -> 409.
 *  - Duplicate account: users.email is UNIQUE; INSERT ... ON CONFLICT (email) DO NOTHING.
 *    If the person already has an account (e.g. a client of another agency) we reuse it and
 *    just add a membership: one person, many agencies.
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ROLES = Set.of("agency_admin", "agency_member", "client_user");

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuthService auth;

    public InviteService(JdbcClient jdbc, AccessService access, AuthService auth) {
        this.jdbc = jdbc;
        this.access = access;
        this.auth = auth;
    }

    public record InviteView(long id, String email, String role, Long clientId, String token, String status,
                             OffsetDateTime createdAt, OffsetDateTime acceptedAt) {}

    public record AcceptResult(long userId, long membershipId, long agencyId, String role, boolean newAccount) {}

    record Accepted(long id, long agencyId, String email, String role, Long clientId) {}

    private static final String SELECT =
            "SELECT id, email, role, client_id, token, status, created_at, accepted_at FROM invites ";

    public List<InviteView> list(Actor actor) {
        access.requireAdmin(actor);
        return jdbc.sql(SELECT + "WHERE agency_id = ? ORDER BY id DESC")
                .param(actor.agencyId())
                .query(InviteView.class)
                .list();
    }

    /** Idempotent: inviting the same email again returns the existing pending invite (same token). */
    public InviteView invite(Actor actor, String rawEmail, String role, Long clientId) {
        access.requireAdmin(actor);
        if (rawEmail == null || !rawEmail.contains("@")) {
            throw ApiException.badRequest("valid email required");
        }
        if (!ROLES.contains(role)) {
            throw ApiException.badRequest("invalid role");
        }
        if ("client_user".equals(role) != (clientId != null)) {
            throw ApiException.badRequest("clientId is required for client_user and only for client_user");
        }
        String email = rawEmail.trim().toLowerCase();
        boolean alreadyMember = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM memberships m JOIN users u ON u.id = m.user_id
                               WHERE u.email = ? AND m.agency_id = ? AND m.active)
                """)
                .params(email, actor.agencyId())
                .query(Boolean.class)
                .single();
        if (alreadyMember) {
            throw ApiException.conflict("already a member of this agency");
        }
        jdbc.sql("""
                INSERT INTO invites (agency_id, email, role, client_id, token, invited_by_membership_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (agency_id, email) WHERE status = 'pending' DO NOTHING
                """)
                .params(actor.agencyId(), email, role, clientId, newToken(), actor.membershipId())
                .update();
        InviteView invite = jdbc.sql(SELECT + "WHERE agency_id = ? AND email = ? AND status = 'pending'")
                .params(actor.agencyId(), email)
                .query(InviteView.class)
                .single();
        // No mail server in this demo: the link is logged and returned instead of emailed.
        log.info("Invite link for {}: /accept?token={}", email, invite.token());
        return invite;
    }

    public void revoke(Actor actor, long inviteId) {
        access.requireAdmin(actor);
        int n = jdbc.sql("UPDATE invites SET status = 'revoked' WHERE id = ? AND agency_id = ? AND status = 'pending'")
                .params(inviteId, actor.agencyId())
                .update();
        if (n == 0) {
            throw ApiException.notFound("pending invite");
        }
    }

    @Transactional
    public AcceptResult accept(String token, String name, String password) {
        // Step 1: claim the invite atomically. Only one caller can ever get a row back.
        var claimed = jdbc.sql("""
                UPDATE invites SET status = 'accepted', accepted_at = now()
                WHERE token = ? AND status = 'pending'
                RETURNING id, agency_id, email, role, client_id
                """)
                .param(token)
                .query(Accepted.class)
                .optional();
        if (claimed.isEmpty()) {
            String status = jdbc.sql("SELECT status FROM invites WHERE token = ?")
                    .param(token).query(String.class).optional().orElse(null);
            if (status == null) {
                throw ApiException.notFound("invite");
            }
            throw ApiException.conflict("invite already " + status);
        }
        var inv = claimed.get();

        // Step 2: find or create the person. Never overwrite an existing password through an invite.
        Long userId = jdbc.sql("SELECT id FROM users WHERE email = ?").param(inv.email())
                .query(Long.class).optional().orElse(null);
        boolean newAccount = false;
        if (userId == null) {
            if (name == null || name.isBlank() || password == null || password.length() < 8) {
                throw ApiException.badRequest("name and a password of at least 8 characters are required");
            }
            userId = jdbc.sql("""
                    INSERT INTO users (email, name, password_hash) VALUES (?, ?, ?)
                    ON CONFLICT (email) DO NOTHING RETURNING id
                    """)
                    .params(inv.email(), name.trim(), auth.hash(password))
                    .query(Long.class).optional().orElse(null);
            if (userId == null) {   // someone created the same account a moment ago
                userId = jdbc.sql("SELECT id FROM users WHERE email = ?").param(inv.email()).query(Long.class).single();
            } else {
                newAccount = true;
            }
        }

        // Step 3: one membership per (user, agency). Re-joining reactivates instead of duplicating.
        long membershipId = jdbc.sql("""
                INSERT INTO memberships (user_id, agency_id, role, client_id) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, agency_id)
                DO UPDATE SET active = TRUE, role = EXCLUDED.role, client_id = EXCLUDED.client_id
                RETURNING id
                """)
                .params(userId, inv.agencyId(), inv.role(), inv.clientId())
                .query(Long.class)
                .single();
        jdbc.sql("UPDATE invites SET accepted_user_id = ? WHERE id = ?").params(userId, inv.id()).update();
        return new AcceptResult(userId, membershipId, inv.agencyId(), inv.role(), newAccount);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
