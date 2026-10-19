package com.atlas.erp.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Garante que migrations + seed de demonstração aplicam em um banco limpo e que o login de demo é válido. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed")
@Testcontainers
class DemoSeedIT {

    private static final String DISABLED_PASSWORD_MARKER = "!DISABLED!";

    // Container próprio (não compartilhado), para não poluir o banco dos outros testes de integração.
    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void seedCreatesAdminWithDisabledPassword() {
        User admin = userRepository.findByEmailIgnoreCase("admin@atlaserp.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isEqualTo(DISABLED_PASSWORD_MARKER);
    }

    @Test
    void seedCreatesRealisticSpreadOfUsers() {
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(60);
        assertThat(userRepository.findAll())
                .extracting(User::getPasswordHash)
                .containsOnly(DISABLED_PASSWORD_MARKER);
        var roles = userRepository.countByRole().stream().map(UserRepository.RoleCount::getRole).collect(Collectors.toSet());
        assertThat(roles).containsExactlyInAnyOrderElementsOf(Arrays.asList(Role.values()));
        assertThat(userRepository.countByStatusAndCreatedAtLessThan(UserStatus.PENDING, Instant.now())).isPositive();
    }
}
