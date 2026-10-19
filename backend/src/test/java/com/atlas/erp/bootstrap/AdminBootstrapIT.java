package com.atlas.erp.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.erp.support.PostgresTestContainer;
import com.atlas.erp.users.Role;
import com.atlas.erp.users.User;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifica o bootstrap em PostgreSQL real por meio do Testcontainers. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.bootstrap.admin.enabled=false")
class AdminBootstrapIT extends PostgresTestContainer {

    private static final int LOCK_NAMESPACE = 0x41544C53;
    private static final int LOCK_ID = 0x42535450;
    private static final String FAILURE_CONSTRAINT = "ck_test_reject_bootstrap_admin";

    @Autowired
    private AdminBootstrapService bootstrapService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + FAILURE_CONSTRAINT);
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + FAILURE_CONSTRAINT);
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
    }

    @Test
    void countIncludesSoftDeletedUsersAndSkipsBootstrap() {
        User user = userRepository.saveAndFlush(new User(
                "Existing User", "existing@example.test", "encoded-test-password", Role.ADMIN, UserStatus.ACTIVE));
        userRepository.delete(user);
        userRepository.flush();

        assertThat(userRepository.countAllIncludingDeleted()).isEqualTo(1L);
        assertThat(bootstrapService.createFirstAdmin(
                "Initial Admin", "admin@example.test", "LongTestPass123")).isFalse();
        assertThat(userRepository.countAllIncludingDeleted()).isEqualTo(1L);
    }

    @Test
    void failedInsertRollsBackTheBootstrapTransaction() {
        jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT " + FAILURE_CONSTRAINT
                + " CHECK (email <> 'bootstrap-failure@example.test')");

        assertThatThrownBy(() -> bootstrapService.createFirstAdmin(
                "Initial Admin", "bootstrap-failure@example.test", "LongTestPass123"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .satisfies(exception -> {
                    ConstraintViolationException cause = (ConstraintViolationException) exception.getCause();
                    assertThat(cause.getConstraintName()).isEqualTo(FAILURE_CONSTRAINT);
                });

        assertThat(userRepository.countAllIncludingDeleted()).isZero();
    }

    @Test
    void missingConfigurationDoesNotCreateAdminWhenDatabaseIsEmpty() {
        assertThatThrownBy(() -> bootstrapService.createFirstAdmin(
                "", "admin@example.test", "LongTestPass123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_NAME");

        assertThat(userRepository.countAllIncludingDeleted()).isZero();
    }

    @Test
    void invalidConfigurationDoesNotCreateAdminWhenDatabaseIsEmpty() {
        assertThatThrownBy(() -> bootstrapService.createFirstAdmin(
                "Initial Admin", "not-an-email", "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("password")
                .hasMessageNotContaining("short");

        assertThat(userRepository.countAllIncludingDeleted()).isZero();
    }

    @Test
    void concurrentInitializationsCreateOnlyOneAdministrator() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> runAfterBarrier(ready, start));
            Future<Boolean> second = executor.submit(() -> runAfterBarrier(ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int createdCount = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(createdCount).isEqualTo(1);
            assertThat(userRepository.countAllIncludingDeleted()).isEqualTo(1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void advisoryLockAndUserInsertRemainInTheSameSpringTransaction() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                assertThat(bootstrapService.createFirstAdmin(
                        "Initial Admin", "admin@example.test", "LongTestPass123")).isTrue();

                Future<Boolean> lockAcquired = executor.submit(() -> jdbcTemplate.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(?, ?)",
                        Boolean.class,
                        LOCK_NAMESPACE,
                        LOCK_ID));
                Future<Long> visibleRows = executor.submit(
                        () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));

                try {
                    assertThat(lockAcquired.get(10, TimeUnit.SECONDS)).isFalse();
                    assertThat(visibleRows.get(10, TimeUnit.SECONDS)).isZero();
                } catch (Exception exception) {
                    throw new AssertionError("Could not verify bootstrap transaction isolation", exception);
                }
            });

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?, ?)",
                    Boolean.class,
                    LOCK_NAMESPACE,
                    LOCK_ID)).isTrue();
            assertThat(userRepository.countAllIncludingDeleted()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean runAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent bootstrap");
        }
        return bootstrapService.createFirstAdmin(
                "Initial Admin", "admin@example.test", "LongTestPass123");
    }
}
