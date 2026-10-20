package com.atlas.erp.security;

import com.atlas.erp.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Emissão e validação de access tokens JWT (HS256, expiração curta). */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-minutes}") long accessExpirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter pelo menos 32 caracteres. Gere um com: openssl rand -base64 48");
        }
        if (accessExpirationMinutes < 1) {
            throw new IllegalStateException("JWT_ACCESS_EXPIRATION_MINUTES deve ser positivo");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessExpirationMinutes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("credential_version", user.getCredentialVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Valida assinatura e expiração e devolve o e-mail (subject). Lança {@link JwtException} se inválido. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public long extractCredentialVersion(String token) {
        Claims claims = parse(token);
        Object claim = claims.get("credential_version");
        if (!(claim instanceof Number version) || version.longValue() < 0) {
            throw new JwtException("Token sem versão de credencial válida");
        }
        return version.longValue();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
