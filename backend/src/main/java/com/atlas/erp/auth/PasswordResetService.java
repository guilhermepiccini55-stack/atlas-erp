package com.atlas.erp.auth;

import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.users.CredentialState;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@ConditionalOnProperty(prefix = "app.password-reset", name = "enabled", havingValue = "true")
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final Duration tokenTtl;
    private final int maxRequestsPerHour;
    private final URI frontendUrl;
    private final String from;

    public PasswordResetService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${app.password-reset.frontend-url:}") String frontendUrl,
            @Value("${app.password-reset.from:}") String from,
            @Value("${app.password-reset.token-expiration-minutes:15}") long tokenExpirationMinutes,
            @Value("${app.password-reset.max-requests-per-hour:3}") int maxRequestsPerHour,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.frontendUrl = validateFrontendUrl(frontendUrl);
        this.from = requireText(from, "PASSWORD_RESET_FROM");
        requireText(smtpHost, "SMTP_HOST");
        if (tokenExpirationMinutes < 1 || maxRequestsPerHour < 1) {
            throw new IllegalStateException("Password reset expiry and request limit must be positive");
        }
        this.tokenTtl = Duration.ofMinutes(tokenExpirationMinutes);
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmailIgnoreCaseForUpdate(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        if (resetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(REQUEST_WINDOW))
                >= maxRequestsPerHour) {
            return;
        }

        String rawToken = generateToken();
        resetTokenRepository.save(new PasswordResetToken(
                user.getId(), AuthService.hash(rawToken), now.plus(tokenTtl)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Redefinição de senha do Atlas ERP");
        message.setText("Use o link abaixo para redefinir sua senha. O link expira em "
                + tokenTtl.toMinutes() + " minutos e pode ser usado uma única vez:\n\n"
                + UriComponentsBuilder.fromUri(frontendUrl).queryParam("token", rawToken).build().toUriString());
        mailSender.send(message);
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokenRepository.findByTokenHashForUpdate(AuthService.hash(rawToken))
                .filter(candidate -> !candidate.isConsumed() && !candidate.isExpiredAt(now))
                .orElseThrow(PasswordResetService::invalidToken);
        User user = userRepository.findById(token.getUserId()).orElseThrow(PasswordResetService::invalidToken);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialState(CredentialState.BCRYPT);
        user.incrementCredentialVersion();
        userRepository.save(user);

        token.consume(now);
        resetTokenRepository.save(token);
        resetTokenRepository.consumeAllForUser(user.getId(), now);
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private static URI validateFrontendUrl(String value) {
        String url = requireText(value, "PASSWORD_RESET_FRONTEND_URL");
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("PASSWORD_RESET_FRONTEND_URL must be an absolute HTTPS URL");
        }
        return uri;
    }

    private static String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when password reset is enabled");
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static BusinessException invalidToken() {
        return new BusinessException("Token de redefinição inválido ou expirado.", HttpStatus.BAD_REQUEST);
    }
}
