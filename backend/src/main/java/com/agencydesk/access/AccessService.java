package com.agencydesk.access;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;

/**
 * The single place that decides who may see what. Every endpoint calls into this class.
 *
 * Three layers, checked in this order:
 *  1. Tenant:     the row's agency_id must equal actor.agencyId()            (all roles)
 *  2. Role scope: agency_member -> only projects they are a member of
 *                 client_user   -> only projects of their own client_id
 *  3. Visibility: client_user   -> only rows marked 'client', AND the parent task must be 'client' too
 *
 * Anything that fails a check is reported as 404, not 403, so a guessed ID
 * cannot even reveal that the row exists in another tenant.
 */
@Service
public class AccessService {

    private final JdbcClient jdbc;

    public AccessService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ProjectRef(long id, long agencyId, long clientId, String name) {}

    public record TaskRef(long id, long agencyId, long projectId, long clientId,
                          String visibility, Long assigneeMembershipId) {}

    /** SQL condition plus its named parameters, to be AND-ed into a list query. */
    public record Scope(String sql, Map<String, Object> params) {}

    // ---------- role checks ----------

    public void requireStaff(Actor actor) {
        if (!actor.isStaff()) {
            throw ApiException.forbidden("agency staff only");
        }
    }

    public void requireAdmin(Actor actor) {
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("agency admin only");
        }
    }

    // ---------- single-row checks (used by every /{id} endpoint) ----------

    public ProjectRef requireProject(Actor actor, long projectId) {
        // Layer 1: the agency_id filter is in the query itself, so another tenant's row is never even loaded.
        var project = jdbc.sql("SELECT id, agency_id, client_id, name FROM projects WHERE id = ? AND agency_id = ?")
                .params(projectId, actor.agencyId())
                .query(ProjectRef.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("project"));
        // Layer 2
        if (!canSeeProject(actor, project)) {
            throw ApiException.notFound("project");
        }
        return project;
    }

    public TaskRef requireTask(Actor actor, long taskId) {
        var task = jdbc.sql("""
                SELECT t.id, t.agency_id, t.project_id, p.client_id, t.visibility, t.assignee_membership_id
                FROM tasks t
                JOIN projects p ON p.id = t.project_id AND p.agency_id = t.agency_id
                WHERE t.id = ? AND t.agency_id = ?
                """)
                .params(taskId, actor.agencyId())
                .query(TaskRef.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("task"));
        var project = new ProjectRef(task.projectId(), task.agencyId(), task.clientId(), null);
        if (!canSeeProject(actor, project)) {
            throw ApiException.notFound("task");
        }
        // Layer 3
        if (actor.isClient() && !"client".equals(task.visibility())) {
            throw ApiException.notFound("task");
        }
        return task;
    }

    /** True if an item with this visibility, hanging off this task, may be shown to the actor. */
    public boolean canSeeChild(Actor actor, TaskRef task, String childVisibility) {
        if (!actor.isClient()) {
            return true;
        }
        // A child can never be more visible than its parent.
        return "client".equals(task.visibility()) && "client".equals(childVisibility);
    }

    private boolean canSeeProject(Actor actor, ProjectRef project) {
        return switch (actor.role()) {
            case agency_admin -> true;
            case agency_member -> isProjectMember(project.id(), actor.membershipId(), actor.agencyId());
            case client_user -> actor.clientId() != null && actor.clientId() == project.clientId();
        };
    }

    public boolean isProjectMember(long projectId, long membershipId, long agencyId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM project_members
                               WHERE project_id = ? AND membership_id = ? AND agency_id = ?)
                """)
                .params(projectId, membershipId, agencyId)
                .query(Boolean.class)
                .single();
    }

    // ---------- list filters (used by every list, search and dashboard query) ----------

    /** Restricts rows of projects aliased {@code p} to what the actor may see. */
    public Scope projectScope(Actor actor) {
        return switch (actor.role()) {
            case agency_admin -> new Scope("p.agency_id = :agencyId", Map.of("agencyId", actor.agencyId()));
            case agency_member -> new Scope("""
                    p.agency_id = :agencyId AND EXISTS (
                        SELECT 1 FROM project_members pm
                        WHERE pm.project_id = p.id AND pm.agency_id = p.agency_id
                          AND pm.membership_id = :membershipId)""",
                    Map.of("agencyId", actor.agencyId(), "membershipId", actor.membershipId()));
            case client_user -> new Scope("p.agency_id = :agencyId AND p.client_id = :clientId",
                    Map.of("agencyId", actor.agencyId(), "clientId", actor.clientId()));
        };
    }

    /** Restricts rows of tasks aliased {@code t} (joined to projects {@code p}) to what the actor may see. */
    public Scope taskScope(Actor actor) {
        Scope projects = projectScope(actor);
        if (actor.isClient()) {
            return new Scope(projects.sql() + " AND t.visibility = 'client'", projects.params());
        }
        return projects;
    }
}
