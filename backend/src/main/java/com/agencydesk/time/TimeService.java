package com.agencydesk.time;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;

/** Time tracking is agency-side only: clients see hour totals on the dashboard, never individual entries. */
@Service
public class TimeService {

    private final JdbcClient jdbc;
    private final AccessService access;

    public TimeService(JdbcClient jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record TimeEntryView(long id, long taskId, String memberName, int minutes, String note,
                                LocalDate entryDate) {}

    public record MemberHours(String memberName, double hours) {}

    public List<TimeEntryView> list(Actor actor, long taskId) {
        access.requireStaff(actor);
        access.requireTask(actor, taskId);
        return jdbc.sql("""
                SELECT te.id, te.task_id, u.name AS member_name, te.minutes, te.note, te.entry_date
                FROM time_entries te
                JOIN memberships m ON m.id = te.membership_id AND m.agency_id = te.agency_id
                JOIN users u ON u.id = m.user_id
                WHERE te.task_id = ? AND te.agency_id = ?
                ORDER BY te.entry_date, te.id
                """)
                .params(taskId, actor.agencyId())
                .query(TimeEntryView.class)
                .list();
    }

    public TimeEntryView log(Actor actor, long taskId, Integer minutes, String note, LocalDate date) {
        access.requireStaff(actor);
        access.requireTask(actor, taskId);
        if (minutes == null || minutes <= 0 || minutes > 24 * 60) {
            throw ApiException.badRequest("minutes must be between 1 and 1440");
        }
        long id = jdbc.sql("""
                INSERT INTO time_entries (agency_id, task_id, membership_id, minutes, note, entry_date)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """)
                .params(actor.agencyId(), taskId, actor.membershipId(), minutes, note,
                        date != null ? date : LocalDate.now())
                .query(Long.class)
                .single();
        return list(actor, taskId).stream().filter(e -> e.id() == id).findFirst().orElseThrow();
    }

    /** Per-project hours by person (staff only). */
    public List<MemberHours> projectSummary(Actor actor, long projectId) {
        access.requireStaff(actor);
        access.requireProject(actor, projectId);
        return jdbc.sql("""
                SELECT u.name AS member_name, ROUND(SUM(te.minutes) / 60.0, 2) AS hours
                FROM time_entries te
                JOIN tasks t ON t.id = te.task_id AND t.agency_id = te.agency_id
                JOIN memberships m ON m.id = te.membership_id AND m.agency_id = te.agency_id
                JOIN users u ON u.id = m.user_id
                WHERE t.project_id = ? AND te.agency_id = ?
                GROUP BY u.name
                ORDER BY hours DESC
                """)
                .params(projectId, actor.agencyId())
                .query(MemberHours.class)
                .list();
    }
}
