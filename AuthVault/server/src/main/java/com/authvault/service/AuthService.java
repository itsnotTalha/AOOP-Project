package com.authvault.service;

import com.authvault.dto.AccountResponse;
import com.authvault.exception.ApiException;
import com.authvault.model.UserRecord;
import com.authvault.repository.AuthRepository;
import com.authvault.security.JwtService;
import com.authvault.security.LegacyPasswordEncoder;
import com.authvault.util.LegacyValues;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String NON_SPACE = "[^" + LegacyValues.WHITESPACE.substring(1);
    private static final Pattern EMAIL = Pattern.compile("^" + NON_SPACE + "+@" + NON_SPACE + "+\\." + NON_SPACE + "+$");
    private final AuthRepository users;
    private final LegacyPasswordEncoder passwords;
    private final JwtService tokens;

    public AuthService(AuthRepository users, LegacyPasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    public AccountResponse.SignedIn register(Map<String, Object> input) {
        Map<String, Object> body = input == null ? Map.of() : input;
        String name = name(body);
        String email = email(body);
        String password = LegacyValues.text(body.get("password"));
        require(!name.isEmpty(), "Full name is required");
        require(!email.isEmpty(), "Email is required");
        require(EMAIL.matcher(email).matches(), "Email is invalid");
        require(!password.isEmpty(), "Password is required");
        require(password.length() >= 8, "Password must be at least 8 characters long");
        if (users.findByEmail(email).isPresent()) throw new ApiException(409, "Email is already registered");
        UserRecord user = users.createWithWallet(name, email, passwords.encode(password));
        return signedIn(user, "User registered successfully");
    }

    public AccountResponse.SignedIn login(Map<String, Object> input) {
        Map<String, Object> body = input == null ? Map.of() : input;
        String email = email(body);
        String password = LegacyValues.text(body.get("password"));
        require(!email.isEmpty(), "Email is required");
        require(!password.isEmpty(), "Password is required");
        var user = users.findByEmail(email).orElseThrow(() -> new ApiException(401, "Invalid email or password"));
        if (!passwords.matches(password, user.passwordHash())) throw new ApiException(401, "Invalid email or password");
        if ("suspended".equals(user.status())) throw new ApiException(403, "This account has been suspended");
        return signedIn(user, "Login successful");
    }

    public AccountResponse.MeUser me(long id) { return AccountResponse.MeUser.from(user(id)); }

    public AccountResponse.User updateProfile(long id, Map<String, Object> input) {
        user(id);
        Map<String, Object> body = input == null ? Map.of() : input;
        String name = name(body);
        String email = email(body);
        require(!name.isEmpty(), "Full name is required");
        require(name.length() <= 100, "Full name must be 100 characters or fewer");
        require(!email.isEmpty(), "Email is required");
        require(email.length() <= 254 && EMAIL.matcher(email).matches(), "Email is invalid");
        users.findByEmail(email).filter(owner -> owner.id() != id).ifPresent(owner -> {
            throw new ApiException(409, "Email is already registered");
        });
        return AccountResponse.User.from(users.updateProfile(id, name, email));
    }

    public void changePassword(long id, Map<String, Object> input) {
        Map<String, Object> body = input == null ? Map.of() : input;
        String current = LegacyValues.text(body.get("currentPassword"));
        String replacement = LegacyValues.text(body.get("newPassword"));
        require(!current.isEmpty(), "Current password is required");
        require(!replacement.isEmpty(), "New password is required");
        require(replacement.length() >= 8, "New password must be at least 8 characters long");
        require(!current.equals(replacement), "New password must be different from the current password");
        UserRecord user = user(id);
        if (!passwords.matches(current, user.passwordHash())) throw new ApiException(401, "Current password is incorrect");
        users.updatePassword(id, passwords.encode(replacement));
    }

    public boolean verifyAccountPassword(long id, Object password) {
        return passwords.matches(LegacyValues.text(password), user(id).passwordHash());
    }

    private UserRecord user(long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
    }

    private AccountResponse.SignedIn signedIn(UserRecord user, String message) {
        return new AccountResponse.SignedIn(true, message, tokens.sign(user), AccountResponse.User.from(user));
    }

    private String name(Map<String, Object> body) {
        Object value = LegacyValues.truthy(body.get("fullName")) ? body.get("fullName") : body.get("full_name");
        return LegacyValues.trim(LegacyValues.text(value));
    }

    private String email(Map<String, Object> body) {
        return LegacyValues.trim(LegacyValues.text(body.get("email"))).toLowerCase(Locale.ROOT);
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new ApiException(400, message);
    }
}
