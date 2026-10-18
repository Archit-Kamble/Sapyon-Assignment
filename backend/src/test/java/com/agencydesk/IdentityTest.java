package com.agencydesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Edge case 3: the same email is a client contact for two agencies, with a different role and client in each. */
class IdentityTest extends ApiTestBase {

    @Test
    void onePersonLogsInOnceAndPicksAnAgency() {
        Res r = post("/api/auth/login", null, "{\"email\":\"RIYA@client.test\",\"password\":\"password123\"}");
        assertThat(r.status()).isEqualTo(200);
        JsonNode memberships = r.body().get("memberships");
        assertThat(memberships.size()).isEqualTo(2);

        String token = r.body().get("token").asString();
        // Two memberships, so nothing is selected yet: no data until she picks one.
        assertThat(get("/api/projects", token).status()).isEqualTo(403);
    }

    @Test
    void eachMembershipSeesOnlyItsOwnAgency() {
        String atPixel = login("riya@client.test", 5L);
        String atBrandHive = login("riya@client.test", 6L);

        assertThat(ids(get("/api/projects", atPixel).body())).containsExactly(1L);
        assertThat(ids(get("/api/projects", atBrandHive).body())).containsExactly(3L);
        assertThat(ids(get("/api/tasks", atBrandHive).body())).containsExactly(7L);   // not 8 (internal)
        assertThat(get("/api/tasks/1", atBrandHive).status()).isEqualTo(404);

        // Switching agency on the same session changes what she sees, immediately.
        assertThat(post("/api/auth/select", atBrandHive, "{\"membershipId\":5}").status()).isEqualTo(200);
        assertThat(ids(get("/api/projects", atBrandHive).body())).containsExactly(1L);
    }
}
