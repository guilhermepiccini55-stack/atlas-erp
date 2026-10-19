package com.atlas.erp.auth;

import com.atlas.erp.auth.dto.LoginRequest;
import com.atlas.erp.auth.dto.LoginResponse;
import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.security.JwtService;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserMapper;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final Duration refreshTtl;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            UserMapper userMapper,
            @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.refreshTtl = Duration.ofDays(refreshExpirationDays);
    }

    /** Autentica (BadCredentialsException/DisabledException viram 401 no handler global) e emite os tokens. */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> invalidSession());
        return issueTokens(user);
    }

    /**
     * Troca um refresh token válido por um novo par de tokens (rotação).
     * Reuso de um token já revogado indica possível roubo: todas as sessões do usuário são encerradas.
     * noRollbackFor garante que essa revogação em massa seja gravada mesmo lançando a exceção.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(AuthService::invalidSession);

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw invalidSession();
        }
        if (stored.isExpired()) {
            throw invalidSession();
        }

        User user = userRepository.findById(stored.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(AuthService::invalidSession);

        stored.revoke();
        refreshTokenRepository.save(stored);
        return issueTokens(user);
    }

    /** Revoga o refresh token informado. Idempotente: token desconhecido não gera erro. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    private LoginResponse issueTokens(User user) {
        String rawRefresh = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(user.getId(), hash(rawRefresh), Instant.now().plus(refreshTtl)));
        return new LoginResponse(
                jwtService.generateAccessToken(user),
                rawRefresh,
                TOKEN_TYPE,
                jwtService.accessTtlSeconds(),
                userMapper.toResponse(user));
    }

    private static BusinessException invalidSession() {
        return new BusinessException("Sessão inválida ou expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
