package com.atlas.erp.bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapLock {

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?, ?)";
    private static final int LOCK_NAMESPACE = 0x41544C53;
    private static final int LOCK_ID = 0x42535450;

    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire() {
        jdbcTemplate.execute(LOCK_SQL, (PreparedStatementCallback<Void>) statement -> {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_ID);
            statement.execute();
            return null;
        });
    }
}
