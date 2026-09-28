package com.authvault.controller;

import com.authvault.security.CurrentUser;
import com.authvault.service.WalletService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service){this.service=service;}
    @GetMapping({"", "/"}) public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user){
        return Map.of("success",true,"wallet",service.wallet(user.id()));}
    @GetMapping({"/transactions","/transactions/"}) public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){
        return Map.of("success",true,"transactions",service.transactions(user.id()));}
    @PostMapping({"/transactions","/transactions/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> add(@AuthenticationPrincipal CurrentUser user,@RequestBody(required=false) Map<String,Object> request){
        Map<String,Object> result=service.add(user.id(),request);
        return Map.of("success",true,"message","Transaction recorded successfully","wallet",result.get("wallet"),"transaction",result.get("transaction"));
    }
}
