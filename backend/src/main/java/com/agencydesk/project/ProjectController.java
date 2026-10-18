package com.agencydesk.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.auth.Actor;
import com.agencydesk.project.ProjectRepository.MemberView;
import com.agencydesk.project.ProjectRepository.ProjectView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    public record CreateProject(@NotNull Long clientId, @NotBlank String name) {}

    public record AddMember(@NotNull Long membershipId) {}

    @GetMapping
    public List<ProjectView> list(Actor actor) {
        return service.list(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView create(Actor actor, @Valid @RequestBody CreateProject req) {
        return service.create(actor, req.clientId(), req.name());
    }

    @GetMapping("/{id}")
    public ProjectView get(Actor actor, @PathVariable long id) {
        return service.get(actor, id);
    }

    @GetMapping("/{id}/members")
    public List<MemberView> members(Actor actor, @PathVariable long id) {
        return service.members(actor, id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(Actor actor, @PathVariable long id, @Valid @RequestBody AddMember req) {
        service.addMember(actor, id, req.membershipId());
    }

    @DeleteMapping("/{id}/members/{membershipId}")
    public ProjectService.RemovalResult removeMember(Actor actor, @PathVariable long id,
                                                     @PathVariable long membershipId) {
        return service.removeMember(actor, id, membershipId);
    }
}
