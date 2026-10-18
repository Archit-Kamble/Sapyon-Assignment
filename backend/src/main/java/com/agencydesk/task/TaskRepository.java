package com.agencydesk.task;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.agencydesk.access.AccessService.Scope;

@Repository
public class TaskRepository {

    private final JdbcClient jdbc;

    public TaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record TaskView(long id, long projectId, String projectName, String title, String description,
                           String status, String priority, Long assigneeMembershipId, String assigneeName,
                           LocalDate dueDate, String visibility, boolean needsReassignment) {}

    public record Filter(Long projectId, String status, String priority, Long assigneeMembershipId, String q) {}

    private static final String SELECT = """
            SELECT t.id, t.project_id, p.name AS project_name, t.title, t.description, t.status, t.priority,
                   t.assignee_membership_id, u.name AS assignee_name, t.due_date, t.visibility,
                   t.needs_reassignment
            FROM tasks t
            JOIN projects p ON p.id = t.project_id AND p.agency_id = t.agency_id
            LEFT JOIN memberships m ON m.id = t.assignee_membership_id AND m.agency_id = t.agency_id
            LEFT JOIN users u ON u.id = m.user_id
            """;

    /**
     * List, filter and search all go through this one query, and the access scope is always AND-ed in.
     * So a client searching for "budget" cannot match an internal task: the visibility rule is part of WHERE.
     */
    public List<TaskView> list(Scope scope, Filter f) {
        StringBuilder where = new StringBuilder(scope.sql());
        Map<String, Object> params = new HashMap<>(scope.params());
        if (f.projectId() != null) {
            where.append(" AND t.project_id = :projectId");
            params.put("projectId", f.projectId());
        }
        if (f.status() != null) {
            where.append(" AND t.status = :status");
            params.put("status", f.status());
        }
        if (f.priority() != null) {
            where.append(" AND t.priority = :priority");
            params.put("priority", f.priority());
        }
        if (f.assigneeMembershipId() != null) {
            where.append(" AND t.assignee_membership_id = :assignee");
            params.put("assignee", f.assigneeMembershipId());
        }
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" AND (t.title ILIKE :q OR t.description ILIKE :q)");
            params.put("q", "%" + f.q().trim() + "%");
        }
        return jdbc.sql(SELECT + " WHERE " + where + " ORDER BY t.id")
                .params(params)
                .query(TaskView.class)
                .list();
    }

    public TaskView get(long agencyId, long taskId) {
        return jdbc.sql(SELECT + " WHERE t.id = ? AND t.agency_id = ?")
                .params(taskId, agencyId)
                .query(TaskView.class)
                .single();
    }

    public long create(long agencyId, long projectId, String title, String description, String status,
                       String priority, Long assignee, LocalDate dueDate, String visibility) {
        return jdbc.sql("""
                INSERT INTO tasks (agency_id, project_id, title, description, status, priority,
                                   assignee_membership_id, due_date, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """)
                .params(agencyId, projectId, title, description, status, priority, assignee, dueDate, visibility)
                .query(Long.class)
                .single();
    }

    public void update(long agencyId, long taskId, String title, String description, String status,
                       String priority, Long assignee, LocalDate dueDate, String visibility,
                       boolean needsReassignment) {
        jdbc.sql("""
                UPDATE tasks SET title = ?, description = ?, status = ?, priority = ?, assignee_membership_id = ?,
                                 due_date = ?, visibility = ?, needs_reassignment = ?
                WHERE id = ? AND agency_id = ?
                """)
                .params(title, description, status, priority, assignee, dueDate, visibility, needsReassignment,
                        taskId, agencyId)
                .update();
    }
}
