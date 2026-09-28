package com.authvault.service;
import java.util.*;
import org.springframework.http.ResponseEntity;
public interface MarketplaceService {
 Map<String,Object> create(long user,String token,Map<String,Object> request);
 List<Map<String,Object>> list(long user,String token);
 Map<String,Object> get(long user,String reference,String token);
 Map<String,Object> update(long user,String reference,String token,Map<String,Object> request);
 Map<String,Object> cancel(long user,String reference,String token);
 ResponseEntity<byte[]> content(long user,String reference,String token);
 Map<String,Object> purchase(long user,String reference);
}
