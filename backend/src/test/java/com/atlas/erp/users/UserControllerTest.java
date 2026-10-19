package com.atlas.erp.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.erp.config.CorsConfig;
import com.atlas.erp.config.SecurityConfig;
import com.atlas.erp.security.CustomUserDetailsService;
import com.atlas.erp.security.JwtService;
import com.atlas.erp.users.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    private static final String VALID_BODY = """
            {"name": "Novo Usuario", "email": "novo@atlaserp.com", "password": "Secret123", "role": "EMPLOYEE"}
            """;

    private static UserResponse sample() {
        return new UserResponse(UUID.randomUUID(), "Lucas Ferreira", "lucas@atlaserp.com",
                Role.ADMIN, UserStatus.ACTIVE, Instant.now(), Instant.now());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsPage() throws Exception {
        when(userService.list(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/users").param("search", "luc").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("lucas@atlaserp.com"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_withInvalidRoleFilter_returns400() throws Exception {
        mockMvc.perform(get("/users").param("role", "SUPERUSER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201WithLocation() throws Exception {
        UserResponse created = sample();
        when(userService.create(any())).thenReturn(created);

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/users/" + created.id()))
                .andExpect(jsonPath("$.id").value(created.id().toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withInvalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "invalid", "password": "123", "role": "EMPLOYEE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns403() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/users/{id}", id))
                .andExpect(status().isNoContent());

        verify(userService).delete(id, "user");
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void me_isAvailableToAnyAuthenticatedRole() throws Exception {
        when(userService.getMe("user")).thenReturn(sample());

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lucas@atlaserp.com"));
    }
}
