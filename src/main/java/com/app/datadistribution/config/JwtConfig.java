// JwtConfig.java  (mostly unchanged – just confirming it's fine)
package com.app.datadistribution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    @Positive(message = "Access token expiration must be positive")
    private long accessTokenExpirationMs = 900_000L; // 15 min
//    private long accessTokenExpirationMs = 900_000L; // 15 min

    @Positive(message = "Refresh token expiration must be positive")
    private long refreshTokenExpirationMs = 604_800_000L; // 7 days

    // Optional: called by Spring after binding (you can remove if not needed)
    public void afterPropertiesSet() {
        byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret too weak (< 256 bits)");
        }
    }
}