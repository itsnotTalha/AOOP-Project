package com.authvault.security;

import com.authvault.exception.ApiException;
import com.authvault.repository.AuthRepository;
import org.springframework.stereotype.Service;

@Service
public class TokenAuthentication {
    private final JwtService tokens;
    private final AuthRepository users;
    @org.springframework.beans.factory.annotation.Autowired private com.authvault.service.AccountExtras extras;

    public TokenAuthentication(JwtService tokens, AuthRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    public CurrentUser authenticate(String header) {
        String[] parts = (header == null ? "" : header).split(" ", -1);
        if (parts.length < 2 || !parts[0].equals("Bearer") || parts[1].isEmpty()) {
            throw new ApiException(401, "Unauthorized");
        }
        String token = parts[1]; // Like Node's destructuring, ignore additional space-separated fields.
        var decoded = tokens.verify(token);
        Long id;
        try {
            Object claim = decoded.getClaim("id").as(Object.class);
            id = claim == null ? null : Long.valueOf(claim.toString());
        } catch (RuntimeException invalidId) {
            id = null;
        }
        var user = users.findById(id).orElseThrow(() -> new ApiException(401, "Account no longer exists"));
        Integer version = decoded.getClaim("sessionVersion").asInt();
        if ((version == null ? 0 : version) != extras.version(user.id())) throw new ApiException(401, "Session expired. Sign in again");
        if ("suspended".equals(user.status())) throw new ApiException(403, "This account has been suspended");
        return new CurrentUser(user.id(), user.email(), user.role(), user.status(), JwtService.fingerprint(token));
    }
}
