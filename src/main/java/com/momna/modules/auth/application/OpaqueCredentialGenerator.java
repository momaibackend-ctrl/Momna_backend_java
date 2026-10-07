package com.momna.modules.auth.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpaqueCredentialGenerator {
    private final SecureRandom random = new SecureRandom();

    public String opaque() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String numericCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
