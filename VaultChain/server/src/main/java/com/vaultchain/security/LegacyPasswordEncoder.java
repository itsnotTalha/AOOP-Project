package com.vaultchain.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Node bcrypt uses at most the first 72 UTF-8 bytes, even across a code-point boundary. */
@Component
public class LegacyPasswordEncoder implements PasswordEncoder {
    @Override
    public String encode(CharSequence password) {
        return BCrypt.hashpw(bytes(password), BCrypt.gensalt("$2b", 10));
    }

    public String encodeVault(CharSequence password) {
        return BCrypt.hashpw(bytes(password), BCrypt.gensalt("$2b", 12));
    }

    @Override
    public boolean matches(CharSequence password, String encoded) {
        if (encoded == null) return false;
        try {
            return BCrypt.checkpw(bytes(password), encoded);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    private byte[] bytes(CharSequence password) {
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                    .replaceWith(new byte[]{(byte) 0xef, (byte) 0xbf, (byte) 0xbd})
                    .encode(java.nio.CharBuffer.wrap(password));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes.length > 72 ? Arrays.copyOf(bytes, 72) : bytes;
        } catch (java.nio.charset.CharacterCodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
