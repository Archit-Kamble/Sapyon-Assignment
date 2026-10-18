package com.agencydesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Edge case 1: Tenant A can't read or write Tenant B's data, even by guessing an ID. */
class TenantIsolationTest extends ApiTestBase {

    @Test
    void otherAgencyCannotReadAnythingByGuessedId() {
        String brandHive = login("admin@brandhive.test");
        // Every path that takes a PixelCo id must answer 404 (not 403: we don't even confirm it exists).
        assertThat(get("/api/projects/1", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/projects/1/dashboard", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/projects/1/members", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/projects/1/hours", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1/comments", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1/files", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/tasks/1/time-entries", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/files/1/download", brandHive).status()).isEqualTo(404);
        assertThat(get("/api/tasks?projectId=1", brandHive).status()).isEqualTo(404);
    }

    @Test
    void otherAgencyCannotWriteByGuessedId() {
        String brandHive = login("admin@brandhive.test");
        assertThat(patch("/api/tasks/1", brandHive, "{\"status\":\"done\"}").status()).isEqualTo(404);
        assertThat(post("/api/tasks/1/comments", brandHive, "{\"body\":\"hi\"}").status()).isEqualTo(404);
        assertThat(post("/api/projects/1/tasks", brandHive, "{\"title\":\"x\"}").status()).isEqualTo(404);
        assertThat(post("/api/tasks/1/time-entries", brandHive, "{\"minutes\":30}").status()).isEqualTo(404);
        assertThat(post("/api/files/1/review", brandHive, "{\"decision\":\"approved\"}").status()).isIn(403, 404);
        // Assigning a PixelCo staff member to a BrandHive task is rejected as well.
        assertThat(patch("/api/tasks/7", brandHive, "{\"assigneeMembershipId\":2}").status()).isEqualTo(400);

        String pixel = login("admin@pixelco.test");
        assertThat(get("/api/tasks/1", pixel).body().get("status").asString()).isEqualTo("review");
    }

    @Test
    void schemaRejectsCrossTenantReferenceEvenIfCodeForgetsToCheck() {
        // PixelCo admin tries to create a project for BrandHive's client 3.
        // No service-level check exists for this on purpose: the composite FK (client_id, agency_id) stops it.
        String pixel = login("admin@pixelco.test");
        Res r = post("/api/projects", pixel, "{\"clientId\":3,\"name\":\"stolen\"}");
        assertThat(r.status()).isEqualTo(409);
    }

    @Test
    void listsOnlyContainOwnAgencyRows() {
        String pixel = login("admin@pixelco.test");
        assertThat(ids(get("/api/projects", pixel).body())).containsExactly(1L, 2L);
        assertThat(ids(get("/api/tasks", pixel).body())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        String brandHive = login("admin@brandhive.test");
        assertThat(ids(get("/api/tasks", brandHive).body())).containsExactlyInAnyOrder(7L, 8L);
        assertThat(ids(get("/api/tasks?q=homepage", brandHive).body())).isEmpty();
    }

    @Test
    void cannotSwitchSessionIntoSomeoneElsesMembership() {
        String brandHive = login("admin@brandhive.test");
        // membership 1 is the PixelCo admin's
        assertThat(post("/api/auth/select", brandHive, "{\"membershipId\":1}").status()).isEqualTo(404);
    }

    @Test
    void agencyMemberSeesOnlyAssignedProjects() {
        String maya = login("maya@pixelco.test");   // member of project 1 only
        assertThat(ids(get("/api/projects", maya).body())).containsExactly(1L);
        assertThat(get("/api/projects/2", maya).status()).isEqualTo(404);
        assertThat(get("/api/tasks/5", maya).status()).isEqualTo(404);
        assertThat(ids(get("/api/tasks", maya).body())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }
}
