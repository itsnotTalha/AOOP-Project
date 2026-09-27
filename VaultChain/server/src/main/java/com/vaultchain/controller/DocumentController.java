package com.vaultchain.controller;

import com.vaultchain.security.CurrentUser;
import com.vaultchain.service.DocumentService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> upload(@AuthenticationPrincipal CurrentUser user,
            @RequestPart(value="file",required=false) MultipartFile file,
            @RequestParam(value="ocrMode",required=false,defaultValue="printed") String mode) {
        return Map.of("success",true,"message","Document uploaded successfully","document",service.upload(user.id(),file,mode));
    }
    @GetMapping({"", "/"})
    public Map<String,Object> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required=false) String search, @RequestParam(required=false) String type,
            @RequestParam(required=false) String ocrStatus) {
        return Map.of("success",true,"documents",service.list(user.id(),search,type,ocrStatus));
    }
    @GetMapping("/{id}")
    public Map<String,Object> get(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) {
        return Map.of("success",true,"document",service.get(user.id(),id));
    }
    @GetMapping("/{id}/ocr")
    public Map<String,Object> ocr(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) {
        return Map.of("success",true,"ocr",service.ocr(user.id(),id));
    }
    @PostMapping("/{id}/ocr")
    public Map<String,Object> retryOcr(@AuthenticationPrincipal CurrentUser user,@PathVariable String id,
            @RequestBody(required=false) Map<String,Object> body) {
        String mode=body==null?"printed":String.valueOf(body.getOrDefault("ocrMode",body.getOrDefault("mode","printed")));
        return Map.of("success",true,"message","OCR processing finished","document",service.retryOcr(user.id(),id,mode));
    }
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) {
        return service.content(user.id(),id,false);
    }
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) {
        return service.content(user.id(),id,true);
    }
    @PostMapping("/{id}/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> verify(@AuthenticationPrincipal CurrentUser user,@PathVariable String id,
            @RequestBody(required=false) Map<String,Object> body) {
        return Map.of("success",true,"message","Document verification completed","verification",
            service.verify(user.id(),id,body==null?null:body.get("referenceDocumentId")));
    }
    @GetMapping("/{id}/report")
    public Map<String,Object> history(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) {
        return Map.of("success",true,"history",service.history(user.id(),id));
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user,@PathVariable String id) { service.delete(user.id(),id); }
}
