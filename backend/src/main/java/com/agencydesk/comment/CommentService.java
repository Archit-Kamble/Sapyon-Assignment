package com.agencydesk.comment;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;

@Service
public class CommentService {

    private final JdbcClient jdbc;
    private final AccessService access;

    public CommentService(JdbcClient jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record CommentView(long id, long taskId, String authorName, String authorRole, String body,
                              String visibility, OffsetDateTime createdAt) {}

    public List<CommentView> list(Actor actor, long taskId) {
        var task = access.requireTask(actor, taskId);
        return jdbc.sql("""
                SELECT c.id, c.task_id, u.name AS author_name, m.role AS author_role, c.body, c.visibility, c.created_at
                FROM comments c
                JOIN memberships m ON m.id = c.author_membership_id AND m.agency_id = c.agency_id
                JOIN users u ON u.id = m.user_id
                WHERE c.task_id = ? AND c.agency_id = ?
                ORDER BY c.created_at, c.id
                """)
                .params(taskId, actor.agencyId())
                .query(CommentView.class)
                .stream()
                .filter(c -> access.canSeeChild(actor, task, c.visibility()))
                .toList();
    }

    public CommentView add(Actor actor, long taskId, String body, String requestedVisibility) {
        var task = access.requireTask(actor, taskId);
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("body is required");
        }
        String visibility;
        if (actor.isClient()) {
            visibility = "client";                     // a client's own comment is always visible to the client side
        } else if ("internal".equals(task.visibility())) {
            visibility = "internal";                   // child can't be more visible than parent: forced
        } else {
            visibility = "client".equals(requestedVisibility) ? "client" : "internal";   // staff default: internal
        }
        long id = jdbc.sql("""
                INSERT INTO comments (agency_id, task_id, author_membership_id, body, visibility)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """)
                .params(actor.agencyId(), taskId, actor.membershipId(), body.trim(), visibility)
                .query(Long.class)
                .single();
        return list(actor, taskId).stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }
}
