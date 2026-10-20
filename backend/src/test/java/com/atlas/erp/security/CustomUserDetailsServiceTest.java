package com.atlas.erp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.atlas.erp.users.CredentialState;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void legacyBcryptCredentialCanBeVerifiedWithoutChangingItsValue() {
        String encoded = passwordEncoder.encode("local-test-password");
        User user = legacyUser(encoded);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(java.util.Optional.of(user));

        var details = new CustomUserDetailsService(userRepository).loadUserByUsername(user.getEmail());

        assertThat(passwordEncoder.matches("local-test-password", details.getPassword())).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo(encoded);
        assertThat(user.getCredentialState()).isEqualTo(CredentialState.LEGACY_UNVERIFIED);
    }

    @Test
    void unrecognizedLegacyCredentialIsNeverComparedAsPlainText() {
        String legacyValue = "legacy-value-not-a-bcrypt-hash";
        User user = legacyUser(legacyValue);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository).loadUserByUsername(user.getEmail()))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(user.getPasswordHash()).isEqualTo(legacyValue);
        assertThat(user.getCredentialState()).isEqualTo(CredentialState.LEGACY_UNVERIFIED);
    }

    private static User legacyUser(String encoded) {
        User user = new User("Legacy User", "legacy@example.test", encoded, Role.GUEST, UserStatus.ACTIVE);
        user.setCredentialState(CredentialState.LEGACY_UNVERIFIED);
        return user;
    }
}
