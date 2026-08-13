package com.fcms.naveen.controller;

import com.fcms.naveen.dto.NaveenDashboard;
import com.fcms.naveen.service.NaveenDashboardService;
import com.fcms.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/naveen/dashboard")
public class NaveenDashboardController {

    private final NaveenDashboardService dashboardService;
    private final AuthService authService;

    public NaveenDashboardController(NaveenDashboardService dashboardService, AuthService authService) {
        this.dashboardService = dashboardService;
        this.authService = authService;
    }

    @GetMapping
    public NaveenDashboard summary(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
        return dashboardService.summary();
    }
}
