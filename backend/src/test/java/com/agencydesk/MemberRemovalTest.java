package com.agencydesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Edge case 5: removing an agency_member from a project mid-task.
 * Decision: removal revokes future access, not past history.
 *  - their tasks on that project become unassigned and flagged needs_reassignment
 *  - their comments and time entries stay (hours were really worked)
 *  - they immediately lose access to the project
 */
class MemberRemovalTest extends ApiTestBase {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void removedMemberLosesAccessTasksAreFlaggedHistoryIsKept() {
        String admin = login("admin@pixelco.test");
        String maya = login("maya@pixelco.test");
        assertThat(get("/api/tasks/1", maya).status()).isEqualTo(200);

        Res r = call("DELETE", "/api/projects/1/members/2", admin, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("tasksUnassigned").asInt()).isEqualTo(2);    // tasks 1 and 2

        var task1 = get("/api/tasks/1", admin).body();
        assertThat(task1.get("assigneeMembershipId").isNull()).isTrue();
        assertThat(task1.get("needsReassignment").asBoolean()).isTrue();

        long mayaMinutes = jdbc.sql("SELECT COALESCE(SUM(minutes), 0) FROM time_entries WHERE membership_id = 2")
                .query(Long.class).single();
        assertThat(mayaMinutes).isEqualTo(270);
        assertThat(get("/api/projects/1/dashboard", admin).body().get("hoursLogged").asDouble()).isEqualTo(6.5);

        assertThat(get("/api/projects/1", maya).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1", maya).status()).isEqualTo(404);
    }

    @Test
    void reassigningClearsTheFlagAndRemovedMemberCannotBeAssigned() {
        String admin = login("admin@pixelco.test");
        call("DELETE", "/api/projects/1/members/2", admin, null);
        assertThat(patch("/api/tasks/1", admin, "{\"assigneeMembershipId\":2}").status()).isEqualTo(400);
        var t = patch("/api/tasks/1", admin, "{\"assigneeMembershipId\":3}").body();
        assertThat(t.get("needsReassignment").asBoolean()).isFalse();
    }

    @Test
    void onlyAdminCanRemoveMembers() {
        assertThat(call("DELETE", "/api/projects/1/members/2", login("dev@pixelco.test"), null).status())
                .isEqualTo(403);
    }
}
