package com.authvault.service;

import com.authvault.exception.ApiException;
import com.authvault.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
 private final JdbcTemplate db; private final ObjectMapper json; private final AccountExtras accounts;
 private final VaultService vaults; private final MarketplaceService market;
 public OrganizationService(JdbcTemplate db,ObjectMapper json,AccountExtras accounts,VaultService vaults,MarketplaceService market){this.db=db;this.json=json;this.accounts=accounts;this.vaults=vaults;this.market=market;}
 private Map<String,Object> parse(String s){try{return json.readValue(s,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 @SuppressWarnings("unchecked") private List<Map<String,Object>> items(Map<String,Object>org,String key){return (List<Map<String,Object>>)org.computeIfAbsent(key,k->new ArrayList<>());}
 private static String text(Map<String,Object>b,String k){return Objects.toString(b.get(k),"").trim();}
 private static double number(Object n){try{double v=Double.parseDouble(String.valueOf(n));if(!Double.isFinite(v))throw new Exception();return v;}catch(Exception e){throw new ApiException(400,"Enter a valid number");}}
 private static double money(Object n){double v=number(n);if(v<=0||v>1e9||Math.abs(v*100-Math.round(v*100))>1e-6)throw new ApiException(400,"Amount must be positive, with at most two decimal places");return v;}
 private static String required(Map<String,Object>b,String key,int max){String v=text(b,key);if(v.isEmpty()||v.length()>max)throw new ApiException(400,key+" is required (maximum "+max+" characters)");return v;}
 private boolean member(Map<String,Object>o,long user){return items(o,"members").stream().anyMatch(m->String.valueOf(user).equals(String.valueOf(m.get("id"))));}
 private Map<String,Object> org(String id,long user,boolean write){var rows=db.queryForList("SELECT owner_id,data FROM organizations WHERE id=?",id);if(rows.isEmpty())throw new ApiException(404,"Organization not found");var row=rows.getFirst();var o=parse((String)row.get("data"));boolean owner=((Number)row.get("owner_id")).longValue()==user;if(!owner&&!member(o,user))throw new ApiException(404,"Organization not found");if(write&&!owner)throw new ApiException(403,"Only the organization owner can make this change");return o;}
 private void save(Map<String,Object>o){db.update("UPDATE organizations SET data=? WHERE id=?",encode(o),o.get("id"));}
 public List<Map<String,Object>> list(CurrentUser u){
  List<Map<String,Object>> result=new ArrayList<>();
  for(var row:db.queryForList("SELECT owner_id,data FROM organizations ORDER BY rowid DESC")){
   var o=parse((String)row.get("data"));boolean owner=((Number)row.get("owner_id")).longValue()==u.id();if(!owner&&!member(o,u.id()))continue;
   o.put("isOwner",owner);
   if(owner){List<Map<String,Object>> current=new ArrayList<>();for(var v:items(o,"companyVaults")){try{current.add(vaults.get(u.id(),(String)v.get("reference"),u.tokenFingerprint()));}catch(ApiException ignored){}}o.put("companyVaults",current);}
   else{o.put("companyVaults",items(o,"companyVaults").stream().map(v->Map.of("reference",v.get("reference"),"name",v.get("name"),"isLocked",true,"passwordProtected",true,"description","Managed by the organization owner")).toList());}
   List<Map<String,Object>> listings=new ArrayList<>();
   for(var l:db.queryForList("SELECT m.public_reference FROM marketplace_listings m JOIN organization_listings o ON o.listing_id=m.id WHERE o.organization_id=?",o.get("id"))){var listing=market.get(u.id(),(String)l.get("public_reference"),u.tokenFingerprint());var item=new LinkedHashMap<>(listing);item.put("id",listing.get("reference"));item.put("status",switch((String)listing.get("status")){case "active"->"Active";case "sold"->"Sold";default->"Cancelled";});item.put("category","Digital Asset");item.put("creator",o.get("name"));item.put("views",0);item.put("listedAt",listing.get("createdAt"));listings.add(item);}o.put("listings",listings);
   result.add(o);
  }return result;
 }
 public List<Map<String,Object>> search(String q){
  if(q==null||q.trim().length()<2)return List.of();
  return db.queryForList("SELECT u.id,u.full_name AS name,COALESCE(e.username,'user_'||u.id) AS username FROM users u LEFT JOIN account_extras e ON e.user_id=u.id WHERE u.status='active' AND (u.full_name LIKE ? OR COALESCE(e.username,'user_'||u.id) LIKE ? OR u.email=?) LIMIT 20","%"+q.trim()+"%","%"+q.trim().replace("@","")+"%",q.trim());
 }
 private Map<String,Object> person(long id,String role,double split){var u=db.queryForMap("SELECT id,full_name,email FROM users WHERE id=?",id);return new LinkedHashMap<>(Map.of("id",String.valueOf(id),"name",u.get("full_name"),"username",accounts.username(id),"email",u.get("email"),"role",role,"split",split,"status","Active","joinedDate",java.time.LocalDate.now().toString(),"avatarColor","linear-gradient(135deg, #6366f1, #8b5cf6)"));}
 @Transactional public Map<String,Object> create(CurrentUser u,Map<String,Object>b){
  String name=required(b,"name",80),id="org-"+UUID.randomUUID();double split=number(b.getOrDefault("splitPercent",80));if(split<0||split>100)throw new ApiException(400,"Split must be between 0 and 100");
  Map<String,Object> o=new LinkedHashMap<>();o.put("id",id);o.put("name",name);o.put("slug",name.toLowerCase().replaceAll("[^a-z0-9]","-"));o.put("verified",false);o.put("entityId",id);o.put("jurisdiction",text(b,"jurisdiction"));o.put("splitPercent",split);o.put("logoColor","linear-gradient(135deg, #10b981, #06b6d4)");o.put("treasuryBalance",0);o.put("totalSales",0);o.put("members",new ArrayList<>(List.of(person(u.id(),"Owner",100))));for(String key:List.of("companyVaults","listings","sales","treasuryTransactions"))o.put(key,new ArrayList<>());
  db.update("INSERT INTO organizations(id,owner_id,data) VALUES(?,?,?)",id,u.id(),encode(o));o.put("isOwner",true);return o;
 }
 private void wallet(long user,double delta,String description,String ref){
  if(db.update("UPDATE wallets SET balance=ROUND(balance+?,2) WHERE user_id=? AND balance+?>=-0.000001",delta,user,delta)!=1)throw new ApiException(400,"Insufficient wallet balance");
  db.update("INSERT INTO wallet_transactions(wallet_id,type,amount,description,reference_id) SELECT id,?,?,?,? FROM wallets WHERE user_id=?",delta<0?"withdrawal":"deposit",Math.abs(delta),description,ref,user);
 }
 private void record(Map<String,Object>o,double amount,String type,String note,String recipient,String ref){items(o,"treasuryTransactions").addFirst(new LinkedHashMap<>(Map.of("id",ref,"txHash",ref,"type",type,"category",note,"note",note,"recipient",recipient,"amount",amount,"status","Executed","date",java.time.Instant.now().toString())));}
 @Transactional public synchronized Map<String,Object> mutate(CurrentUser u,String id,String action,Map<String,Object>b){
  var o=org(id,u.id(),true); Object result=o;
  switch(action){
   case "inviteMember"->{Long target=accounts.resolve(text(b,"username").isEmpty()?text(b,"email"):text(b,"username"));if(target==null)throw new ApiException(404,"Ask this contributor to register first");if(member(o,target))throw new ApiException(409,"Already a member");double split=number(b.getOrDefault("split",0));if(split<0||split>100)throw new ApiException(400,"Split must be 0–100");String role=required(b,"role",60);if(role.equalsIgnoreCase("Owner"))throw new ApiException(400,"There can only be one owner");items(o,"members").add(person(target,role,split));}
   case "removeMember"->{String memberId=required(b,"memberId",40);if(memberId.equals(String.valueOf(u.id())))throw new ApiException(400,"Cannot remove the owner");if(!items(o,"members").removeIf(m->memberId.equals(m.get("id"))))throw new ApiException(404,"Member not found");}
   case "createCompanyVault"->{var v=vaults.create(u.id(),b);items(o,"companyVaults").add(v);result=v;}
   case "toggleCompanyVaultLock"->{String ref=required(b,"vaultRef",80);if(items(o,"companyVaults").stream().noneMatch(v->ref.equals(v.get("reference"))))throw new ApiException(404,"Vault not found");var v=vaults.get(u.id(),ref,u.tokenFingerprint());result=Boolean.TRUE.equals(v.get("isLocked"))?vaults.unlock(u.id(),ref,b.get("password"),u.tokenFingerprint()):vaults.lock(u.id(),ref,u.tokenFingerprint());}
   case "addListing"->{var l=market.create(u.id(),u.tokenFingerprint(),b);db.update("INSERT INTO organization_listings(listing_id,organization_id) SELECT id,? FROM marketplace_listings WHERE public_reference=?",id,l.get("reference"));result=l;}
   case "updateListingPrice"->{String ref=required(b,"listingId",80);if(db.queryForList("SELECT m.id FROM marketplace_listings m JOIN organization_listings o ON m.id=o.listing_id WHERE o.organization_id=? AND m.public_reference=?",id,ref).isEmpty())throw new ApiException(404,"Listing not found");result=market.update(u.id(),ref,u.tokenFingerprint(),Map.of("price",money(b.get("newPrice"))));}
   case "depositTreasury","withdrawTreasury","batchDisburseDividends"->{
    double amount=money(b.get("amount")),balance=number(o.get("treasuryBalance"));String ref="ORG-TX-"+UUID.randomUUID();
    if(action.equals("depositTreasury")){wallet(u.id(),-amount,"Organization treasury deposit: "+o.get("name"),ref);balance+=amount;record(o,amount,"Inflow","Wallet deposit",(String)o.get("name"),ref);}
    else{if(amount>balance)throw new ApiException(400,"Insufficient treasury balance");balance-=amount;
     if(action.equals("withdrawTreasury")){wallet(u.id(),amount,"Organization treasury withdrawal: "+o.get("name"),ref);record(o,amount,"Outflow","Withdrawal to owner wallet","Owner wallet",ref);}
     else{var members=items(o,"members");double total=members.stream().mapToDouble(m->number(m.get("split"))).sum();if(total<=0)throw new ApiException(400,"Set contributor shares first");long cents=Math.round(amount*100),remaining=cents;for(int i=0;i<members.size();i++){var m=members.get(i);long payout=i==members.size()-1?remaining:Math.min(remaining,Math.round(cents*number(m.get("split"))/total));remaining-=payout;if(payout>0)wallet(Long.parseLong((String)m.get("id")),payout/100.0,"Organization dividend: "+o.get("name"),ref);items(o,"sales").addFirst(new LinkedHashMap<>(Map.of("id","tx-payout-"+ref+"-"+m.get("id"),"date",java.time.Instant.now().toString(),"assetTitle","Contributor dividend","buyer",o.get("name"),"grossAmount",payout/100.0,"treasuryCut",0,"creatorPayout",payout/100.0,"contributorName",m.get("name"),"txHash",ref)));}record(o,amount,"Outflow","Contributor dividends","Contributor wallets",ref);}
    }o.put("treasuryBalance",Math.round(balance*100)/100.0);
   }
   default->throw new ApiException(404,"Unknown organization action");
  }
  save(o);return Map.of("result",result,"organizations",list(u));
 }
}
