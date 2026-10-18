package com.agencydesk.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.agencydesk.auth.Actor;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/projects/{projectId}/dashboard")
    public DashboardService.Dashboard get(Actor actor, @PathVariable long projectId) {
        return service.forProject(actor, projectId);
    }
}
