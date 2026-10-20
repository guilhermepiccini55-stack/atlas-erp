package com.atlas.erp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

class JwtCredentialVersionTest {

    private static final String SECRET = "a-local-test-secret-with-more-than-32-characters";
    private final JwtService jwtService = new JwtService(SECRET, 15);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatedAccessTokenCarriesCurrentCredentialVersion() {
        User user = user();
        user.incrementCredentialVersion();

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractCredentialVersion(token)).isEqualTo(1);
    }

    @Test
    void tokenWithoutCredentialVersionIsRejected() {
        assertThatThrownBy(() -> jwtService.extractCredentialVersion(token(null)))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void filterRejectsMissingAndOutdatedCredentialVersions() throws Exception {
        User currentUser = user();
        currentUser.incrementCredentialVersion();
        AtlasUserDetails currentDetails = new AtlasUserDetails(currentUser);
        UserDetailsService userDetailsService = org.mockito.Mockito.mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(currentUser.getEmail())).thenReturn(currentDetails);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        assertUnauthenticated(filter, token(null));
        assertUnauthenticated(filter, token(0L));
        verify(userDetailsService, org.mockito.Mockito.times(1)).loadUserByUsername(currentUser.getEmail());
    }

    @Test
    void filterAcceptsTokenWithCurrentCredentialVersion() throws Exception {
        User currentUser = user();
        currentUser.incrementCredentialVersion();
        AtlasUserDetails currentDetails = new AtlasUserDetails(currentUser);
        UserDetailsService userDetailsService = org.mockito.Mockito.mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(currentUser.getEmail())).thenReturn(currentDetails);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        MockHttpServletRequest request = request(token(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(AtlasUserDetails.class);
    }

    private static void assertUnauthenticated(JwtAuthenticationFilter filter, String token) throws Exception {
        MockHttpServletRequest request = request(token);
        filter.doFilter(request, new MockHttpServletResponse(), new org.springframework.mock.web.MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String token(Long version) {
        var builder = Jwts.builder()
                .subject("jwt-test@example.test")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)));
        if (version != null) {
            builder.claim("credential_version", version);
        }
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static User user() {
        User user = new User("JWT Test", "jwt-test@example.test", "$2a$10$hash", Role.EMPLOYEE, UserStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 90L);
        return user;
    }
}
