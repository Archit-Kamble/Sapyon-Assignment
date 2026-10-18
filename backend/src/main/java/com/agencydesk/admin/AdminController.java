package com.agencydesk.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;

/** Small agency-side lookups: clients and staff of the current agency. */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final JdbcClient jdbc;
    private final AccessService access;

    public AdminController(JdbcClient jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record ClientView(long id, String name) {}

    public record StaffView(long membershipId, String name, String email, String role) {}

    public record NewClient(String name) {}

    @GetMapping("/clients")
    public List<ClientView> clients(Actor actor) {
        access.requireStaff(actor);
        return jdbc.sql("SELECT id, name FROM clients WHERE agency_id = ? ORDER BY name")
                .param(actor.agencyId()).query(ClientView.class).list();
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientView createClient(Actor actor, @RequestBody NewClient req) {
        access.requireAdmin(actor);
        if (req.name() == null || req.name().isBlank()) {
            throw ApiException.badRequest("name is required");
        }
        return jdbc.sql("INSERT INTO clients (agency_id, name) VALUES (?, ?) RETURNING id, name")
                .params(actor.agencyId(), req.name().trim()).query(ClientView.class).single();
    }

    @GetMapping("/staff")
    public List<StaffView> staff(Actor actor) {
        access.requireStaff(actor);
        return jdbc.sql("""
                SELECT m.id AS membership_id, u.name, u.email, m.role
                FROM memberships m JOIN users u ON u.id = m.user_id
                WHERE m.agency_id = ? AND m.active AND m.role <> 'client_user'
                ORDER BY u.name
                """)
                .param(actor.agencyId()).query(StaffView.class).list();
    }
}
