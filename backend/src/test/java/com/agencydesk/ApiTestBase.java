package com.agencydesk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Starts the real app on a random port and talks to it over HTTP, exactly like the frontend does.
 * The test database is wiped and re-seeded (V1 schema + V2 seed) before every test.
 *
 * Seed cheat-sheet (all passwords: password123):
 *   Agency 1 PixelCo:   admin@pixelco.test (m1), maya@pixelco.test (m2, member of project 1),
 *                       dev@pixelco.test (m3, projects 1 and 2), riya@client.test (m5, client Acme),
 *                       omar@globex.test (m7, client Globex)
 *   Agency 2 BrandHive: admin@brandhive.test (m4), riya@client.test (m6, client Zeta)
 *   Projects: 1 Acme (PixelCo), 2 Globex (PixelCo), 3 Zeta (BrandHive)
 *   Tasks: client-visible 1, 4, 5, 7 | internal 2, 3, 6, 8
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestBase {

    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private Environment env;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    protected record Res(int status, JsonNode body) {}

    protected String base() {
        return "http://localhost:" + env.getProperty("local.server.port");
    }

    protected Res call(String method, String path, String token, String jsonBody) {
        try {
            var b = HttpRequest.newBuilder(URI.create(base() + path))
                    .header("Content-Type", "application/json")
                    .method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(jsonBody));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = r.body() == null || r.body().isBlank() ? null : JSON.readTree(r.body());
            return new Res(r.statusCode(), body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Res get(String path, String token) {
        return call("GET", path, token, null);
    }

    protected Res post(String path, String token, String body) {
        return call("POST", path, token, body);
    }

    protected Res patch(String path, String token, String body) {
        return call("PATCH", path, token, body);
    }

    /** Logs in and, if the person has several agencies, selects the given membership. */
    protected String login(String email, Long membershipId) {
        Res r = post("/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"password123\"}");
        if (r.status() != 200) {
            throw new AssertionError("login failed for " + email + ": " + r.status());
        }
        String token = r.body().get("token").asString();
        if (membershipId != null) {
            Res s = post("/api/auth/select", token, "{\"membershipId\":" + membershipId + "}");
            if (s.status() != 200) {
                throw new AssertionError("select failed: " + s.status());
            }
        }
        return token;
    }

    protected String login(String email) {
        return login(email, null);
    }

    protected static java.util.List<Long> ids(JsonNode array) {
        var out = new java.util.ArrayList<Long>();
        array.forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }
}
