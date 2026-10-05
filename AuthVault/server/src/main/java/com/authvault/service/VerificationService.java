package com.authvault.service;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface VerificationService {
    Map<String, Object> create(long user, String token, MultipartFile file);
    List<Map<String, Object>> list(long user, String token);
    Map<String, Object> get(long user, String reference, String token);
    ResponseEntity<byte[]> matchContent(long user, String reference, Long assetId, String token);
    Map<String, Object> createDispute(long user, Map<String, Object> body);
    List<Map<String, Object>> listDisputes(long user);
}
