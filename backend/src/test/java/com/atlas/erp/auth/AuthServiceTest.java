package com.atlas.erp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.erp.auth.dto.LoginRequest;
import com.atlas.erp.auth.dto.LoginResponse;
import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.security.JwtService;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserMapper;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, userRepository, refreshTokenRepository,
                jwtService, new UserMapper(), 7);
        user = new User("Maria Teste", "maria@atlaserp.com", "hash", Role.MANAGER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void login_withValidCredentials_issuesAccessAndRefreshTokens() {
        when(userRepository.findByEmailIgnoreCase("maria@atlaserp.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);

        LoginResponse response = authService.login(new LoginRequest("maria@atlaserp.com", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("maria@atlaserp.com");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .as("o refresh token deve ser salvo apenas como hash")
                .isNotEqualTo(response.refreshToken())
                .isEqualTo(AuthService.hash(response.refreshToken()));
    }

    @Test
    void login_withBadCredentials_propagatesAndIssuesNothing() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("maria@atlaserp.com", "wrong")));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withValidToken_rotatesTokens() {
        RefreshToken stored = new RefreshToken(user.getId(), AuthService.hash("raw-token"), Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("raw-token"))).thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");

        LoginResponse response = authService.refresh("raw-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isNotBlank().isNotEqualTo("raw-token");
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void refresh_withUnknownToken_isUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("nope"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withRevokedToken_revokesAllUserSessions() {
        RefreshToken stored = new RefreshToken(user.getId(), AuthService.hash("reused"), Instant.now().plus(1, ChronoUnit.DAYS));
        stored.revoke();
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("reused"))).thenReturn(Optional.of(stored));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("reused"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    void refresh_withExpiredToken_isUnauthorized() {
        RefreshToken stored = new RefreshToken(user.getId(), AuthService.hash("old"), Instant.now().minus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("old"))).thenReturn(Optional.of(stored));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("old"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_forInactiveUser_isUnauthorized() {
        user.setStatus(UserStatus.INACTIVE);
        RefreshToken stored = new RefreshToken(user.getId(), AuthService.hash("tok"), Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("tok"))).thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("tok"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void logout_revokesTheToken() {
        RefreshToken stored = new RefreshToken(user.getId(), AuthService.hash("bye"), Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(AuthService.hash("bye"))).thenReturn(Optional.of(stored));

        authService.logout("bye");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_withUnknownToken_isIdempotent() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("unknown");

        verify(refreshTokenRepository, never()).save(any());
    }
}
