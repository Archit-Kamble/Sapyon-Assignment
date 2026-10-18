package com.agencydesk.comment;

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
import com.agencydesk.comment.CommentService.CommentView;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    public record NewComment(String body, String visibility) {}

    @GetMapping
    public List<CommentView> list(Actor actor, @PathVariable long taskId) {
        return service.list(actor, taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView add(Actor actor, @PathVariable long taskId, @RequestBody NewComment req) {
        return service.add(actor, taskId, req.body(), req.visibility());
    }
}
