package com.authvault.controller;
import com.authvault.security.CurrentUser;
import com.authvault.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service){this.service=service;}
    @GetMapping("/{section:overview|revenue|marketplace|transactions|users|assets|verification|analytics|security|notifications|logs|settings}")
    public Map<String,Object> read(@AuthenticationPrincipal CurrentUser user,@PathVariable String section,@RequestParam Map<String,String> options){
        return Map.of(section,service.read(user,section,options));
    }
    @PatchMapping("/{section:users|listings|assets|disputes}/{id}")
    public Map<String,Object> update(@AuthenticationPrincipal CurrentUser user,@PathVariable String section,@PathVariable Long id,@RequestBody Map<String,Object> body,HttpServletRequest request){
        return Map.of(section.substring(0,section.length()-1),service.update(user,section,id,body,request.getRemoteAddr()));
    }
    @PatchMapping("/settings/marketplace")
    public Map<String,Object> settings(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body,HttpServletRequest request){
        return Map.of("marketplace",service.update(user,"settings",null,body,request.getRemoteAddr()));
    }
    @PatchMapping("/notifications/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notifications(@AuthenticationPrincipal CurrentUser user){service.markNotificationsRead(user);}
}
