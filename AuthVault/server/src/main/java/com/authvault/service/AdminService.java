package com.authvault.service;
import com.authvault.security.CurrentUser;
import java.util.Map;
public interface AdminService {
    Object read(CurrentUser user,String section,Map<String,String> options);
    Object update(CurrentUser user,String section,Long id,Map<String,Object> body,String address);
    void markNotificationsRead(CurrentUser user);
}
