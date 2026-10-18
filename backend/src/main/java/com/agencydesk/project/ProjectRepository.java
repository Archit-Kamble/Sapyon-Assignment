package com.agencydesk.project;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.agencydesk.access.AccessService.Scope;

@Repository
public class ProjectRepository {

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ProjectView(long id, long clientId, String clientName, String name) {}

    public record MemberView(long membershipId, String name, String email, String role) {}

    public List<ProjectView> list(Scope scope) {
        return jdbc.sql("""
                SELECT p.id, p.client_id, c.name AS client_name, p.name
                FROM projects p
                JOIN clients c ON c.id = p.client_id AND c.agency_id = p.agency_id
                WHERE %s
                ORDER BY p.id
                """.formatted(scope.sql()))
                .params(scope.params())
                .query(ProjectView.class)
                .list();
    }

    public ProjectView get(long agencyId, long projectId) {
        return jdbc.sql("""
                SELECT p.id, p.client_id, c.name AS client_name, p.name
                FROM projects p
                JOIN clients c ON c.id = p.client_id AND c.agency_id = p.agency_id
                WHERE p.id = ? AND p.agency_id = ?
                """)
                .params(projectId, agencyId)
                .query(ProjectView.class)
                .single();
    }

    public long create(long agencyId, long clientId, String name) {
        // The composite FK (client_id, agency_id) makes a project for another agency's client impossible.
        return jdbc.sql("INSERT INTO projects (agency_id, client_id, name) VALUES (?, ?, ?) RETURNING id")
                .params(agencyId, clientId, name)
                .query(Long.class)
                .single();
    }

    public List<MemberView> members(long agencyId, long projectId) {
        return jdbc.sql("""
                SELECT m.id AS membership_id, u.name, u.email, m.role
                FROM project_members pm
                JOIN memberships m ON m.id = pm.membership_id AND m.agency_id = pm.agency_id
                JOIN users u ON u.id = m.user_id
                WHERE pm.project_id = ? AND pm.agency_id = ?
                ORDER BY u.name
                """)
                .params(projectId, agencyId)
                .query(MemberView.class)
                .list();
    }

    /** Returns the membership's role if it is an active STAFF membership of this agency. */
    public String staffRole(long agencyId, long membershipId) {
        return jdbc.sql("""
                SELECT role FROM memberships
                WHERE id = ? AND agency_id = ? AND active AND role <> 'client_user'
                """)
                .params(membershipId, agencyId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public void addMember(long agencyId, long projectId, long membershipId) {
        jdbc.sql("""
                INSERT INTO project_members (project_id, membership_id, agency_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """)
                .params(projectId, membershipId, agencyId)
                .update();
    }

    public int removeMember(long agencyId, long projectId, long membershipId) {
        return jdbc.sql("DELETE FROM project_members WHERE project_id = ? AND membership_id = ? AND agency_id = ?")
                .params(projectId, membershipId, agencyId)
                .update();
    }

    /** Edge case "member removed mid-task": clear the owner and flag it; the task and its history stay. */
    public int unassignTasks(long agencyId, long projectId, long membershipId) {
        return jdbc.sql("""
                UPDATE tasks SET assignee_membership_id = NULL, needs_reassignment = TRUE
                WHERE agency_id = ? AND project_id = ? AND assignee_membership_id = ?
                """)
                .params(agencyId, projectId, membershipId)
                .update();
    }
}
