package com.atlas.erp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.erp.support.PostgresTestContainer;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Fluxo completo (filtro JWT, @PreAuthorize, Flyway, JPA) contra um PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT extends PostgresTestContainer {

    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String createUser(Role role, UserStatus status) {
        String email = "it." + UUID.randomUUID().toString().substring(0, 8) + "@atlaserp.com";
        userRepository.saveAndFlush(new User("IT " + role, email, passwordEncoder.encode(PASSWORD), role, status));
        return email;
    }

    private JsonNode postJson(String url, Map<String, String> body, int expectedStatus, String bearer) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        String json = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    private JsonNode login(String email) throws Exception {
        return postJson("/auth/login", Map.of("email", email, "password", PASSWORD), 200, null);
    }

    @Test
    void loginRefreshRotationAndReuseDetection() throws Exception {
        String email = createUser(Role.EMPLOYEE, UserStatus.ACTIVE);

        JsonNode session = login(email);
        String access = session.get("accessToken").asText();
        String refresh = session.get("refreshToken").asText();
        assertThat(session.get("user").get("email").asText()).isEqualTo(email);
        assertThat(session.toString()).doesNotContain("passwordHash");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // EMPLOYEE não pode listar usuários
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden());

        JsonNode rotated = postJson("/auth/refresh", Map.of("refreshToken", refresh), 200, null);
        String newRefresh = rotated.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // reuso do refresh antigo: 401 e todas as sessões do usuário são revogadas
        postJson("/auth/refresh", Map.of("refreshToken", refresh), 401, null);
        postJson("/auth/refresh", Map.of("refreshToken", newRefresh), 401, null);
    }

    @Test
    void invalidCredentialsAndMissingTokenAreRejected() throws Exception {
        String email = createUser(Role.EMPLOYEE, UserStatus.ACTIVE);

        postJson("/auth/login", Map.of("email", email, "password", "wrong-password"), 401, null);
        postJson("/auth/login", Map.of("email", "ghost@atlaserp.com", "password", PASSWORD), 401, null);

        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingUserCannotLogin() throws Exception {
        String email = createUser(Role.EMPLOYEE, UserStatus.PENDING);

        postJson("/auth/login", Map.of("email", email, "password", PASSWORD), 401, null);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String email = createUser(Role.MANAGER, UserStatus.ACTIVE);
        String refresh = login(email).get("refreshToken").asText();

        postJson("/auth/logout", Map.of("refreshToken", refresh), 204, null);

        postJson("/auth/refresh", Map.of("refreshToken", refresh), 401, null);
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void adminCanManageUsersEndToEnd() throws Exception {
        String adminEmail = createUser(Role.ADMIN, UserStatus.ACTIVE);
        String token = login(adminEmail).get("accessToken").asText();
        String newEmail = "created." + UUID.randomUUID().toString().substring(0, 8) + "@atlaserp.com";
        Map<String, String> body = Map.of("name", "Criado Pelo Teste", "email", newEmail,
                "password", PASSWORD, "role", "VIEWER");

        // cria
        JsonNode created = postJson("/users", body, 201, token);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");

        // e-mail duplicado
        postJson("/users", body, 409, token);

        // lista com filtro e ordenação
        mockMvc.perform(get("/users").param("search", newEmail).param("sort", "name,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(newEmail));

        // ordenação por campo não permitido
        mockMvc.perform(get("/users").param("sort", "passwordHash,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // estatísticas (executa as agregações no banco real)
        mockMvc.perform(get("/users/stats").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers.sparkline.length()").value(6))
                .andExpect(jsonPath("$.byRole.length()").value(Role.values().length))
                .andExpect(jsonPath("$.activity.length()").value(6));

        // exclusão lógica
        mockMvc.perform(delete("/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // não pode excluir a si mesmo
        UUID selfId = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow().getId();
        mockMvc.perform(delete("/users/{id}", selfId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void userCanChangeOwnPassword() throws Exception {
        String email = createUser(Role.VIEWER, UserStatus.ACTIVE);
        String token = login(email).get("accessToken").asText();

        mockMvc.perform(put("/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "NewSecret456"))))
                .andExpect(status().isNoContent());

        postJson("/auth/login", Map.of("email", email, "password", PASSWORD), 401, null);
        postJson("/auth/login", Map.of("email", email, "password", "NewSecret456"), 200, null);
    }
}
