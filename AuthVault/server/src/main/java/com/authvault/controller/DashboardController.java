package com.authvault.controller;

import com.authvault.security.CurrentUser;
import com.authvault.service.DashboardService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping({"/summary","/summary/"})
    public Map<String,Object> summary(@AuthenticationPrincipal CurrentUser user){
        return Map.of("success",true,"summary",service.summary(user.id()));
    }
}
