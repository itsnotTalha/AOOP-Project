package com.authvault.controller;
import com.authvault.security.CurrentUser;
import com.authvault.service.VerificationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/verifications")
public class VerificationController {
 private final VerificationService service;
 public VerificationController(VerificationService service){this.service=service;}
 @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> create(@AuthenticationPrincipal CurrentUser user,@RequestPart(value="file",required=false) MultipartFile file){
 return Map.of("success",true,"verification",service.create(user.id(),user.tokenFingerprint(),file));}
 @GetMapping({"","/"}) public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){
 return Map.of("success",true,"verifications",service.list(user.id(),user.tokenFingerprint()));}
 @GetMapping("/{reference}") public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){
 return Map.of("success",true,"verification",service.get(user.id(),reference,user.tokenFingerprint()));}
}
