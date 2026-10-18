package com.agencydesk.task;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.auth.Actor;
import com.agencydesk.task.TaskRepository.Filter;
import com.agencydesk.task.TaskRepository.TaskView;
import com.agencydesk.task.TaskService.TaskInput;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** One endpoint for board, filters and search: /api/tasks?projectId=1&status=todo&q=logo */
    @GetMapping("/tasks")
    public List<TaskView> list(Actor actor,
                               @RequestParam(required = false) Long projectId,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String priority,
                               @RequestParam(required = false) Long assignee,
                               @RequestParam(required = false) String q) {
        return service.list(actor, new Filter(projectId, status, priority, assignee, q));
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView create(Actor actor, @PathVariable long projectId, @RequestBody TaskInput in) {
        return service.create(actor, projectId, in);
    }

    @GetMapping("/tasks/{id}")
    public TaskView get(Actor actor, @PathVariable long id) {
        return service.get(actor, id);
    }

    @PatchMapping("/tasks/{id}")
    public TaskView update(Actor actor, @PathVariable long id, @RequestBody TaskInput in) {
        return service.update(actor, id, in);
    }
}
