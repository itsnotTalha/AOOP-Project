package com.vaultchain.controller;
import com.vaultchain.security.CurrentUser;
import com.vaultchain.service.MarketplaceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/marketplace/listings")
public class MarketplaceController {
 private final MarketplaceService service;
 public MarketplaceController(MarketplaceService service){this.service=service;}
 @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> create(@AuthenticationPrincipal CurrentUser user,@RequestBody(required=false) Map<String,Object> body){return Map.of("success",true,"message","Listing created successfully","listing",service.create(user.id(),user.tokenFingerprint(),body));}
 @GetMapping({"","/"}) public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){return Map.of("success",true,"listings",service.list(user.id(),user.tokenFingerprint()));}
 @GetMapping("/{reference}") public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"listing",service.get(user.id(),reference,user.tokenFingerprint()));}
 @PatchMapping("/{reference}") public Map<String,Object> update(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,@RequestBody(required=false) Map<String,Object> body){return Map.of("success",true,"message","Listing updated successfully","listing",service.update(user.id(),reference,user.tokenFingerprint(),body));}
 @DeleteMapping("/{reference}") public Map<String,Object> cancel(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"message","Listing cancelled successfully","listing",service.cancel(user.id(),reference,user.tokenFingerprint()));}
 @GetMapping("/{reference}/content") public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return service.content(user.id(),reference,user.tokenFingerprint());}
 @PostMapping("/{reference}/purchase") public Map<String,Object> purchase(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"message","Purchase completed successfully","receipt",service.purchase(user.id(),reference));}
}
