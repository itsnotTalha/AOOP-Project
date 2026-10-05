package com.authvault.service.impl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.authvault.entity.Asset;
import com.authvault.entity.AssetHash;
import com.authvault.entity.AssetMetadata;
import com.authvault.entity.MarketplaceListing;
import com.authvault.exception.ApiException;
import com.authvault.repository.AssetHashJpaRepository;
import com.authvault.repository.AssetJpaRepository;
import com.authvault.repository.AssetMetadataJpaRepository;
import com.authvault.repository.DocumentJpaRepository;
import com.authvault.repository.MarketplaceListingJpaRepository;
import com.authvault.repository.OwnershipHistoryJpaRepository;
import com.authvault.service.AssetService;
import com.authvault.service.BlockchainService;
import com.authvault.service.VaultAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
@Service
public class AssetServiceImpl implements AssetService {
 private final AssetJpaRepository assets;private final AssetMetadataJpaRepository metadata;private final AssetHashJpaRepository hashes;
 private final DocumentJpaRepository documents;
 private final OwnershipHistoryJpaRepository history;private final MarketplaceListingJpaRepository listings;private final VaultAccessService access;
 private final BlockchainService blockchainService;
 private final AssetFingerprints fingerprints;private final ObjectMapper json;private final EntityManager manager;private final Path directory;private final String secret;private final int strong,possible;
 public AssetServiceImpl(AssetJpaRepository assets,AssetMetadataJpaRepository metadata,AssetHashJpaRepository hashes,
 DocumentJpaRepository documents,
 OwnershipHistoryJpaRepository history,MarketplaceListingJpaRepository listings,VaultAccessService access,
 BlockchainService blockchainService,AssetFingerprints fingerprints,ObjectMapper json,EntityManager manager,
 @Value("${authvault.asset-directory:${UPLOAD_DIRECTORY:./data/uploads}}") String directory,
 @Value("${authvault.public-id-secret:${PUBLIC_ID_SECRET:${JWT_SECRET:authvault-development-secret}}}") String secret,
 @Value("${authvault.phash-strong-match-max:${PHASH_STRONG_MATCH_MAX:6}}") int strong,
 @Value("${authvault.phash-possible-match-max:${PHASH_POSSIBLE_MATCH_MAX:12}}") int possible){
 this.assets=assets;this.metadata=metadata;this.hashes=hashes;this.documents=documents;this.history=history;this.listings=listings;this.access=access;
 this.blockchainService=blockchainService;
 this.fingerprints=fingerprints;this.json=json;this.manager=manager;this.directory=Path.of(directory).toAbsolutePath().normalize();this.secret=secret;
 this.strong=Math.max(0,strong);this.possible=Math.max(this.strong,possible);}
 private static Long number(Object value){return value instanceof Number n?n.longValue():null;}
 private static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 private static long id(String value){try{long n=Long.parseLong(value);if(n>0)return n;}catch(Exception ignored){}throw new ApiException(400,"Asset id must be a positive integer");}
 private Asset owned(long user,String value){return assets.findByIdAndOwnerId(id(value),user).orElseThrow(()->new ApiException(404,"Asset not found"));}
 private void validate(MultipartFile file,String missing){if(file==null||file.isEmpty())throw new ApiException(400,missing);
 if(file.getSize()>20L*1024*1024)throw new ApiException(413,"File size exceeds the 20 MB limit");
 String name=file.getOriginalFilename()==null?"":file.getOriginalFilename().toLowerCase(Locale.ROOT);
 if(!Set.of("image/png","image/jpeg","image/webp").contains(file.getContentType())||
 !(name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".webp")))
 throw new ApiException(400,"Only jpg, jpeg, png, and webp files are allowed");}
 private Map<String,Object> publicAsset(Asset a,String token,boolean uploaded){AssetMetadata m=metadata.findByAssetId(a.getId()).orElse(null);
 AssetHash h=hashes.findByAssetId(a.getId()).orElse(null);Map<String,Object> protection=uploaded?
 map("passwordProtected",false,"isLocked",false,"protectingVaults",List.of()):access.protection(a.getOwnerId(),a.getId(),token);
 boolean locked=Boolean.TRUE.equals(protection.get("isLocked"));return map("id",a.getId(),"title",a.getTitle(),"description",a.getDescription(),
 "category",a.getCategory(),"fileName",a.getFileName(),"fileSize",a.getFileSize(),"mimeType",a.getMimeType(),"status",a.getStatus(),
 "createdAt",a.getCreatedAt(),"updatedAt",a.getUpdatedAt(),"width",locked||m==null?null:m.getWidth(),
 "height",locked||m==null?null:m.getHeight(),"sha256",locked||h==null?null:h.getSha256Hash(),"phash",locked||h==null?null:h.getPhash(),
 "hasHash",h!=null,"hasMetadata",m!=null,"contentUrl",locked?null:"/api/assets/"+a.getId()+"/content","vaultProtection",protection);}
 @Override @Transactional public Map<String,Object> upload(long user,String title,String description,String category,MultipartFile file){
 if(title==null||title.isBlank())throw new ApiException(400,"Title is required");if(category==null||category.isBlank())throw new ApiException(400,"Category is required");
 validate(file,"File is required");Path stored=null;try{byte[] bytes=file.getBytes();var fp=fingerprints.compute(bytes,file.getContentType(),true);
 var duplicates=hashes.findByPhashOrderByAssetIdAsc(fp.phash());if(!duplicates.isEmpty()){Long duplicateId=duplicates.get(0).getAssetId();
 boolean own=assets.findById(duplicateId).map(a->a.getOwnerId()==user).orElse(false);throw new ApiException(409,own?
 "This image was already uploaded before as asset id "+duplicateId:"This image was already uploaded to VaultChain");}
 var shaDups=hashes.findBySha256HashOrderByAssetIdAsc(fp.sha256());if(!shaDups.isEmpty()){Long duplicateId=shaDups.get(0).getAssetId();
 boolean own=assets.findById(duplicateId).map(a->a.getOwnerId()==user).orElse(false);throw new ApiException(409,own?
 "This image was already uploaded before as asset id "+duplicateId:"This image was already uploaded to VaultChain");}
 String rawSha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
 var docDups=documents.findBySha256Hash(rawSha);
 if(!docDups.isEmpty()){throw new ApiException(409,"This file was already uploaded to VaultChain as a document (\""+docDups.get(0).getOriginalName()+"\")");}
 var docDups2=documents.findBySha256Hash(fp.sha256());
 if(!docDups2.isEmpty()){throw new ApiException(409,"This file was already uploaded to VaultChain as a document (\""+docDups2.get(0).getOriginalName()+"\")");}
 Files.createDirectories(directory);String name=file.getOriginalFilename();stored=directory.resolve(UUID.randomUUID()+name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT));
 Files.write(stored,bytes);Asset a=new Asset();a.setOwnerId(user);a.setTitle(title.trim());a.setDescription(description==null?null:description.trim());
 a.setCategory(category.trim());a.setFileName(stored.getFileName().toString());a.setFilePath(stored.toString());a.setFileSize(file.getSize());
 a.setMimeType(file.getContentType());a.setStatus("active");a=assets.saveAndFlush(a);manager.refresh(a);
 Map<String,Object> extracted=fp.metadata();AssetMetadata m=new AssetMetadata();m.setAssetId(a.getId());m.setWidth(number(extracted.get("width")));
 m.setHeight(number(extracted.get("height")));m.setCamera((String)extracted.get("camera"));m.setLocation((String)extracted.get("location"));
 m.setCreatedDate((String)extracted.get("createdDate"));m.setMetadataJson(json.writeValueAsString(extracted.get("metadataJson")));metadata.saveAndFlush(m);
 AssetHash h=new AssetHash();h.setAssetId(a.getId());h.setSha256Hash(fp.sha256());h.setPhash(fp.phash());hashes.saveAndFlush(h);
 blockchainService.recordBlock(a.getId(),user,"REGISTER_ASSET","ASSET:"+a.getId()+":"+fp.sha256());
 Map<String,Object> publicMetadata=map("assetId",a.getId(),"width",m.getWidth(),"height",m.getHeight(),"pixelCount",extracted.get("pixelCount"),
 "patterns",extracted.get("patterns"),"camera",m.getCamera(),"location",m.getLocation(),"createdDate",m.getCreatedDate(),"metadataJson",extracted.get("metadataJson"));
 return map("asset",publicAsset(a,null,true),"hash",map("sha256",h.getSha256Hash(),"phash",h.getPhash(),"alreadyUploadedBefore",false,"duplicateAssetId",null),
 "metadata",publicMetadata,"pixelCount",extracted.get("pixelCount"),"blockchain",blockchainService.getVerificationFlow(a.getId(),fp.sha256()));
 }catch(ApiException e){remove(stored);throw e;}catch(Exception e){remove(stored);throw new ApiException(500,e.getMessage());}}
 private static void remove(Path p){if(p!=null)try{Files.deleteIfExists(p);}catch(Exception ignored){}}
 @Override public List<Map<String,Object>> list(long user,String token){return assets.findByOwnerIdOrderByCreatedAtDescIdDesc(user).stream().map(a->publicAsset(a,token,false)).toList();}
 @Override public Map<String,Object> get(long user,String id,String token){return publicAsset(owned(user,id),token,false);}
 @Override public Map<String,Object> metadata(long user,String id,String token){Asset a=owned(user,id);access.assertAssetUnlocked(user,a.getId(),token);
 AssetMetadata m=metadata.findByAssetId(a.getId()).orElseThrow(()->new ApiException(404,"Asset metadata not found"));Map<String,Object> raw;
 try{raw=json.readValue(m.getMetadataJson(),Map.class);}catch(Exception e){raw=Map.of();}
 return map("width",m.getWidth(),"height",m.getHeight(),"pixelCount",raw.get("pixelCount"),"pixel_count",raw.get("pixelCount"),
 "patterns",raw.get("patterns"),"camera",m.getCamera(),"location",m.getLocation(),"created_date",m.getCreatedDate(),"metadata_json",raw);}
 @Override public Map<String,Object> hash(long user,String id,String token){Asset a=owned(user,id);access.assertAssetUnlocked(user,a.getId(),token);
 AssetHash h=hashes.findByAssetId(a.getId()).orElseThrow(()->new ApiException(404,"Asset hash not found"));
 return map("sha256",h.getSha256Hash(),"phash",h.getPhash(),"alreadyUploadedBefore",false,"duplicateAssetId",null);}
 @Override public ResponseEntity<byte[]> content(long user,String id,String token){Asset a=owned(user,id);access.assertAssetUnlocked(user,a.getId(),token);
 try{Path p=directory.resolve(Path.of(a.getFileName()).getFileName());return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, max-age=300")
 .contentType(MediaType.parseMediaType(a.getMimeType()==null?"application/octet-stream":a.getMimeType())).body(Files.readAllBytes(p));}
 catch(Exception e){throw new ApiException(404,"Not Found");}}
 private String reference(Long owner){if(owner==null)return null;try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(),"HmacSHA256"));
 return "VC-"+HexFormat.of().formatHex(mac.doFinal(("owner:"+owner).getBytes())).substring(0,8).toUpperCase(Locale.ROOT);}
 catch(Exception e){throw new IllegalStateException(e);}}
 @Override public List<Map<String,Object>> history(long user,String id){long assetId;try{assetId=Long.parseLong(id);}catch(Exception e){throw new ApiException(404,"Asset not found");}
 if(assetId<=0||assets.findByIdAndOwnerId(assetId,user).isEmpty())throw new ApiException(404,"Asset not found");
 return history.findByAssetIdOrderByTransferredAtDescIdDesc(assetId).stream().map(h->map("transactionReference",h.getTransactionReference(),
 "listingReference",h.getListingId()==null?null:listings.findById(h.getListingId()).map(MarketplaceListing::getPublicReference).orElse(null),
 "price",h.getPrice(),"currency","VaultChain Credits","transferType",h.getTransferType(),"transferredAt",h.getTransferredAt(),
 "previousOwner",reference(h.getPreviousOwner()),"newOwner",reference(h.getNewOwner()))).toList();}
 private Map<String,Object> match(Asset a,long user,String type,Map<String,Object> checked,String similarity,Integer distance,Integer threshold,Integer bits){
 boolean own=a.getOwnerId()==user;return map("match",true,"matchType",type,"similarity",similarity,"distance",distance,"threshold",threshold,
 "hashBits",bits,"checked",checked,"asset",map("id",own?a.getId():null,"reference","AV-A"+String.format("%06d",a.getId()),
 "title",own?a.getTitle():null,"registeredAt",a.getCreatedAt(),"owner",map("isCurrentUser",own,"label",own?"You":reference(a.getOwnerId()))));}
 @Override public Map<String,Object> check(long user,MultipartFile file){validate(file,"Image file is required");try{
 byte[] bytes=file.getBytes();var fp=fingerprints.compute(bytes,file.getContentType(),false);
 for(var h:hashes.findBySha256HashOrderByAssetIdAsc(fp.sha256())){Asset a=assets.findById(h.getAssetId()).orElse(null);
 if(a!=null)return match(a,user,"exact",map("sha256",fp.sha256(),"phash",null),null,null,null,null);}
 var visual=fingerprints.compute(bytes,file.getContentType(),true);Map<String,Object> checked=map("sha256",fp.sha256(),"phash",visual.phash());
 Asset nearest=null;int best=Integer.MAX_VALUE;for(var h:hashes.findByPhashIsNotNull()){
 if(h.getPhash().length()!=visual.phash().length())continue;int distance=0;
 for(int i=0;i<h.getPhash().length();i++)distance+=Integer.bitCount(Character.digit(h.getPhash().charAt(i),16)^Character.digit(visual.phash().charAt(i),16));
 if(distance<best){Asset candidate=assets.findById(h.getAssetId()).orElse(null);if(candidate!=null){nearest=candidate;best=distance;}}}
 if(nearest!=null&&best<=possible){String similarity=best==0?"identical":best<=strong?"strong":"possible";
 return match(nearest,user,"perceptual",checked,similarity,best,similarity.equals("possible")?possible:strong,visual.phash().length()*4);}
 return map("match",false,"matchType",null,"checked",checked,"asset",null);
 }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(500,e.getMessage());}}
 @Override @Transactional public Map<String,Object> delete(long user,String id,String token){
  Asset a=owned(user,id);
  access.assertAssetUnlocked(user,a.getId(),token);
  Number txCount=(Number)manager.createNativeQuery(
   "SELECT COUNT(*) FROM marketplace_transactions WHERE asset_id = :assetId"
  ).setParameter("assetId",a.getId()).getSingleResult();
  if(txCount!=null&&txCount.longValue()>0){
   throw new ApiException(400,"Assets with settled marketplace transactions cannot be deleted");
  }
  manager.createNativeQuery("UPDATE documents SET asset_id = NULL WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("UPDATE verification_reports SET asset_id = NULL WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM marketplace_previews WHERE listing_id IN (SELECT id FROM marketplace_listings WHERE asset_id = :assetId)").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM marketplace_offers WHERE listing_id IN (SELECT id FROM marketplace_listings WHERE asset_id = :assetId)").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM marketplace_options WHERE listing_id IN (SELECT id FROM marketplace_listings WHERE asset_id = :assetId)").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM marketplace_messages WHERE listing_id IN (SELECT id FROM marketplace_listings WHERE asset_id = :assetId)").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM organization_listings WHERE listing_id IN (SELECT id FROM marketplace_listings WHERE asset_id = :assetId)").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM marketplace_listings WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM vault_assets WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM fractional_ownership WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM ownership_history WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM blockchain_blocks WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM asset_metadata WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  manager.createNativeQuery("DELETE FROM asset_hashes WHERE asset_id = :assetId").setParameter("assetId",a.getId()).executeUpdate();
  assets.delete(a);
  assets.flush();
  if(a.getFileName()!=null){
   remove(directory.resolve(Path.of(a.getFileName()).getFileName()));
  }
  if(a.getFilePath()!=null){
   try{remove(Path.of(a.getFilePath()));}catch(Exception ignored){}
  }
  return map("success",true,"message","Asset deleted successfully","id",a.getId());
 }
}
