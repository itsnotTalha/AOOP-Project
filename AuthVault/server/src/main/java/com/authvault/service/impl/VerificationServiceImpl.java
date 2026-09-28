package com.authvault.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.authvault.entity.*;
import com.authvault.exception.ApiException;
import com.authvault.repository.*;
import com.authvault.service.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
@Service
public class VerificationServiceImpl implements VerificationService {
 private final VerificationReportJpaRepository reports;private final AssetHashJpaRepository hashes;private final AssetJpaRepository assets;
 private final VaultAccessService access;private final AssetFingerprints fingerprints;private final ObjectMapper json;private final EntityManager manager;
 private final String secret;private final int strong,possible;
 public VerificationServiceImpl(VerificationReportJpaRepository reports,AssetHashJpaRepository hashes,AssetJpaRepository assets,
 VaultAccessService access,AssetFingerprints fingerprints,ObjectMapper json,EntityManager manager,
 @Value("${authvault.public-id-secret:${PUBLIC_ID_SECRET:${JWT_SECRET:authvault-development-secret}}}") String secret,
 @Value("${authvault.phash-strong-match-max:${PHASH_STRONG_MATCH_MAX:6}}") int strong,
 @Value("${authvault.phash-possible-match-max:${PHASH_POSSIBLE_MATCH_MAX:12}}") int possible){
 this.reports=reports;this.hashes=hashes;this.assets=assets;this.access=access;this.fingerprints=fingerprints;this.json=json;
 this.manager=manager;this.secret=secret;this.strong=Math.max(0,strong);this.possible=Math.max(this.strong,possible);}
 private static Map<String,Object> map(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
 private String hmac(String input,int length){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
 return HexFormat.of().formatHex(mac.doFinal(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,length).toUpperCase(Locale.ROOT);}
 catch(Exception e){throw new IllegalStateException(e);}}
 private String reference(long id){return "VR-"+hmac("verification:"+id,6);}
 private static int distance(String left,String right){if(left==null||right==null||left.length()!=right.length())return Integer.MAX_VALUE;
 int result=0;for(int i=0;i<left.length();i++){int a=Character.digit(left.charAt(i),16),b=Character.digit(right.charAt(i),16);
 if(a<0||b<0)return Integer.MAX_VALUE;result+=Integer.bitCount(a^b);}return result;}
 private Map<String,Object> publicReport(VerificationReport report,long user,String token,boolean detailed){Map<String,Object> raw;
 try{raw=json.readValue(report.getReportJson(),Map.class);}catch(Exception e){raw=Map.of();}
 if(!"global_image_search".equals(report.getVerificationType()))return legacy(report,raw,user,token,detailed);
 List<Map<String,Object>> matches=new ArrayList<>();Object value=raw.get("matches");
 if(value instanceof List<?> rows)for(Object item:rows){if(!(item instanceof Map<?,?> data))continue;
 long assetId=((Number)data.get("assetId")).longValue();long ownerId=((Number)data.get("ownerId")).longValue();boolean own=ownerId==user;
 Map<String,Object> match=map("rank",data.get("rank"),"matchType",data.get("matchType"),"assetReference","AV-A"+String.format("%06d",assetId),
 "ownerReference",own?"You":"VC-"+hmac("owner:"+ownerId,8),"ownerIsCurrentUser",own,"sha256Match",data.get("sha256Match"),
 "distance",data.get("distance"),"hashBits",data.get("hashBits"),"registeredAt",data.get("registeredAt"));
 if(detailed&&own){Asset a=assets.findByIdAndOwnerId(assetId,user).orElse(null);if(a!=null&&!Boolean.TRUE.equals(access.protection(user,assetId,token).get("isLocked")))
 match.put("asset",map("id",a.getId(),"title",a.getTitle(),"mimeType",a.getMimeType(),"contentUrl","/api/assets/"+a.getId()+"/content"));}
 matches.add(match);}
 Object defaults=raw.get("thresholds");Map<?,?> thresholds=defaults instanceof Map<?,?> m?m:Map.of();
 Map<String,Object> result=map("reference",reference(report.getId()),"result",matches.isEmpty()?"no_match":"matches_found", "createdAt",report.getCreatedAt(),"matches",matches,
 "thresholds",map("strong",(thresholds.containsKey("strong")?thresholds.get("strong"):strong),"possible",(thresholds.containsKey("possible")?thresholds.get("possible"):possible),
 "maxResults",(thresholds.containsKey("maxResults")?thresholds.get("maxResults"):5),"hashBits",(thresholds.containsKey("hashBits")?thresholds.get("hashBits"):256)),
 "nearestDistance",raw.get("nearestDistance"),"candidateCount",raw.get("candidateCount"));
 if(detailed)result.put("comparison",raw.get("comparison"));return result;
 }
 private Map<String,Object> legacy(VerificationReport report,Map<String,Object> raw,long user,String token,boolean detailed){
 Map<String,Object> registered=map("reference",report.getAssetId()==null?null:"AV-A"+String.format("%06d",report.getAssetId()));
 if(detailed&&report.getAssetId()!=null){Asset a=assets.findByIdAndOwnerId(report.getAssetId(),user).orElse(null);
 if(a!=null&&!Boolean.TRUE.equals(access.protection(user,a.getId(),token).get("isLocked"))){registered.putAll(map("id",a.getId(),"title",a.getTitle(),
 "fileName",a.getFileName(),"contentUrl","/api/assets/"+a.getId()+"/content"));Object extra=raw.get("registeredAsset");
 if(extra instanceof Map<?,?> m)m.forEach((key,value)->registered.put(String.valueOf(key),value));}}
 Map<String,Object> out=map("reference",reference(report.getId()),"result",report.getStatus(),"createdAt",report.getCreatedAt(),
 "registeredAsset",registered,"fingerprints",raw.getOrDefault("fingerprints",Map.of()));
 if(detailed)out.putAll(map("comparison",raw.get("comparison"),"metadataDifferences",raw.getOrDefault("metadataDifferences",List.of()),"warnings",raw.getOrDefault("warnings",List.of())));
 return out;}
 @Override @Transactional public Map<String,Object> create(long user,String token,MultipartFile file){
 if(file==null||file.isEmpty())throw new ApiException(400,"Comparison image is required");
 if(file.getSize()>20L*1024*1024)throw new ApiException(413,"File size exceeds the 20 MB limit");
 String name=file.getOriginalFilename()==null?"comparison-image":file.getOriginalFilename();String lower=name.toLowerCase(Locale.ROOT);
 if(!Set.of("image/png","image/jpeg","image/webp").contains(file.getContentType())||
 !(lower.endsWith(".png")||lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".webp")))throw new ApiException(400,"Only jpg, jpeg, png, and webp files are allowed");
 try{var fp=fingerprints.compute(file.getBytes(),file.getContentType(),true);List<Map<String,Object>> ranked=new ArrayList<>();
 for(var hash:hashes.findByPhashIsNotNull()){Asset a=assets.findById(hash.getAssetId()).orElse(null);if(a==null)continue;
 boolean exact=fp.sha256().equals(hash.getSha256Hash());int d=exact?0:distance(fp.phash(),hash.getPhash());if(d==Integer.MAX_VALUE)continue;
 String type=exact?"exact":d<=strong?"strong_visual":d<=possible?"possible_visual":"no_match";
 ranked.add(map("assetId",a.getId(),"ownerId",a.getOwnerId(),"registeredAt",a.getCreatedAt(),"sha256Match",exact,
 "distance",d,"hashBits",fp.phash().length()*4,"matchType",type));}
 ranked.sort(Comparator.<Map<String,Object>,Boolean>comparing(m->(Boolean)m.get("sha256Match"),Comparator.reverseOrder())
 .thenComparingInt(m->(Integer)m.get("distance")).thenComparingLong(m->((Number)m.get("assetId")).longValue()));
 Integer nearest=ranked.isEmpty()?null:(Integer)ranked.get(0).get("distance");List<Map<String,Object>> matches=new ArrayList<>();
 for(var candidate:ranked)if(Boolean.TRUE.equals(candidate.get("sha256Match"))||(Integer)candidate.get("distance")<=possible){
 candidate.put("rank",matches.size()+1);matches.add(candidate);if(matches.size()==5)break;}
 String safe=PathName.safe(name);Map<String,Object> report=map("comparison",map("fileName",safe,"mimeType",file.getContentType(),"fileSize",file.getSize(),
 "width",fp.metadata().get("width"),"height",fp.metadata().get("height")),"matches",matches,"nearestDistance",matches.isEmpty()?nearest:null,
 "candidateCount",ranked.size(),"thresholds",map("strong",strong,"possible",possible,"maxResults",5,"hashBits",fp.phash().length()*4));
 VerificationReport row=new VerificationReport();row.setUserId(user);row.setAssetId(matches.isEmpty()?null:((Number)matches.get(0).get("assetId")).longValue());
 row.setVerificationType("global_image_search");row.setSha256Match(!matches.isEmpty()&&Boolean.TRUE.equals(matches.get(0).get("sha256Match"))?1L:0L);
 row.setStatus(matches.isEmpty()?"no_match":"matches_found");row.setReportJson(json.writeValueAsString(report));row=reports.saveAndFlush(row);manager.refresh(row);
 return publicReport(row,user,token,true);
 }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(500,e.getMessage());}}
 @Override public List<Map<String,Object>> list(long user,String token){return reports.findByUserIdOrderByCreatedAtDescIdDesc(user).stream().map(r->publicReport(r,user,token,false)).toList();}
 @Override public Map<String,Object> get(long user,String reference,String token){String normalized=reference==null?"":reference.trim().toUpperCase(Locale.ROOT);
 return reports.findByUserIdOrderByCreatedAtDescIdDesc(user).stream().filter(r->reference(r.getId()).equals(normalized)).findFirst()
 .map(r->publicReport(r,user,token,true)).orElseThrow(()->new ApiException(404,"Verification report not found"));}
 private static class PathName {static String safe(String input){String name=input.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1)
 .replaceAll("[\\x00-\\x1f\\x7f]","");if(name.length()>180)name=name.substring(0,180);return name.isBlank()?"comparison-image":name;}}
}
