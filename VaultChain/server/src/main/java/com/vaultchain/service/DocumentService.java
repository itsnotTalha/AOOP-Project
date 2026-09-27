package com.vaultchain.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

public interface DocumentService {
    Map<String,Object> upload(long userId, MultipartFile file, String mode);
    List<Map<String,Object>> list(long userId, String search, String type, String ocrStatus);
    Map<String,Object> get(long userId, String id);
    Map<String,Object> ocr(long userId, String id);
    Map<String,Object> retryOcr(long userId, String id, String mode);
    ResponseEntity<byte[]> content(long userId, String id, boolean preview);
    Map<String,Object> verify(long userId, String id, Object referenceId);
    List<Map<String,Object>> history(long userId, String id);
    void delete(long userId, String id);
}
