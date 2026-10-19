package com.atlas.erp.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.erp.auth.dto.LoginResponse;
import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.config.CorsConfig;
import com.atlas.erp.config.SecurityConfig;
import com.atlas.erp.security.CustomUserDetailsService;
import com.atlas.erp.security.JwtService;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserStatus;
import com.atlas.erp.users.dto.UserResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    private static final String LOGIN_BODY = """
            {"email": "admin@atlaserp.com", "password": "Atlas@123"}
            """;

    @Test
    void login_withValidBody_returnsTokens() throws Exception {
        UserResponse user = new UserResponse(UUID.randomUUID(), "Admin", "admin@atlaserp.com",
                Role.ADMIN, UserStatus.ACTIVE, Instant.now(), Instant.now());
        when(authService.login(any())).thenReturn(new LoginResponse("acc", "ref", "Bearer", 900, user));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@atlaserp.com"));
    }

    @Test
    void login_withInvalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_withBadCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void login_withDisabledAccount_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("disabled"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refresh_withInvalidToken_returns401() throws Exception {
        when(authService.refresh(any()))
                .thenThrow(new BusinessException("Sessão inválida ou expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "abc"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void logout_isPublicAndReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "abc"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).logout("abc");
    }
}
