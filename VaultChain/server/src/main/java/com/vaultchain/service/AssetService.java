package com.vaultchain.service;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
public interface AssetService {
 Map<String,Object> upload(long user,String title,String description,String category,MultipartFile file);
 Map<String,Object> check(long user,MultipartFile file);
 List<Map<String,Object>> list(long user,String token);
 Map<String,Object> get(long user,String id,String token);
 Map<String,Object> metadata(long user,String id,String token);
 Map<String,Object> hash(long user,String id,String token);
 ResponseEntity<byte[]> content(long user,String id,String token);
 List<Map<String,Object>> history(long user,String id);
}
