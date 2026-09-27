package com.vaultchain.controller;

import com.vaultchain.security.CurrentUser;
import com.vaultchain.service.VaultService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/vaults")
public class VaultController {
    private final VaultService service;
    public VaultController(VaultService service){this.service=service;}
    private static Map<String,Object> body(Map<String,Object> value){return value==null?Map.of():value;}
    @PostMapping({"", "/"}) @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@AuthenticationPrincipal CurrentUser user,@RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.create(user.id(),request));}
    @GetMapping({"", "/"})
    public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user){
        return Map.of("success",true,"vaults",service.list(user.id(),user.tokenFingerprint()),"stats",service.stats(user.id()));}
    @GetMapping({"/{reference}","/{reference}/"})
    public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){
        return Map.of("success",true,"vault",service.get(user.id(),reference,user.tokenFingerprint()));}
    @PostMapping({"/{reference}/unlock","/{reference}/unlock/"})
    public Map<String,Object> unlock(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,
            @RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.unlock(user.id(),reference,body(request).get("password"),user.tokenFingerprint()));}
    @PostMapping({"/{reference}/lock","/{reference}/lock/"})
    public Map<String,Object> lock(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){
        return Map.of("success",true,"vault",service.lock(user.id(),reference,user.tokenFingerprint()));}
    @PostMapping({"/{reference}/change-password","/{reference}/change-password/"})
    public Map<String,Object> changePassword(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,
            @RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.changePassword(user.id(),reference,request,user.tokenFingerprint()));}
    @PostMapping({"/{reference}/reset-password","/{reference}/reset-password/"})
    public Map<String,Object> resetPassword(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,
            @RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.resetPassword(user.id(),reference,request,user.tokenFingerprint()));}
    @PatchMapping({"/{reference}","/{reference}/"})
    public Map<String,Object> update(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,
            @RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.update(user.id(),reference,request,user.tokenFingerprint()));}
    @DeleteMapping({"/{reference}","/{reference}/"}) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference){
        service.delete(user.id(),reference,user.tokenFingerprint());}
    @PostMapping({"/{reference}/assets","/{reference}/assets/"})
    public Map<String,Object> addAssets(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,
            @RequestBody(required=false) Map<String,Object> request){
        return Map.of("success",true,"vault",service.addAssets(user.id(),reference,request,user.tokenFingerprint()));}
    @DeleteMapping({"/{reference}/assets/{assetId}","/{reference}/assets/{assetId}/"})
    public Map<String,Object> removeAsset(@AuthenticationPrincipal CurrentUser user,@PathVariable String reference,@PathVariable String assetId){
        return Map.of("success",true,"vault",service.removeAsset(user.id(),reference,assetId,user.tokenFingerprint()));}
}
