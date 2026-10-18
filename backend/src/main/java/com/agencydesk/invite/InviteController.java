package com.agencydesk.invite;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.auth.Actor;
import com.agencydesk.invite.InviteService.AcceptResult;
import com.agencydesk.invite.InviteService.InviteView;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService service;

    public InviteController(InviteService service) {
        this.service = service;
    }

    public record NewInvite(String email, String role, Long clientId) {}

    public record AcceptRequest(String token, String name, String password) {}

    @GetMapping
    public List<InviteView> list(Actor actor) {
        return service.list(actor);
    }

    @PostMapping
    public InviteView invite(Actor actor, @RequestBody NewInvite req) {
        return service.invite(actor, req.email(), req.role(), req.clientId());
    }

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Actor actor, @PathVariable long id) {
        service.revoke(actor, id);
    }

    /** Public: the invite token itself is the credential. */
    @PostMapping("/accept")
    public AcceptResult accept(@RequestBody AcceptRequest req) {
        return service.accept(req.token(), req.name(), req.password());
    }
}
