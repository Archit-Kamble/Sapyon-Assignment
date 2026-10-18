package com.agencydesk.dashboard;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;

/**
 * Counts and hours are computed with the SAME task scope as the task list,
 * so a client's dashboard only counts client-visible tasks and only hours logged on them.
 */
@Service
public class DashboardService {

    private final JdbcClient jdbc;
    private final AccessService access;

    public DashboardService(JdbcClient jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record Dashboard(long projectId, String projectName, Map<String, Long> tasksByStatus,
                            long totalTasks, double hoursLogged) {}

    public Dashboard forProject(Actor actor, long projectId) {
        var project = access.requireProject(actor, projectId);
        var scope = access.taskScope(actor);
        Map<String, Object> params = new HashMap<>(scope.params());
        params.put("projectId", projectId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : new String[] {"todo", "in_progress", "review", "done"}) {
            byStatus.put(s, 0L);
        }
        jdbc.sql("""
                SELECT t.status, COUNT(*) AS n
                FROM tasks t
                JOIN projects p ON p.id = t.project_id AND p.agency_id = t.agency_id
                WHERE t.project_id = :projectId AND %s
                GROUP BY t.status
                """.formatted(scope.sql()))
                .params(params)
                .query((rs, i) -> byStatus.put(rs.getString("status"), rs.getLong("n")))
                .list();

        double hours = jdbc.sql("""
                SELECT COALESCE(ROUND(SUM(te.minutes) / 60.0, 2), 0)
                FROM time_entries te
                JOIN tasks t ON t.id = te.task_id AND t.agency_id = te.agency_id
                JOIN projects p ON p.id = t.project_id AND p.agency_id = t.agency_id
                WHERE t.project_id = :projectId AND %s
                """.formatted(scope.sql()))
                .params(params)
                .query(Double.class)
                .single();

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new Dashboard(projectId, project.name(), byStatus, total, hours);
    }
}
