package com.agencydesk.project;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;
import com.agencydesk.project.ProjectRepository.MemberView;
import com.agencydesk.project.ProjectRepository.ProjectView;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final AccessService access;

    public ProjectService(ProjectRepository projects, AccessService access) {
        this.projects = projects;
        this.access = access;
    }

    public record RemovalResult(int tasksUnassigned) {}

    public List<ProjectView> list(Actor actor) {
        return projects.list(access.projectScope(actor));
    }

    public ProjectView get(Actor actor, long projectId) {
        access.requireProject(actor, projectId);
        return projects.get(actor.agencyId(), projectId);
    }

    public ProjectView create(Actor actor, long clientId, String name) {
        access.requireAdmin(actor);
        long id = projects.create(actor.agencyId(), clientId, name);
        return projects.get(actor.agencyId(), id);
    }

    public List<MemberView> members(Actor actor, long projectId) {
        access.requireStaff(actor);
        access.requireProject(actor, projectId);
        return projects.members(actor.agencyId(), projectId);
    }

    public void addMember(Actor actor, long projectId, long membershipId) {
        access.requireAdmin(actor);
        access.requireProject(actor, projectId);
        if (projects.staffRole(actor.agencyId(), membershipId) == null) {
            throw ApiException.notFound("staff membership");
        }
        projects.addMember(actor.agencyId(), projectId, membershipId);
    }

    /**
     * Removing someone revokes future access, not past history:
     * their tasks on this project become unassigned and flagged for reassignment,
     * their comments and time entries are kept unchanged.
     */
    @Transactional
    public RemovalResult removeMember(Actor actor, long projectId, long membershipId) {
        access.requireAdmin(actor);
        access.requireProject(actor, projectId);
        if (projects.removeMember(actor.agencyId(), projectId, membershipId) == 0) {
            throw ApiException.notFound("project member");
        }
        return new RemovalResult(projects.unassignTasks(actor.agencyId(), projectId, membershipId));
    }
}
