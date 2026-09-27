package com.vaultchain.controller;
import com.vaultchain.security.CurrentUser;
import com.vaultchain.service.AssetService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/assets")
public class AssetController {
 private final AssetService service;
 public AssetController(AssetService service){this.service=service;}
 @PostMapping("/upload") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> upload(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) String title,
 @RequestParam(required=false) String description,@RequestParam(required=false) String category,
 @RequestPart(value="file",required=false) MultipartFile file){Map<String,Object> result=new LinkedHashMap<>();result.put("success",true);
 result.put("message","Asset uploaded successfully");result.putAll(service.upload(user.id(),title,description,category,file));return result;}
 @PostMapping("/check") public Map<String,Object> check(@AuthenticationPrincipal CurrentUser user,@RequestPart(value="file",required=false) MultipartFile file){return Map.of("success",true,"result",service.check(user.id(),file));}
 @GetMapping({"","/"}) public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){return Map.of("success",true,"assets",service.list(user.id(),user.tokenFingerprint()));}
 @GetMapping("/{id}") public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String id){return Map.of("success",true,"asset",service.get(user.id(),id,user.tokenFingerprint()));}
 @GetMapping("/{id}/metadata") public Map<String,Object> metadata(@AuthenticationPrincipal CurrentUser user,@PathVariable String id){return Map.of("success",true,"metadata",service.metadata(user.id(),id,user.tokenFingerprint()));}
 @GetMapping("/{id}/hash") public Map<String,Object> hash(@AuthenticationPrincipal CurrentUser user,@PathVariable String id){return Map.of("success",true,"hashes",service.hash(user.id(),id,user.tokenFingerprint()));}
 @GetMapping("/{id}/content") public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser user,@PathVariable String id){return service.content(user.id(),id,user.tokenFingerprint());}
 @GetMapping("/{id}/ownership-history") public Map<String,Object> history(@AuthenticationPrincipal CurrentUser user,@PathVariable String id){return Map.of("success",true,"history",service.history(user.id(),id));}
}
