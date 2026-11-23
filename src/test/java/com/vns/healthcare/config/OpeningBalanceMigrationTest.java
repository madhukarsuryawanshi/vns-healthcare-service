package com.vns.healthcare.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OpeningBalanceMigrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void run_executesIdempotentAlter() {
        OpeningBalanceMigration migration = new OpeningBalanceMigration(jdbcTemplate);

        migration.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(OpeningBalanceMigration.ADD_OPENING_BALANCE_SAVED);
        verify(jdbcTemplate).execute(OpeningBalanceMigration.BACKFILL_OPENING_BALANCE_SAVED);
        verify(jdbcTemplate).execute(OpeningBalanceMigration.LOCK_OPENING_BALANCE_SAVED);
    }

    @Test
    void run_catchesExceptionAndContinues() {
        doThrow(new RuntimeException("table unavailable"))
                .when(jdbcTemplate)
                .execute(OpeningBalanceMigration.ADD_OPENING_BALANCE_SAVED);

        OpeningBalanceMigration migration = new OpeningBalanceMigration(jdbcTemplate);

        assertDoesNotThrow(() -> migration.run(new DefaultApplicationArguments(new String[0])));
    }
}
