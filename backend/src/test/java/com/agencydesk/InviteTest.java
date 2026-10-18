package com.agencydesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Edge case 4: resending doesn't duplicate an invite; accepting twice doesn't create two accounts. */
class InviteTest extends ApiTestBase {

    @Autowired
    private JdbcClient jdbc;

    private String invite(String admin, String email) {
        Res r = post("/api/invites", admin, "{\"email\":\"" + email + "\",\"role\":\"agency_member\"}");
        assertThat(r.status()).isEqualTo(200);
        return r.body().get("token").asString();
    }

    private Res accept(String token) {
        return post("/api/invites/accept", null,
                "{\"token\":\"" + token + "\",\"name\":\"Neha\",\"password\":\"secret-pass\"}");
    }

    @Test
    void resendingReturnsTheSamePendingInvite() {
        String admin = login("admin@pixelco.test");
        String first = invite(admin, "neha@new.test");
        String again = invite(admin, "NEHA@new.test");
        assertThat(again).isEqualTo(first);
        long pending = jdbc.sql("SELECT count(*) FROM invites WHERE email = 'neha@new.test' AND status = 'pending'")
                .query(Long.class).single();
        assertThat(pending).isEqualTo(1);
    }

    @Test
    void acceptingTwiceCreatesOneAccountAndOneMembership() {
        String token = invite(login("admin@pixelco.test"), "neha@new.test");
        assertThat(accept(token).status()).isEqualTo(200);
        assertThat(accept(token).status()).isEqualTo(409);
        assertThat(countUsers("neha@new.test")).isEqualTo(1);
        assertThat(post("/api/auth/login", null, "{\"email\":\"neha@new.test\",\"password\":\"secret-pass\"}").status())
                .isEqualTo(200);
    }

    @Test
    void concurrentAcceptsOnlyOneWins() throws Exception {
        String token = invite(login("admin@pixelco.test"), "race@new.test");
        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            jobs.add(() -> {
                start.await();
                return accept(token).status();
            });
        }
        var futures = jobs.stream().map(pool::submit).toList();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(n - 1);
        assertThat(countUsers("race@new.test")).isEqualTo(1);
        long memberships = jdbc.sql("""
                SELECT count(*) FROM memberships m JOIN users u ON u.id = m.user_id WHERE u.email = 'race@new.test'
                """).query(Long.class).single();
        assertThat(memberships).isEqualTo(1);
    }

    @Test
    void existingPersonJoiningAnotherAgencyKeepsOneAccountAndPassword() {
        // Omar is already a PixelCo client. BrandHive invites the same email as a member.
        String bh = login("admin@brandhive.test");
        String token = post("/api/invites", bh, "{\"email\":\"omar@globex.test\",\"role\":\"agency_member\"}")
                .body().get("token").asString();
        Res r = post("/api/invites/accept", null,
                "{\"token\":\"" + token + "\",\"name\":\"Hacker\",\"password\":\"overwrite-attempt\"}");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("newAccount").asBoolean()).isFalse();
        assertThat(countUsers("omar@globex.test")).isEqualTo(1);
        // The invite did NOT change his password.
        assertThat(post("/api/auth/login", null, "{\"email\":\"omar@globex.test\",\"password\":\"password123\"}")
                .body().get("memberships").size()).isEqualTo(2);
    }

    @Test
    void cannotInviteSomeoneWhoIsAlreadyAMember() {
        Res r = post("/api/invites", login("admin@pixelco.test"),
                "{\"email\":\"maya@pixelco.test\",\"role\":\"agency_member\"}");
        assertThat(r.status()).isEqualTo(409);
    }

    @Test
    void onlyAdminsCanInvite() {
        Res r = post("/api/invites", login("maya@pixelco.test"), "{\"email\":\"x@y.test\",\"role\":\"agency_member\"}");
        assertThat(r.status()).isEqualTo(403);
    }

    private long countUsers(String email) {
        return jdbc.sql("SELECT count(*) FROM users WHERE email = ?").param(email).query(Long.class).single();
    }
}
