package com.authvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest @AutoConfigureMockMvc
class ClientIntegrationTest {
 @TempDir static Path directory;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("authvault.database-path",()->directory.resolve("integration.sqlite").toString());r.add("authvault.asset-directory",()->directory.resolve("uploads").toString());r.add("authvault.document-directory",()->directory.resolve("documents").toString());}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 JsonNode call(MockHttpServletRequestBuilder request,String token,Object body,int status)throws Exception{
  if(token!=null)request.header("Authorization","Bearer "+token);
  if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
  return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
 }
 JsonNode register()throws Exception{return call(post("/api/auth/register"),null,Map.of("fullName","Integration user","email",UUID.randomUUID()+"@example.test","password","Password123!"),201);}
 String token(JsonNode account){return account.path("token").asText();}
 @Test void recoveryIsOneTimeAndRevokesExistingSessions()throws Exception{
  var a=register();String token=token(a),username="test_"+UUID.randomUUID().toString().substring(0,8);
  call(patch("/api/auth/profile"),token,Map.of("fullName","Updated user","username",username),200);
  assertThat(call(post("/api/auth/login"),null,Map.of("identifier",username,"password","Password123!"),200).path("user").path("username").asText()).isEqualTo(username);
  var recovery=call(put("/api/auth/recovery"),token,Map.of("currentPassword","Password123!","question","school","answer"," My SCHOOL ","birthDate","2000-01-01"),200);
  var reset=new HashMap<String,Object>(Map.of("identifier",username,"question","school","answer","my school","birthDate","2000-01-01","recoveryCode",recovery.path("recoveryCode").asText(),"newPassword","NewPassword456!"));
  reset.put("answer","wrong");call(post("/api/auth/forgot-password"),null,reset,400);
  reset.put("answer","my school");call(post("/api/auth/forgot-password"),null,reset,200);
  call(get("/api/auth/me"),token,null,401);
  call(post("/api/auth/forgot-password"),null,reset,400);
  call(post("/api/auth/login"),null,Map.of("identifier",username,"password","NewPassword456!"),200);
  assertThat(db.queryForObject("SELECT recovery_code FROM account_extras WHERE user_id=?",String.class,a.path("user").path("id").asLong())).isNull();
 }
 @Test void organizationsEnforceMembershipAndConserveCredits()throws Exception{
  var owner=register();var member=register();var outsider=register();String t=token(owner);
  String id=call(post("/api/organizations"),t,Map.of("name","Real studio"),200).path("organization").path("id").asText();String base="/api/organizations/"+id+"/";
  call(post(base+"depositTreasury"),token(outsider),Map.of("amount",10),404);
  call(post(base+"inviteMember"),t,Map.of("email",member.path("user").path("email").asText(),"role","Contributor","split",100),200);
  assertThat(call(get("/api/organizations"),token(member),null,200).path("organizations").size()).isEqualTo(1);
  call(post(base+"depositTreasury"),token(member),Map.of("amount",10),403);
  call(post("/api/wallet/transactions"),t,Map.of("type","deposit","amount",100),201);
  call(post(base+"depositTreasury"),t,Map.of("amount",60),200);
  call(post(base+"withdrawTreasury"),t,Map.of("amount",70),400);
  call(post(base+"batchDisburseDividends"),t,Map.of("amount",20),200);
  assertThat(db.queryForObject("SELECT balance FROM wallets WHERE user_id=?",Double.class,owner.path("user").path("id").asLong())).isEqualTo(50);
  assertThat(db.queryForObject("SELECT balance FROM wallets WHERE user_id=?",Double.class,member.path("user").path("id").asLong())).isEqualTo(10);
  assertThat(call(get("/api/organizations"),t,null,200).path("organizations").get(0).path("treasuryBalance").asDouble()).isEqualTo(40);
  call(post(base+"removeMember"),t,Map.of("memberId",member.path("user").path("id").asText()),200);
  assertThat(call(get("/api/organizations"),token(member),null,200).path("organizations").size()).isZero();
 }
 @Test void previewPermissionsOffersAndOrganizationSalesAreServerEnforced()throws Exception{
  var seller=register();var buyer=register();var stranger=register();String s=token(seller),b=token(buyer);
  var file=new MockMultipartFile("file","art.png","image/png",getClass().getResourceAsStream("/image/synthetic-rgba.png"));
  long asset=call(multipart("/api/assets/upload").file(file).param("title","Art").param("category","art"),s,null,201).path("asset").path("id").asLong();
  String org=call(post("/api/organizations"),s,Map.of("name","Market studio"),200).path("organization").path("id").asText();
  String ref=call(post("/api/organizations/"+org+"/addListing"),s,Map.of("assetId",asset,"title","Artwork","price",100,"isAnonymous",true),200).path("result").path("reference").asText();String base="/api/marketplace/listings/"+ref;
  call(get(base+"/content"),b,null,403);
  long request=call(post(base+"/preview-requests"),b,null,200).path("request").path("id").asLong();
  call(patch(base+"/preview-requests/"+request),token(stranger),Map.of("status","approved"),404);
  call(patch(base+"/preview-requests/"+request),s,Map.of("status","approved"),200);
  mvc.perform(get(base+"/content").header("Authorization","Bearer "+b)).andExpect(status().isOk());
  call(patch(base+"/preview-requests/"+request),s,Map.of("status","revoked"),200);
  call(get(base+"/content"),b,null,403);
  call(post(base+"/messages"),b,Map.of("amount",80,"text","My offer"),200);
  long offer=call(get(base+"/messages"),s,null,200).path("messages").get(0).path("id").asLong();
  call(post(base+"/offers/"+offer+"/accept"),token(stranger),null,403);
  call(post(base+"/offers/"+offer+"/accept"),b,null,403);
  call(post(base+"/offers/"+offer+"/accept"),s,null,200);
  assertThat(call(get(base),b,null,200).path("listing").path("price").asDouble()).isEqualTo(80);
  assertThat(call(get(base),token(stranger),null,200).path("listing").path("price").asDouble()).isEqualTo(100);
  assertThat(call(get(base+"/messages"),token(stranger),null,200).path("messages").size()).isZero();
  call(post("/api/wallet/transactions"),b,Map.of("type","deposit","amount",100),201);
  call(post(base+"/purchase"),b,null,200);
  assertThat(db.queryForObject("SELECT balance FROM wallets WHERE user_id=?",Double.class,buyer.path("user").path("id").asLong())).isEqualTo(20);
  assertThat(db.queryForObject("SELECT balance FROM wallets WHERE user_id=?",Double.class,seller.path("user").path("id").asLong())).isZero();
  assertThat(call(get("/api/organizations"),s,null,200).path("organizations").get(0).path("treasuryBalance").asDouble()).isEqualTo(76);
 }
 @Test void documentIntegrityDetectsTamperingAndPersistsReports()throws Exception{
  var a=register();String t=token(a);
  var image=new java.awt.image.BufferedImage(240,120,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var graphics=image.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,240,120);graphics.setColor(java.awt.Color.BLACK);graphics.drawString("Document integrity test",10,50);graphics.dispose();
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
  var file=new MockMultipartFile("file","document.png","image/png",bytes.toByteArray());
  var document=call(multipart("/api/documents").file(file).param("name","My document").param("description","Registered description"),t,null,201).path("document");long id=document.path("id").asLong();
  assertThat(document.path("metadataSha256").asText()).hasSize(64);
  var report=call(post("/api/documents/"+id+"/integrity"),t,null,200);
  assertThat(report.at("/verification/report/evidence/sha256Match").asBoolean()).isTrue();
  String stored=db.queryForObject("SELECT stored_name FROM documents WHERE id=?",String.class,id);Files.writeString(directory.resolve("documents").resolve(stored),"tampered");
  assertThat(call(post("/api/documents/"+id+"/integrity"),t,null,200).at("/verification/report/evidence/sha256Match").asBoolean()).isFalse();
  assertThat(call(get("/api/documents/"+id+"/report"),t,null,200).path("history").size()).isEqualTo(2);
  call(post("/api/documents/"+id+"/integrity"),token(register()),null,404);
 }
}
