package com.authvault.controller;

import com.authvault.dto.AccountResponse;
import com.authvault.security.CurrentUser;
import com.authvault.service.AuthService;
import com.authvault.service.VaultSessionRevoker;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.authvault.service.AccountExtras;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService accounts;
    private final AccountExtras extras;
    private final VaultSessionRevoker sessions;
    public AuthController(AuthService accounts, VaultSessionRevoker sessions, AccountExtras extras) {
        this.accounts = accounts;
        this.extras = extras;
        this.sessions = sessions;
    }

    @GetMapping("/recovery/questions") public Map<String,Object> questions(){return Map.of("questions",AccountExtras.QUESTIONS);}
    @GetMapping("/recovery") public Map<String,Object> recovery(@AuthenticationPrincipal CurrentUser user){return extras.settings(user.id());}
    @PutMapping("/recovery") public Map<String,Object> recoverySave(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body){return extras.save(user.id(),body);}
    @PostMapping("/forgot-password") public Map<String,Object> reset(@RequestBody Map<String,Object> body){return extras.reset(body);}

    @PostMapping({"/register", "/register/"})
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse.SignedIn register(@RequestBody(required = false) Map<String, Object> body) {
        return accounts.register(body);
    }

    @PostMapping({"/login", "/login/"})
    public AccountResponse.SignedIn login(@RequestBody(required = false) Map<String, Object> body) {
        return accounts.login(body);
    }

    @GetMapping({"/me", "/me/"})
    public AccountResponse.Me me(@AuthenticationPrincipal CurrentUser user) {
        return new AccountResponse.Me(true, accounts.me(user.id()));
    }

    @PatchMapping({"/profile", "/profile/"})
    public AccountResponse.Profile profile(@AuthenticationPrincipal CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        return new AccountResponse.Profile(true, "Profile updated successfully", accounts.updateProfile(user.id(), body));
    }

    @PatchMapping({"/password", "/password/"})
    public AccountResponse.Message password(@AuthenticationPrincipal CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        accounts.changePassword(user.id(), body);
        return new AccountResponse.Message(true, "Password changed successfully");
    }

    @PostMapping({"/logout", "/logout/"})
    public AccountResponse.Message logout(@AuthenticationPrincipal CurrentUser user) {
        sessions.revokeTokenAccess(user.id(), user.tokenFingerprint());
        return new AccountResponse.Message(true, "Logged out successfully");
    }
}
