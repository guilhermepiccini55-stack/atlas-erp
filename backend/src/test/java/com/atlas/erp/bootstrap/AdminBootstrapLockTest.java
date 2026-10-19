package com.atlas.erp.bootstrap;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapLockTest {

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?, ?)";
    private static final int LOCK_NAMESPACE = 0x41544C53;
    private static final int LOCK_ID = 0x42535450;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void acquire_executesTransactionScopedPostgresAdvisoryLock() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        doAnswer(invocation -> {
            PreparedStatementCallback<Void> callback = invocation.getArgument(1);
            callback.doInPreparedStatement(statement);
            return null;
        }).when(jdbcTemplate).execute(eq(LOCK_SQL), ArgumentMatchers.<PreparedStatementCallback<Void>>any());

        new AdminBootstrapLock(jdbcTemplate).acquire();

        verify(jdbcTemplate).execute(eq(LOCK_SQL), ArgumentMatchers.<PreparedStatementCallback<Void>>any());
        verify(statement).setInt(1, LOCK_NAMESPACE);
        verify(statement).setInt(2, LOCK_ID);
        verify(statement).execute();
    }
}
