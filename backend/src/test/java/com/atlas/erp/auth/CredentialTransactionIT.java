package com.atlas.erp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.erp.support.PostgresTestContainer;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserService;
import com.atlas.erp.users.UserStatus;
import com.atlas.erp.users.AdminPasswordChangeAuditRepository;
import com.atlas.erp.users.dto.AdminPasswordChangeReason;
import com.atlas.erp.users.dto.AdminPasswordChangeRequest;
import com.atlas.erp.users.dto.ChangePasswordRequest;
import java.time.Instant;
import java.util.UUID;
import com.atlas.erp.users.AdminPasswordChangeAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.password-reset.enabled=true",
        "app.password-reset.frontend-url=https://atlas.example/reset-password",
        "app.password-reset.from=no-reply@atlas.example",
        "spring.mail.host=localhost"
})
class CredentialTransactionIT extends PostgresTestContainer {

    private static final String TRIGGER_NAME = "trg_fail_test_refresh_revoke";
    private static final String FUNCTION_NAME = "fail_test_refresh_revoke";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdminPasswordChangeAuditRepository adminPasswordChangeAuditRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockBean
    private JavaMailSender mailSender;

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON refresh_tokens");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "()");
    }

    @Test
    void ownPasswordChangeRollsBackCredentialAndRefreshRevocationOnPostgresFailure() {
        String oldPassword = "Current-password-123";
        User user = createUser(Role.EMPLOYEE, oldPassword);
        RefreshToken refreshToken = createRefreshToken(user);
        String originalHash = user.getPasswordHash();
        installRefreshRevocationFailure();

        assertThatThrownBy(() -> userService.changePassword(
                user.getEmail(), new ChangePasswordRequest(oldPassword, "New-password-123")))
                .hasStackTraceContaining("intentional local integration-test failure");

        assertUserAndRefreshTokenUnchanged(user.getId(), originalHash, refreshToken.getId());
    }

    @Test
    void administrativePasswordChangeRollsBackCredentialAndRefreshRevocationOnPostgresFailure() {
        User actor = createUser(Role.ADMIN, "Admin-password-123");
        User target = createUser(Role.EMPLOYEE, "Target-password-123");
        RefreshToken refreshToken = createRefreshToken(target);
        String originalHash = target.getPasswordHash();
        installRefreshRevocationFailure();

        assertThatThrownBy(() -> userService.changePasswordAsAdmin(
                target.getId(),
                new AdminPasswordChangeRequest(
                        "Admin-replacement-123", AdminPasswordChangeReason.SECURITY_CONCERN),
                actor.getEmail()))
                .hasStackTraceContaining("intentional local integration-test failure");

        assertUserAndRefreshTokenUnchanged(target.getId(), originalHash, refreshToken.getId());
        assertThat(adminPasswordChangeAuditRepository.findAllByAffectedUserId(target.getId())).isEmpty();
    }

    @Test
    void administrativePasswordChangePersistsAuditWithoutCredentialMaterial() {
        User actor = createUser(Role.ADMIN, "Admin-password-123");
        User target = createUser(Role.EMPLOYEE, "Target-password-123");

        userService.changePasswordAsAdmin(
                target.getId(),
                new AdminPasswordChangeRequest(
                        "Admin-replacement-123", AdminPasswordChangeReason.ACCOUNT_COMPROMISED),
                actor.getEmail());

        AdminPasswordChangeAudit audit =
                adminPasswordChangeAuditRepository.findAllByAffectedUserId(target.getId()).get(0);
        assertThat(audit.getAdministratorId()).isEqualTo(actor.getId());
        assertThat(audit.getAffectedUserId()).isEqualTo(target.getId());
        assertThat(audit.getOccurredAt()).isNotNull();
        assertThat(audit.getReason()).isEqualTo(AdminPasswordChangeReason.ACCOUNT_COMPROMISED);
        assertThat(audit.getOperationType())
                .isEqualTo(AdminPasswordChangeAudit.OperationType.ADMIN_PASSWORD_CHANGE);
    }

    @Test
    void passwordResetRollsBackCredentialResetTokenAndRefreshRevocationOnPostgresFailure() {
        User user = createUser(Role.EMPLOYEE, "Legacy-password-123");
        RefreshToken refreshToken = createRefreshToken(user);
        String originalHash = user.getPasswordHash();
        String rawResetToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = resetTokenRepository.saveAndFlush(new PasswordResetToken(
                user.getId(), AuthService.hash(rawResetToken), Instant.now().plusSeconds(600)));
        installRefreshRevocationFailure();

        assertThatThrownBy(() -> passwordResetService.confirmReset(rawResetToken, "Reset-password-123"))
                .hasStackTraceContaining("intentional local integration-test failure");

        assertUserAndRefreshTokenUnchanged(user.getId(), originalHash, refreshToken.getId());
        assertThat(resetTokenRepository.findById(resetToken.getId()).orElseThrow().isConsumed()).isFalse();
    }

    private User createUser(Role role, String password) {
        String email = role.name().toLowerCase() + "." + UUID.randomUUID() + "@atlas.example";
        return userRepository.saveAndFlush(new User(
                "Transaction Test", email, passwordEncoder.encode(password), role, UserStatus.ACTIVE));
    }

    private RefreshToken createRefreshToken(User user) {
        return refreshTokenRepository.saveAndFlush(new RefreshToken(
                user.getId(), AuthService.hash(UUID.randomUUID().toString()), Instant.now().plusSeconds(3600)));
    }

    private void installRefreshRevocationFailure() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_test_refresh_revoke()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF NEW.revoked AND NOT OLD.revoked THEN
                        RAISE EXCEPTION 'intentional local integration-test failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_test_refresh_revoke
                BEFORE UPDATE ON refresh_tokens
                FOR EACH ROW
                EXECUTE FUNCTION fail_test_refresh_revoke()
                """);
    }

    private void assertUserAndRefreshTokenUnchanged(Long userId, String originalHash, UUID refreshTokenId) {
        User persistedUser = userRepository.findById(userId).orElseThrow();
        RefreshToken persistedRefreshToken = refreshTokenRepository.findById(refreshTokenId).orElseThrow();

        assertThat(persistedUser.getPasswordHash()).isEqualTo(originalHash);
        assertThat(persistedUser.getCredentialVersion()).isZero();
        assertThat(persistedRefreshToken.isRevoked()).isFalse();
    }
}
