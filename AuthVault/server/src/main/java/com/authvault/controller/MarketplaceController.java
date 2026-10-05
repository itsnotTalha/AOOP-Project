package com.authvault.controller;
import com.authvault.security.CurrentUser;
import com.authvault.service.MarketplaceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/marketplace/listings")
public class MarketplaceController {
 @org.springframework.beans.factory.annotation.Autowired private com.authvault.service.impl.MarketplaceServiceImpl extended;
 private final MarketplaceService service;
 public MarketplaceController(MarketplaceService service){this.service=service;}
 @PostMapping("/{reference}/tip") public Map<String,Object> tip(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference){return extended.tip(u.id(),reference);}
 @PostMapping("/{reference}/preview-requests") public Map<String,Object> requestPreview(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference){return Map.of("request",extended.previewRequest(u.id(),reference));}
 @GetMapping("/{reference}/preview-requests") public Map<String,Object> previews(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference){return Map.of("requests",extended.previews(u.id(),reference));}
 @PatchMapping("/{reference}/preview-requests/{id}") public Map<String,Object> decide(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference,@PathVariable long id,@RequestBody Map<String,Object>b){return Map.of("request",extended.decidePreview(u.id(),u.tokenFingerprint(),reference,id,String.valueOf(b.get("status"))));}
 @GetMapping("/{reference}/messages") public Map<String,Object> messages(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference){return Map.of("messages",extended.messages(u.id(),reference));}
 @PostMapping("/{reference}/messages") public Map<String,Object> send(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference,@RequestBody Map<String,Object>b){return extended.sendMessage(u.id(),reference,b);}
 @PostMapping("/{reference}/offers/{id}/accept") public Map<String,Object> accept(@AuthenticationPrincipal CurrentUser u,@PathVariable String reference,@PathVariable long id){return extended.acceptOffer(u.id(),reference,id);}
 @PostMapping({"","/"}) @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> create(@AuthenticationPrincipal CurrentUser user,@RequestBody(required=false) Map<String,Object> body){return Map.of("success",true,"message","Listing created successfully","listing",service.create(user.id(),user.tokenFingerprint(),body));}
 @GetMapping({"","/"}) public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){return Map.of("success",true,"listings",service.list(user.id(),user.tokenFingerprint()));}
 @GetMapping("/{reference}") public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"listing",service.get(user.id(),reference,user.tokenFingerprint()));}
 @PatchMapping("/{reference}") public Map<String,Object> update(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,@RequestBody(required=false) Map<String,Object> body){return Map.of("success",true,"message","Listing updated successfully","listing",service.update(user.id(),reference,user.tokenFingerprint(),body));}
 @DeleteMapping("/{reference}") public Map<String,Object> cancel(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"message","Listing cancelled successfully","listing",service.cancel(user.id(),reference,user.tokenFingerprint()));}
 @GetMapping("/{reference}/content") public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return service.content(user.id(),reference,user.tokenFingerprint());}
 @PostMapping("/{reference}/purchase") public Map<String,Object> purchase(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){return Map.of("success",true,"message","Purchase completed successfully","receipt",service.purchase(user.id(),reference));}
}
