package com.authvault.service;

import java.time.LocalDate;
import java.util.*;
import com.authvault.exception.ApiException;
import com.authvault.repository.AuthRepository;
import com.authvault.security.LegacyPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountExtras {
 private final JdbcTemplate db;
 private final AuthRepository users;
 private final LegacyPasswordEncoder passwords;
 public static final List<Map<String,String>> QUESTIONS = List.of(
  Map.of("id","childhood","label","What was your childhood nickname?"),
  Map.of("id","school","label","What was the name of your first school?"),
  Map.of("id","place","label","In which city did your parents meet?"));
 public AccountExtras(JdbcTemplate db,AuthRepository users,LegacyPasswordEncoder passwords){this.db=db;this.users=users;this.passwords=passwords;}
 public String username(long id){return db.query("SELECT username FROM account_extras WHERE user_id=?",(r,n)->r.getString(1),id).stream().filter(Objects::nonNull).findFirst().orElse("user_"+id);}
 public Long resolve(String identifier){
  String value=identifier.trim().toLowerCase(Locale.ROOT);
  var ids=db.query("SELECT user_id FROM account_extras WHERE username=?",(r,n)->r.getLong(1),value);
  if(!ids.isEmpty())return ids.getFirst();
  if(value.matches("user_[0-9]+")){long id=Long.parseLong(value.substring(5)); if(username(id).equals(value)&&users.findById(id).isPresent())return id;}
  return users.findByEmail(value).map(u->u.id()).orElse(null);
 }
 public void setUsername(long id,String value){
  value=value.trim().toLowerCase(Locale.ROOT);
  if(!value.matches("[a-z0-9_]{3,30}") || value.matches("user_[0-9]+")&&!value.equals("user_"+id))throw new ApiException(400,"Username must be 3–30 letters, numbers or underscores and must not use another account's reserved name");
  Long existing=resolve(value);if(existing!=null&&existing!=id)throw new ApiException(409,"Username is already taken");
  try {db.update("INSERT INTO account_extras(user_id,username) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET username=excluded.username",id,value);}
  catch(org.springframework.dao.DuplicateKeyException e){throw new ApiException(409,"Username is already taken");}
 }
 public int version(long id){return db.query("SELECT session_version FROM account_extras WHERE user_id=?",(r,n)->r.getInt(1),id).stream().findFirst().orElse(0);}
 public Map<String,Object> settings(long id){
  var rows=db.queryForList("SELECT recovery_question,recovery_code FROM account_extras WHERE user_id=?",id);
  String q=rows.isEmpty()?"":Objects.toString(rows.getFirst().get("recovery_question"),"");
  return Map.of("enabled",!rows.isEmpty()&&rows.getFirst().get("recovery_code")!=null,"question",q,"questions",QUESTIONS);
 }
 @Transactional
 public Map<String,Object> save(long id,Map<String,Object> b){
  var user=users.findById(id).orElseThrow();
  if(!passwords.matches(text(b,"currentPassword"),user.passwordHash()))throw new ApiException(401,"Current password is incorrect");
  String question=text(b,"question"),answer=normalized(text(b,"answer")),birth=text(b,"birthDate");
  if(QUESTIONS.stream().noneMatch(q->q.get("id").equals(question))||answer.length()<2||answer.length()>200)throw new ApiException(400,"Choose a security question and an answer of 2–200 characters");
  try{LocalDate date=LocalDate.parse(birth);if(date.isAfter(LocalDate.now())||date.isBefore(LocalDate.of(1900,1,1)))throw new IllegalArgumentException();}catch(Exception e){throw new ApiException(400,"Enter a valid birth date");}
  String code=UUID.randomUUID().toString()+"-"+UUID.randomUUID().toString().substring(0,8);
  db.update("INSERT INTO account_extras(user_id) VALUES(?) ON CONFLICT DO NOTHING",id);
  db.update("UPDATE account_extras SET recovery_question=?,recovery_answer=?,recovery_birth=?,recovery_code=?,recovery_attempts=0,recovery_locked_until=0 WHERE user_id=?",question,passwords.encode(answer),passwords.encode(birth),passwords.encode(code),id);
  return Map.of("question",question,"recoveryCode",code);
 }
 // Serialize attempts and code consumption, including failed attempts (which must not roll back).
 public synchronized Map<String,Object> reset(Map<String,Object> b){
  String replacement=text(b,"newPassword");
  if(replacement.length()<8||replacement.length()>72)throw new ApiException(400,"Password must be 8–72 characters");
  Long id=resolve(text(b,"identifier"));
  var rows=id==null?List.<Map<String,Object>>of():db.queryForList("SELECT * FROM account_extras WHERE user_id=?",id);
  if(rows.isEmpty())throw new ApiException(400,"Recovery details are invalid");
  var row=rows.getFirst();long now=System.currentTimeMillis();
  if(((Number)row.get("recovery_locked_until")).longValue()>now)throw new ApiException(429,"Too many attempts. Try again in 15 minutes");
  boolean valid=row.get("recovery_code")!=null&&Objects.equals(row.get("recovery_question"),text(b,"question"))&&passwords.matches(normalized(text(b,"answer")),Objects.toString(row.get("recovery_answer"),""))&&passwords.matches(text(b,"birthDate"),Objects.toString(row.get("recovery_birth"),""))&&passwords.matches(text(b,"recoveryCode"),Objects.toString(row.get("recovery_code"),""));
  if(!valid){int attempts=((Number)row.get("recovery_attempts")).intValue()+1;db.update("UPDATE account_extras SET recovery_attempts=?,recovery_locked_until=? WHERE user_id=?",attempts>=5?0:attempts,attempts>=5?now+900000:0,id);throw new ApiException(400,"Recovery details are invalid");}
  // One SQL update atomically changes password, consumes the code and revokes old JWT versions.
  new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource())).execute(status->{
   db.update("UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",passwords.encode(replacement),id);
   db.update("UPDATE account_extras SET recovery_code=NULL,recovery_attempts=0,session_version=session_version+1 WHERE user_id=?",id);return null;
  });
  return Map.of("success",true,"message","Password reset successfully. Sign in with your new password.");
 }
 private static String text(Map<String,Object>b,String key){return Objects.toString(b.get(key),"");}
 private static String normalized(String s){return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
}
