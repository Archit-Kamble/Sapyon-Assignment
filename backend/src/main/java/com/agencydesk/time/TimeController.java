package com.agencydesk.time;

import java.time.LocalDate;
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
import com.agencydesk.time.TimeService.MemberHours;
import com.agencydesk.time.TimeService.TimeEntryView;

@RestController
@RequestMapping("/api")
public class TimeController {

    private final TimeService service;

    public TimeController(TimeService service) {
        this.service = service;
    }

    public record NewEntry(Integer minutes, String note, LocalDate entryDate) {}

    @GetMapping("/tasks/{taskId}/time-entries")
    public List<TimeEntryView> list(Actor actor, @PathVariable long taskId) {
        return service.list(actor, taskId);
    }

    @PostMapping("/tasks/{taskId}/time-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeEntryView log(Actor actor, @PathVariable long taskId, @RequestBody NewEntry req) {
        return service.log(actor, taskId, req.minutes(), req.note(), req.entryDate());
    }

    @GetMapping("/projects/{projectId}/hours")
    public List<MemberHours> summary(Actor actor, @PathVariable long projectId) {
        return service.projectSummary(actor, projectId);
    }
}
