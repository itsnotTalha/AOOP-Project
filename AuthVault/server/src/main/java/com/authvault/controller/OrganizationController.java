package com.authvault.controller;
import com.authvault.security.CurrentUser;
import com.authvault.service.OrganizationService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/organizations")
public class OrganizationController {
 private final OrganizationService service;
 public OrganizationController(OrganizationService service){this.service=service;}
 @GetMapping public Map<String,Object> list(@AuthenticationPrincipal CurrentUser u){return Map.of("organizations",service.list(u));}
 @GetMapping("/users") public Map<String,Object> users(@RequestParam(defaultValue="") String q){return Map.of("users",service.search(q));}
 @PostMapping public Map<String,Object> create(@AuthenticationPrincipal CurrentUser u,@RequestBody Map<String,Object>b){return Map.of("organization",service.create(u,b));}
 @PostMapping("/{id}/{action}") public Map<String,Object> mutate(@AuthenticationPrincipal CurrentUser u,@PathVariable String id,@PathVariable String action,@RequestBody Map<String,Object>b){return service.mutate(u,id,action,b);}
}
