package com.vaultchain.service;

import com.vaultchain.entity.*;
import com.vaultchain.exception.ApiException;
import com.vaultchain.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultAccessService {
    private final VaultJpaRepository vaults;
    private final VaultAssetJpaRepository members;
    private final VaultUnlockSessionJpaRepository sessions;
    private final VaultUnlockAttemptJpaRepository attempts;
    private final long ttl,maxAttempts,windowSeconds,blockSeconds;
    public VaultAccessService(VaultJpaRepository vaults,VaultAssetJpaRepository members,
            VaultUnlockSessionJpaRepository sessions,VaultUnlockAttemptJpaRepository attempts,
            @Value("${vaultchain.vault-unlock-ttl-seconds:${VAULT_UNLOCK_TTL_SECONDS:0}}") long ttl,
            @Value("${vaultchain.vault-unlock-max-attempts:${VAULT_UNLOCK_MAX_ATTEMPTS:5}}") long maxAttempts,
            @Value("${vaultchain.vault-unlock-window-seconds:${VAULT_UNLOCK_WINDOW_SECONDS:900}}") long windowSeconds,
            @Value("${vaultchain.vault-unlock-block-seconds:${VAULT_UNLOCK_BLOCK_SECONDS:900}}") long blockSeconds){
        this.vaults=vaults;this.members=members;this.sessions=sessions;this.attempts=attempts;
        this.ttl=ttl;this.maxAttempts=Math.max(1,Math.min(maxAttempts,100));
        this.windowSeconds=Math.max(1,Math.min(windowSeconds,86400));this.blockSeconds=Math.max(1,Math.min(blockSeconds,86400));
    }
    private static boolean future(String time){if(time==null)return false;try{return Instant.parse(time).isAfter(Instant.now());}catch(Exception ignored){return false;}}
    private static Map<String,Object> state(boolean protectedByPassword,boolean locked,String expiry){
        Map<String,Object> result=new LinkedHashMap<>();result.put("passwordProtected",protectedByPassword);
        result.put("isLocked",locked);result.put("unlockExpiresAt",expiry);return result;
    }
    public Map<String,Object> access(Vault vault,long userId,String fingerprint){
        if(vault.getPasswordHash()==null)return state(false,false,null);
        if(fingerprint==null)return state(true,true,null);
        var found=sessions.findByVaultIdAndUserIdAndTokenFingerprint(vault.getId(),userId,fingerprint);
        if(found.isEmpty()||!future(found.get().getExpiresAt()))return state(true,true,null);
        return state(true,false,found.get().getExpiresAt());
    }
    @Transactional public String grant(Vault vault,long userId,String fingerprint){
        if(fingerprint==null)throw new ApiException(401,"Unauthorized");
        long seconds=ttl>0?Math.min(ttl,86400):vault.getAutoLockMinutes()==null?600:vault.getAutoLockMinutes()*60;
        String expiry=Instant.now().plusSeconds(seconds).toString();
        VaultUnlockSession session=sessions.findByVaultIdAndUserIdAndTokenFingerprint(vault.getId(),userId,fingerprint).orElseGet(VaultUnlockSession::new);
        session.setVaultId(vault.getId());session.setUserId(userId);session.setTokenFingerprint(fingerprint);session.setExpiresAt(expiry);
        sessions.saveAndFlush(session);return expiry;
    }
    @Transactional public void revoke(Vault vault,long userId,String fingerprint){
        if(fingerprint==null)throw new ApiException(401,"Unauthorized");
        sessions.deleteByVaultIdAndUserIdAndTokenFingerprint(vault.getId(),userId,fingerprint);
    }
    @Transactional public void revokeAll(Vault vault){sessions.deleteByVaultId(vault.getId());}
    public void assertAttemptAllowed(Vault vault,long userId){
        var record=attempts.findByVaultIdAndUserId(vault.getId(),userId);
        if(record.isPresent()&&future(record.get().getBlockedUntil()))throw new ApiException(429,"Too many Vault password attempts. Try again later.");
    }
    @Transactional public boolean failedAttempt(Vault vault,long userId){
        var record=attempts.findByVaultIdAndUserId(vault.getId(),userId).orElseGet(VaultUnlockAttempt::new);
        Instant now=Instant.now();Instant window;
        try{window=Instant.parse(record.getWindowStartedAt());}catch(Exception ignored){window=Instant.EPOCH;}
        long count=window.plusSeconds(windowSeconds).isAfter(now)&&record.getAttemptCount()!=null?record.getAttemptCount()+1:1;
        if(count==1)record.setWindowStartedAt(now.toString());
        record.setVaultId(vault.getId());record.setUserId(userId);record.setAttemptCount(count);
        record.setBlockedUntil(count>=maxAttempts?now.plusSeconds(blockSeconds).toString():null);
        attempts.saveAndFlush(record);return count>=maxAttempts;
    }
    @Transactional public void clearAttempts(Vault vault,long userId){attempts.deleteByVaultIdAndUserId(vault.getId(),userId);}
    public Map<String,Object> protection(long userId,long assetId,String fingerprint){
        List<Map<String,Object>> protecting=new ArrayList<>();String earliest=null;boolean locked=false;
        for(var member:members.findByAssetIdOrderByVaultIdAsc(assetId)){
            Vault vault=vaults.findById(member.getVaultId()).orElse(null);
            if(vault==null||vault.getUserId()!=userId||vault.getPasswordHash()==null)continue;
            Map<String,Object> access=access(vault,userId,fingerprint);
            String expiry=(String)access.get("unlockExpiresAt");boolean thisLocked=(boolean)access.get("isLocked");
            locked|=thisLocked;if(expiry!=null&&(earliest==null||expiry.compareTo(earliest)<0))earliest=expiry;
            Map<String,Object> item=new LinkedHashMap<>();item.put("reference",vault.getPublicReference());item.put("name",vault.getName());
            item.put("isLocked",thisLocked);item.put("unlockExpiresAt",expiry);protecting.add(item);
        }
        Map<String,Object> result=state(!protecting.isEmpty(),locked,locked?null:earliest);result.put("protectingVaults",protecting);return result;
    }
    public Map<String,Object> assertAssetUnlocked(long userId,long assetId,String fingerprint){
        Map<String,Object> result=protection(userId,assetId,fingerprint);
        if(Boolean.TRUE.equals(result.get("isLocked")))throw new ApiException(423,"Protected by Vault — unlock every protecting Vault to access");
        return result;
    }
}
