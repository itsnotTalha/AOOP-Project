package com.authvault.service;
import java.util.List;
import java.util.Map;
public interface VaultService {
    Map<String,Object> create(long userId,Map<String,Object> body);
    List<Map<String,Object>> list(long userId,String fingerprint);
    Map<String,Object> stats(long userId);
    Map<String,Object> get(long userId,String reference,String fingerprint);
    Map<String,Object> unlock(long userId,String reference,Object password,String fingerprint);
    Map<String,Object> lock(long userId,String reference,String fingerprint);
    Map<String,Object> changePassword(long userId,String reference,Map<String,Object> body,String fingerprint);
    Map<String,Object> resetPassword(long userId,String reference,Map<String,Object> body,String fingerprint);
    Map<String,Object> update(long userId,String reference,Map<String,Object> body,String fingerprint);
    void delete(long userId,String reference,String fingerprint);
    Map<String,Object> addAssets(long userId,String reference,Map<String,Object> body,String fingerprint);
    Map<String,Object> removeAsset(long userId,String reference,String assetId,String fingerprint);
}
