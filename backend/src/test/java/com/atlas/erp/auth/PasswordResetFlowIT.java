package com.atlas.erp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.erp.support.PostgresTestContainer;
import com.atlas.erp.users.CredentialState;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.password-reset.enabled=true",
        "app.password-reset.frontend-url=https://atlas.example/reset-password",
        "app.password-reset.from=no-reply@atlas.example",
        "spring.mail.host=localhost"
})
class PasswordResetFlowIT extends PostgresTestContainer {

    private static final Pattern RESET_TOKEN = Pattern.compile("[?&]token=([^\\s]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockBean
    private JavaMailSender mailSender;

    @Test
    void emailResetChangesLegacyCredentialOnceAndRevokesExistingSessions() throws Exception {
        String email = "legacy." + UUID.randomUUID().toString().substring(0, 8) + "@atlaserp.com";
        User legacyUser = new User(
                "Legacy User", email, "unverified-legacy-value", Role.GUEST, UserStatus.ACTIVE);
        legacyUser.setCredentialState(CredentialState.LEGACY_UNVERIFIED);
        legacyUser = userRepository.saveAndFlush(legacyUser);

        RefreshToken session = refreshTokenRepository.saveAndFlush(new RefreshToken(
                legacyUser.getId(), AuthService.hash("existing-refresh-token"), Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        Matcher tokenMatcher = RESET_TOKEN.matcher(mailCaptor.getValue().getText());
        assertThat(tokenMatcher.find()).isTrue();
        String rawToken = tokenMatcher.group(1);

        PasswordResetToken storedToken = resetTokenRepository.findByTokenHash(AuthService.hash(rawToken))
                .orElseThrow();
        assertThat(storedToken.getTokenHash()).isNotEqualTo(rawToken);

        String newPassword = "new-local-test-password";
        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", newPassword))))
                .andExpect(status().isNoContent());

        User updated = userRepository.findById(legacyUser.getId()).orElseThrow();
        assertThat(updated.getCredentialState()).isEqualTo(CredentialState.BCRYPT);
        assertThat(updated.getCredentialVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches(newPassword, updated.getPasswordHash())).isTrue();
        assertThat(refreshTokenRepository.findById(session.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(resetTokenRepository.findByTokenHash(AuthService.hash(rawToken))
                .orElseThrow().isConsumed()).isTrue();

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", newPassword))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetRequestDoesNotRevealWhetherAnEmailExists() throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "missing@example.test"))))
                .andExpect(status().isAccepted());
    }
}
