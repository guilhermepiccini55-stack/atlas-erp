package com.atlas.erp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.users.CredentialState;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private JavaMailSender mailSender;

    private BCryptPasswordEncoder passwordEncoder;
    private PasswordResetService passwordResetService;
    private User user;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordResetService = new PasswordResetService(
                userRepository,
                refreshTokenRepository,
                resetTokenRepository,
                passwordEncoder,
                mailSender,
                "https://atlas.example/reset-password",
                "no-reply@atlas.example",
                15,
                3,
                "smtp.atlas.example");
        user = new User("Legacy User", "legacy@example.test", "unverified-legacy-value", Role.GUEST, UserStatus.ACTIVE);
        user.setCredentialState(CredentialState.LEGACY_UNVERIFIED);
        ReflectionTestUtils.setField(user, "id", 31L);
    }

    @Test
    void requestResetStoresOnlyTokenHashAndSendsGenericResetLink() {
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(resetTokenRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(0L);

        passwordResetService.requestReset(user.getEmail());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(tokenCaptor.capture());
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        String rawToken = messageCaptor.getValue().getText().substring(
                messageCaptor.getValue().getText().lastIndexOf("token=") + "token=".length()).trim();
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(AuthService.hash(rawToken));
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(messageCaptor.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(user.getPasswordHash()).isEqualTo("unverified-legacy-value");
        assertThat(user.getCredentialState()).isEqualTo(CredentialState.LEGACY_UNVERIFIED);
    }

    @Test
    void requestResetForUnknownAddressDoesNotSendMailOrStoreToken() {
        when(userRepository.findByEmailIgnoreCaseForUpdate("missing@example.test")).thenReturn(Optional.empty());

        passwordResetService.requestReset("missing@example.test");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    void confirmResetSetsBcryptCredentialConsumesTokensAndRevokesSessions() {
        String rawToken = "one-time-reset-token";
        PasswordResetToken token = new PasswordResetToken(31L, AuthService.hash(rawToken), Instant.now().plusSeconds(300));
        when(resetTokenRepository.findByTokenHashForUpdate(AuthService.hash(rawToken))).thenReturn(Optional.of(token));
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));

        passwordResetService.confirmReset(rawToken, "a-new-local-test-password");

        assertThat(user.getCredentialState()).isEqualTo(CredentialState.BCRYPT);
        assertThat(user.getCredentialVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches("a-new-local-test-password", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("unverified-legacy-value");
        verify(userRepository).save(user);
        verify(resetTokenRepository).consumeAllForUser(31L, token.getConsumedAt());
        verify(refreshTokenRepository).revokeAllByUserId(31L);
    }

    @Test
    void confirmResetRejectsExpiredOrConsumedTokens() {
        String rawToken = "expired-reset-token";
        PasswordResetToken token = new PasswordResetToken(31L, AuthService.hash(rawToken), Instant.now().minusSeconds(1));
        when(resetTokenRepository.findByTokenHashForUpdate(AuthService.hash(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "a-new-local-test-password"))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void resetServiceRequiresExternalMailAndHttpsConfiguration() {
        assertThatThrownBy(() -> new PasswordResetService(
                userRepository, refreshTokenRepository, resetTokenRepository, passwordEncoder, mailSender,
                "http://insecure.example/reset", "no-reply@atlas.example", 15, 3, "smtp.atlas.example"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new PasswordResetService(
                userRepository, refreshTokenRepository, resetTokenRepository, passwordEncoder, mailSender,
                "https://atlas.example/reset", "no-reply@atlas.example", 15, 3, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_HOST");
    }
}
