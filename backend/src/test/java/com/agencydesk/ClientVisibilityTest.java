package com.agencydesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Edge case 2: internal content never reaches a client, on any path (list, search, filter, detail, children). */
class ClientVisibilityTest extends ApiTestBase {

    private String riyaAtPixelCo() {
        return login("riya@client.test", 5L);
    }

    @Test
    void listSearchAndFiltersHideInternalTasks() {
        String riya = riyaAtPixelCo();
        assertThat(ids(get("/api/tasks", riya).body())).containsExactlyInAnyOrder(1L, 4L);
        assertThat(ids(get("/api/tasks?projectId=1", riya).body())).containsExactlyInAnyOrder(1L, 4L);
        assertThat(ids(get("/api/tasks?q=budget", riya).body())).isEmpty();          // internal task 2
        assertThat(ids(get("/api/tasks?q=staging", riya).body())).isEmpty();         // internal task 3
        assertThat(ids(get("/api/tasks?status=todo", riya).body())).containsExactly(4L);
        assertThat(ids(get("/api/tasks?priority=low", riya).body())).isEmpty();      // only internal task 3 is low
    }

    @Test
    void internalTaskAndItsChildrenAre404ForClient() {
        String riya = riyaAtPixelCo();
        assertThat(get("/api/tasks/2", riya).status()).isEqualTo(404);
        assertThat(get("/api/tasks/2/comments", riya).status()).isEqualTo(404);
        assertThat(get("/api/tasks/2/files", riya).status()).isEqualTo(404);
        assertThat(post("/api/tasks/2/comments", riya, "{\"body\":\"peek\"}").status()).isEqualTo(404);
        assertThat(get("/api/files/2/download", riya).status()).isEqualTo(404);      // internal file id 2
    }

    @Test
    void internalCommentsOnVisibleTaskAreHidden() {
        String riya = riyaAtPixelCo();
        JsonNode comments = get("/api/tasks/1/comments", riya).body();
        assertThat(comments.size()).isEqualTo(2);
        comments.forEach(c -> assertThat(c.get("visibility").asString()).isEqualTo("client"));
        assertThat(comments.toString()).doesNotContain("picky");

        String admin = login("admin@pixelco.test");
        assertThat(get("/api/tasks/1/comments", admin).body().size()).isEqualTo(3);
    }

    @Test
    void childCanNeverBeMoreVisibleThanParent() {
        String admin = login("admin@pixelco.test");
        // Staff asks for a client-visible comment on an INTERNAL task: it is forced to internal.
        Res r = post("/api/tasks/2/comments", admin, "{\"body\":\"note\",\"visibility\":\"client\"}");
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("visibility").asString()).isEqualTo("internal");

        // Making a client-visible task internal hides its client-visible comments immediately.
        assertThat(patch("/api/tasks/1", admin, "{\"visibility\":\"internal\"}").status()).isEqualTo(200);
        String riya = riyaAtPixelCo();
        assertThat(get("/api/tasks/1/comments", riya).status()).isEqualTo(404);
        assertThat(ids(get("/api/tasks", riya).body())).containsExactly(4L);
    }

    @Test
    void dashboardCountsOnlyWhatTheViewerMaySee() {
        JsonNode client = get("/api/projects/1/dashboard", riyaAtPixelCo()).body();
        assertThat(client.get("totalTasks").asLong()).isEqualTo(2);              // tasks 1 and 4
        assertThat(client.get("hoursLogged").asDouble()).isEqualTo(4.5);         // 180 + 90 min on task 1 only

        JsonNode agency = get("/api/projects/1/dashboard", login("admin@pixelco.test")).body();
        assertThat(agency.get("totalTasks").asLong()).isEqualTo(4);
        assertThat(agency.get("hoursLogged").asDouble()).isEqualTo(6.5);         // + 120 min on internal task 3
    }

    @Test
    void clientCanCommentButCannotCreateOrChangeTasks() {
        String riya = riyaAtPixelCo();
        Res c = post("/api/tasks/1/comments", riya, "{\"body\":\"Approved from my side\",\"visibility\":\"internal\"}");
        assertThat(c.status()).isEqualTo(201);
        assertThat(c.body().get("visibility").asString()).isEqualTo("client");    // client comments are always client-visible
        assertThat(post("/api/projects/1/tasks", riya, "{\"title\":\"new\"}").status()).isEqualTo(403);
        assertThat(patch("/api/tasks/1", riya, "{\"status\":\"done\"}").status()).isEqualTo(403);
        assertThat(post("/api/tasks/1/time-entries", riya, "{\"minutes\":10}").status()).isEqualTo(403);
        assertThat(get("/api/tasks/1/time-entries", riya).status()).isEqualTo(403);
    }

    @Test
    void clientReviewsOnlyVisibleFiles() {
        String riya = riyaAtPixelCo();
        assertThat(ids(get("/api/tasks/1/files", riya).body())).containsExactly(1L);
        Res ok = post("/api/files/1/review", riya, "{\"decision\":\"needs_changes\"}");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("approvalStatus").asString()).isEqualTo("needs_changes");
        assertThat(post("/api/files/2/review", riya, "{\"decision\":\"approved\"}").status()).isEqualTo(404);
        // staff cannot approve on the client's behalf
        assertThat(post("/api/files/1/review", login("admin@pixelco.test"), "{\"decision\":\"approved\"}").status())
                .isEqualTo(403);
    }

    @Test
    void clientCannotSeeAnotherClientOfTheSameAgency() {
        String omar = login("omar@globex.test");    // Globex client of PixelCo
        assertThat(ids(get("/api/projects", omar).body())).containsExactly(2L);
        assertThat(get("/api/projects/1", omar).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1", omar).status()).isEqualTo(404);
        assertThat(ids(get("/api/tasks", omar).body())).containsExactly(5L);
    }
}
