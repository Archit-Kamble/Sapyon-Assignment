package com.agencydesk.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.agencydesk.access.AccessService;
import com.agencydesk.auth.Actor;
import com.agencydesk.common.ApiException;
import com.agencydesk.project.ProjectRepository;
import com.agencydesk.task.TaskRepository.Filter;
import com.agencydesk.task.TaskRepository.TaskView;

@Service
public class TaskService {

    static final Set<String> STATUSES = Set.of("todo", "in_progress", "review", "done");
    static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "urgent");
    static final Set<String> VISIBILITIES = Set.of("internal", "client");

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final AccessService access;

    public TaskService(TaskRepository tasks, ProjectRepository projects, AccessService access) {
        this.tasks = tasks;
        this.projects = projects;
        this.access = access;
    }

    /** All fields optional; null means "leave unchanged" on update, or the default on create. */
    public record TaskInput(String title, String description, String status, String priority,
                            Long assigneeMembershipId, LocalDate dueDate, String visibility) {}

    public List<TaskView> list(Actor actor, Filter filter) {
        if (filter.projectId() != null) {
            access.requireProject(actor, filter.projectId());
        }
        return tasks.list(access.taskScope(actor), filter);
    }

    public TaskView get(Actor actor, long taskId) {
        access.requireTask(actor, taskId);
        return tasks.get(actor.agencyId(), taskId);
    }

    public TaskView create(Actor actor, long projectId, TaskInput in) {
        // Clients may comment, but never create tasks.
        access.requireStaff(actor);
        access.requireProject(actor, projectId);
        if (in.title() == null || in.title().isBlank()) {
            throw ApiException.badRequest("title is required");
        }
        String status = check(in.status(), STATUSES, "todo", "status");
        String priority = check(in.priority(), PRIORITIES, "medium", "priority");
        String visibility = check(in.visibility(), VISIBILITIES, "internal", "visibility");
        checkAssignee(actor, projectId, in.assigneeMembershipId());
        long id = tasks.create(actor.agencyId(), projectId, in.title().trim(), in.description(), status, priority,
                in.assigneeMembershipId(), in.dueDate(), visibility);
        return tasks.get(actor.agencyId(), id);
    }

    public TaskView update(Actor actor, long taskId, TaskInput in) {
        // Clients can never change status (or anything else on a task).
        access.requireStaff(actor);
        var ref = access.requireTask(actor, taskId);
        TaskView cur = tasks.get(actor.agencyId(), taskId);

        Long assignee = in.assigneeMembershipId() != null ? in.assigneeMembershipId() : cur.assigneeMembershipId();
        boolean assigneeChanged = !Objects.equals(assignee, cur.assigneeMembershipId());
        if (assigneeChanged) {
            checkAssignee(actor, ref.projectId(), assignee);
        }
        tasks.update(actor.agencyId(), taskId,
                in.title() != null ? in.title().trim() : cur.title(),
                in.description() != null ? in.description() : cur.description(),
                in.status() != null ? check(in.status(), STATUSES, null, "status") : cur.status(),
                in.priority() != null ? check(in.priority(), PRIORITIES, null, "priority") : cur.priority(),
                assignee,
                in.dueDate() != null ? in.dueDate() : cur.dueDate(),
                in.visibility() != null ? check(in.visibility(), VISIBILITIES, null, "visibility") : cur.visibility(),
                // Giving the task a new owner resolves the "needs reassignment" flag.
                assigneeChanged ? false : cur.needsReassignment());
        return tasks.get(actor.agencyId(), taskId);
    }

    /** The assignee must be active staff of THIS agency and work on THIS project (admins always qualify). */
    private void checkAssignee(Actor actor, long projectId, Long membershipId) {
        if (membershipId == null) {
            return;
        }
        String role = projects.staffRole(actor.agencyId(), membershipId);
        if (role == null) {
            throw ApiException.badRequest("assignee is not staff of this agency");
        }
        if (!"agency_admin".equals(role) && !access.isProjectMember(projectId, membershipId, actor.agencyId())) {
            throw ApiException.badRequest("assignee is not a member of this project");
        }
    }

    private static String check(String value, Set<String> allowed, String fallback, String field) {
        if (value == null) {
            return fallback;
        }
        if (!allowed.contains(value)) {
            throw ApiException.badRequest("invalid " + field + ": " + value);
        }
        return value;
    }
}
