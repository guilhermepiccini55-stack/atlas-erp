package com.atlas.erp.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class LegacySchemaMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("atlas_migration_test")
            .withUsername("atlas")
            .withPassword("atlas");

    @Test
    void incrementalMigrationsPreserveLegacyUserAndAddBigintTokenRelations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        configure(dataSource).target("1").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "INSERT INTO users (name, email, password) VALUES (?, ?, ?)",
                "Legacy User",
                "legacy@example.test",
                "legacy-test-value");
        Long originalId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "legacy@example.test");

        configure(dataSource).load().migrate();

        Map<String, Object> user = jdbc.queryForMap(
                "SELECT id, name, email, password, role, status, credential_state, credential_version "
                        + "FROM users WHERE email = ?",
                "legacy@example.test");
        assertThat(((Number) user.get("id")).longValue()).isEqualTo(originalId);
        assertThat(user.get("name")).isEqualTo("Legacy User");
        assertThat(user.get("email")).isEqualTo("legacy@example.test");
        assertThat(user.get("password")).isEqualTo("legacy-test-value");
        assertThat(user.get("role")).isEqualTo("GUEST");
        assertThat(user.get("status")).isEqualTo("ACTIVE");
        assertThat(user.get("credential_state")).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(((Number) user.get("credential_version")).longValue()).isZero();

        Integer v1Checksum = jdbc.queryForObject(
                "SELECT checksum FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class);
        assertThat(v1Checksum).isEqualTo(-830772873);

        assertThat(columnType(jdbc, "refresh_tokens", "user_id")).isEqualTo("bigint");
        assertThat(columnType(jdbc, "password_reset_tokens", "user_id")).isEqualTo("bigint");
        assertThat(columnType(jdbc, "users", "credential_version")).isEqualTo("bigint");
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'admin_password_change_audit'
                """,
                Integer.class)).isEqualTo(1);
        assertThat(columnType(jdbc, "admin_password_change_audit", "administrator_id")).isEqualTo("bigint");
        assertThat(columnType(jdbc, "admin_password_change_audit", "affected_user_id")).isEqualTo("bigint");
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'refresh_tokens'
                  AND constraint_type = 'FOREIGN KEY'
                """,
                Integer.class)).isEqualTo(1);
    }

    private static String columnType(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """,
                String.class,
                table,
                column);
    }

    private static FluentConfiguration configure(DriverManagerDataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true);
    }
}
