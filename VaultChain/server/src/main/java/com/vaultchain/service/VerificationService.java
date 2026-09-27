package com.vaultchain.service;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
public interface VerificationService {
 Map<String,Object> create(long user,String token,MultipartFile file);
 List<Map<String,Object>> list(long user,String token);
 Map<String,Object> get(long user,String reference,String token);
}
