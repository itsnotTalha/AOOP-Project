package com.vaultchain.service.impl;

import com.vaultchain.entity.*;
import com.vaultchain.exception.ApiException;
import com.vaultchain.repository.*;
import com.vaultchain.security.LegacyPasswordEncoder;
import com.vaultchain.service.AuthService;
import com.vaultchain.service.VaultAccessService;
import com.vaultchain.service.VaultService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VaultServiceImpl implements VaultService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final VaultJpaRepository vaults;private final VaultAssetJpaRepository members;private final AssetJpaRepository assets;
    private final AssetMetadataJpaRepository metadata;private final AssetHashJpaRepository hashes;
    private final LegacyPasswordEncoder passwords;private final AuthService accounts;private final VaultAccessService access;
    private final EntityManager entityManager;
    public VaultServiceImpl(VaultJpaRepository vaults,VaultAssetJpaRepository members,AssetJpaRepository assets,
            AssetMetadataJpaRepository metadata,AssetHashJpaRepository hashes,LegacyPasswordEncoder passwords,
            AuthService accounts,VaultAccessService access,EntityManager entityManager){
        this.vaults=vaults;this.members=members;this.assets=assets;this.metadata=metadata;this.hashes=hashes;
        this.passwords=passwords;this.accounts=accounts;this.access=access;this.entityManager=entityManager;
    }
    private static String text(Object value){return value==null?"":String.valueOf(value);}
    private static String name(Object value){String name=text(value).trim();
        if(name.isEmpty())throw new ApiException(400,"Vault name is required");
        if(name.length()>80)throw new ApiException(400,"Vault name must be 80 characters or fewer");return name;}
    private static String description(Object value){String description=text(value).trim();
        if(description.length()>500)throw new ApiException(400,"Description must be 500 characters or fewer");
        return description.isEmpty()?null:description;}
    private static String password(Object value){String password=text(value);int bytes=password.getBytes(StandardCharsets.UTF_8).length;
        if(bytes<8)throw new ApiException(400,"Vault password must be at least 8 characters");
        if(bytes>72)throw new ApiException(400,"Vault password must be 72 bytes or fewer");return password;}
    private static String confirmed(Map<String,Object> body){String password=password(body.get("newPassword"));
        if(!password.equals(text(body.get("confirmPassword"))))throw new ApiException(400,"New Vault passwords do not match");return password;}
    private static long minutes(Object value,long fallback){if(value==null||text(value).isEmpty())return fallback;
        try{double valueNumber=Double.parseDouble(text(value));if(valueNumber==5||valueNumber==10||valueNumber==30)return (long)valueNumber;}
        catch(Exception ignored){}throw new ApiException(400,"Auto-lock duration must be 5, 10, or 30 minutes");}
    private static String reference(){byte[] bytes=new byte[3];RANDOM.nextBytes(bytes);return "VT-"+java.util.HexFormat.of().withUpperCase().formatHex(bytes);}
    private static String now(){return TIME.format(LocalDateTime.now(ZoneOffset.UTC));}
    private static String normalized(String reference){String value=text(reference).trim().toUpperCase(Locale.ROOT);
        if(!value.matches("VT-[A-F0-9]{6}"))throw new ApiException(404,"Vault not found");return value;}
    private Vault owned(long userId,String reference){return vaults.findByPublicReferenceAndUserId(normalized(reference),userId)
        .orElseThrow(()->new ApiException(404,"Vault not found"));}
    private void unlocked(Vault vault,long userId,String fingerprint){
        if(Boolean.TRUE.equals(access.access(vault,userId,fingerprint).get("isLocked")))throw new ApiException(423,"Vault is locked");}
    private Map<String,Object> publicAsset(Asset asset,String addedAt,String fingerprint){
        Map<String,Object> protection=access.protection(asset.getOwnerId(),asset.getId(),fingerprint);
        boolean locked=Boolean.TRUE.equals(protection.get("isLocked"));
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",asset.getId());out.put("title",asset.getTitle());
        out.put("description",asset.getDescription());out.put("category",asset.getCategory());out.put("fileName",asset.getFileName());
        out.put("fileSize",asset.getFileSize());out.put("mimeType",asset.getMimeType());out.put("status",asset.getStatus());
        out.put("createdAt",asset.getCreatedAt());
        var info=metadata.findByAssetId(asset.getId());out.put("width",locked?null:info.map(AssetMetadata::getWidth).orElse(null));
        out.put("height",locked?null:info.map(AssetMetadata::getHeight).orElse(null));out.put("hasHash",hashes.findByAssetId(asset.getId()).isPresent());
        out.put("hasMetadata",info.isPresent());out.put("addedAt",addedAt);
        out.put("reference","VC-A"+String.format("%06d",asset.getId()));
        out.put("contentUrl",locked?null:"/api/assets/"+asset.getId()+"/content");
        out.put("vaultProtection",protection);return out;
    }
    private Map<String,Object> publicVault(Vault vault,long userId,String fingerprint,int limit){
        Map<String,Object> state=access.access(vault,userId,fingerprint);
        List<VaultAsset> membership=members.findByVaultIdOrderByAddedAtDescAssetIdDesc(vault.getId());
        List<Map<String,Object>> visible=new ArrayList<>();
        if(!Boolean.TRUE.equals(state.get("isLocked")))for(VaultAsset link:membership){
            Asset asset=assets.findById(link.getAssetId()).orElse(null);
            if(asset!=null&&asset.getOwnerId()==userId){visible.add(publicAsset(asset,link.getAddedAt(),fingerprint));
                if(limit>0&&visible.size()==limit)break;}}
        Map<String,Object> out=new LinkedHashMap<>();out.put("reference",vault.getPublicReference());out.put("name",vault.getName());
        out.put("description",vault.getDescription());out.put("assetCount",membership.size());
        out.putAll(state);out.put("autoLockMinutes",vault.getAutoLockMinutes());
        out.put("createdAt",vault.getCreatedAt());out.put("updatedAt",vault.getUpdatedAt());out.put("assets",visible);return out;
    }
    @Override @Transactional
    public Map<String,Object> create(long userId,Map<String,Object> body){
        body=body==null?Map.of():body;String name=name(body.get("name")),description=description(body.get("description"));
        String hash=passwords.encodeVault(password(body.get("password")));long minutes=minutes(body.get("autoLockMinutes"),10);
        for(int attempt=0;attempt<4;attempt++){
            Vault vault=new Vault();vault.setUserId(userId);vault.setPublicReference(reference());vault.setName(name);
            vault.setDescription(description);vault.setPasswordHash(hash);vault.setAutoLockMinutes(minutes);
            try{vault=vaults.saveAndFlush(vault);entityManager.refresh(vault);return publicVault(vault,userId,null,0);}
            catch(org.springframework.dao.DataIntegrityViolationException failure){if(attempt==3)throw new ApiException(500,"Unable to allocate a Vault reference");}
        }
        throw new ApiException(500,"Unable to allocate a Vault reference");
    }
    @Override public List<Map<String,Object>> list(long userId,String fingerprint){
        return vaults.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream().map(v->publicVault(v,userId,fingerprint,3)).toList();}
    @Override public Map<String,Object> stats(long userId){
        long total=vaults.countByUserId(userId),allAssets=assets.countByOwnerId(userId);
        Set<Long> organized=new HashSet<>();for(Vault vault:vaults.findByUserIdOrderByUpdatedAtDescIdDesc(userId))
            for(VaultAsset link:members.findByVaultIdOrderByAddedAtDescAssetIdDesc(vault.getId()))organized.add(link.getAssetId());
        return Map.of("totalVaults",total,"organizedAssets",organized.size(),"totalAssets",allAssets,
            "unorganizedAssets",Math.max(0,allAssets-organized.size()));
    }
    @Override public Map<String,Object> get(long userId,String reference,String fingerprint){return publicVault(owned(userId,reference),userId,fingerprint,0);}
    @Override public Map<String,Object> unlock(long userId,String reference,Object secret,String fingerprint){
        Vault vault=owned(userId,reference);if(vault.getPasswordHash()==null)throw new ApiException(409,"This legacy Vault does not have password protection configured");
        access.assertAttemptAllowed(vault,userId);
        if(!passwords.matches(text(secret),vault.getPasswordHash())){
            boolean blocked=access.failedAttempt(vault,userId);throw new ApiException(blocked?429:401,
                blocked?"Too many Vault password attempts. Try again later.":"Incorrect Vault password");}
        access.clearAttempts(vault,userId);access.grant(vault,userId,fingerprint);return get(userId,reference,fingerprint);
    }
    @Override public Map<String,Object> lock(long userId,String reference,String fingerprint){
        Vault vault=owned(userId,reference);access.revoke(vault,userId,fingerprint);return get(userId,reference,fingerprint);}
    @Override public Map<String,Object> changePassword(long userId,String reference,Map<String,Object> body,String fingerprint){
        Vault vault=owned(userId,reference);if(vault.getPasswordHash()==null)throw new ApiException(409,"Set an initial password through Edit Vault");
        body=body==null?Map.of():body;String replacement=confirmed(body);access.assertAttemptAllowed(vault,userId);
        if(!passwords.matches(text(body.get("currentPassword")),vault.getPasswordHash())){
            boolean blocked=access.failedAttempt(vault,userId);throw new ApiException(blocked?429:401,
                blocked?"Too many Vault password attempts. Try again later.":"Current Vault password is incorrect");}
        access.clearAttempts(vault,userId);vault.setPasswordHash(passwords.encodeVault(replacement));
        vault.setAutoLockMinutes(minutes(body.get("autoLockMinutes"),vault.getAutoLockMinutes()));vault.setUpdatedAt(now());
        vaults.saveAndFlush(vault);access.revokeAll(vault);return get(userId,reference,fingerprint);
    }
    @Override public Map<String,Object> resetPassword(long userId,String reference,Map<String,Object> body,String fingerprint){
        Vault vault=owned(userId,reference);body=body==null?Map.of():body;String replacement=confirmed(body);access.assertAttemptAllowed(vault,userId);
        if(!accounts.verifyAccountPassword(userId,body.get("accountPassword"))){
            boolean blocked=access.failedAttempt(vault,userId);throw new ApiException(blocked?429:401,
                blocked?"Too many password attempts. Try again later.":"Account password is incorrect");}
        access.clearAttempts(vault,userId);vault.setPasswordHash(passwords.encodeVault(replacement));
        vault.setAutoLockMinutes(minutes(body.get("autoLockMinutes"),vault.getAutoLockMinutes()));vault.setUpdatedAt(now());
        vaults.saveAndFlush(vault);access.revokeAll(vault);return get(userId,reference,fingerprint);
    }
    @Override public Map<String,Object> update(long userId,String reference,Map<String,Object> body,String fingerprint){
        Vault vault=owned(userId,reference);unlocked(vault,userId,fingerprint);body=body==null?Map.of():body;
        if(!body.containsKey("name")&&!body.containsKey("description")&&!body.containsKey("password"))
            throw new ApiException(400,"Provide a name or description to update");
        if(body.containsKey("password")&&vault.getPasswordHash()!=null)throw new ApiException(400,"Vault password changes are not supported");
        if(body.containsKey("name"))vault.setName(name(body.get("name")));
        if(body.containsKey("description"))vault.setDescription(description(body.get("description")));
        if(body.containsKey("password"))vault.setPasswordHash(passwords.encodeVault(password(body.get("password"))));
        vault.setUpdatedAt(now());vaults.saveAndFlush(vault);return get(userId,reference,fingerprint);
    }
    @Override @Transactional public void delete(long userId,String reference,String fingerprint){
        Vault vault=owned(userId,reference);unlocked(vault,userId,fingerprint);vaults.deleteVaultById(vault.getId());vaults.flush();
    }
    private static long assetId(Object raw){try{long value=Long.parseLong(String.valueOf(raw));if(value>0)return value;}
        catch(Exception ignored){}throw new ApiException(400,"Asset IDs must be positive integers");}
    @Override @Transactional
    public Map<String,Object> addAssets(long userId,String reference,Map<String,Object> body,String fingerprint){
        Vault vault=owned(userId,reference);unlocked(vault,userId,fingerprint);Object raw=body==null?null:body.get("assetIds");
        if(!(raw instanceof List<?> ids)||ids.isEmpty())throw new ApiException(400,"Select at least one asset");
        if(ids.size()>50)throw new ApiException(400,"Add no more than 50 assets at once");
        List<Long> values=ids.stream().map(VaultServiceImpl::assetId).toList();
        if(new HashSet<>(values).size()!=values.size())throw new ApiException(400,"Duplicate asset IDs are not allowed");
        for(long id:values)if(assets.findByIdAndOwnerId(id,userId).isEmpty())throw new ApiException(404,"One or more assets were not found");
        for(long id:values)if(members.existsByVaultIdAndAssetId(vault.getId(),id))throw new ApiException(409,"One or more assets are already in this Vault");
        for(long id:values){VaultAsset link=new VaultAsset();link.setVaultId(vault.getId());link.setAssetId(id);members.save(link);}
        members.flush();vault.setUpdatedAt(now());vaults.saveAndFlush(vault);return get(userId,reference,fingerprint);
    }
    @Override @Transactional
    public Map<String,Object> removeAsset(long userId,String reference,String rawAssetId,String fingerprint){
        Vault vault=owned(userId,reference);unlocked(vault,userId,fingerprint);long id;
        try{id=Long.parseLong(rawAssetId);if(id<=0)throw new NumberFormatException();}
        catch(Exception failure){throw new ApiException(400,"Asset ID must be a positive integer");}
        if(!members.existsByVaultIdAndAssetId(vault.getId(),id))throw new ApiException(404,"Asset is not in this Vault");
        members.deleteByVaultIdAndAssetId(vault.getId(),id);members.flush();vault.setUpdatedAt(now());vaults.saveAndFlush(vault);
        return get(userId,reference,fingerprint);
    }
}
