package com.vns.healthcare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OpeningBalanceMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpeningBalanceMigration.class);

    static final String ADD_OPENING_BALANCE_SAVED =
            "ALTER TABLE business_bank_statement_tables "
                    + "ADD COLUMN IF NOT EXISTS opening_balance_saved BOOLEAN DEFAULT FALSE";

    static final String BACKFILL_OPENING_BALANCE_SAVED =
            "UPDATE business_bank_statement_tables "
                    + "SET opening_balance_saved = FALSE "
                    + "WHERE opening_balance_saved IS NULL";

    static final String LOCK_OPENING_BALANCE_SAVED =
            "ALTER TABLE business_bank_statement_tables "
                    + "ALTER COLUMN opening_balance_saved SET DEFAULT FALSE, "
                    + "ALTER COLUMN opening_balance_saved SET NOT NULL";

    private final JdbcTemplate jdbcTemplate;

    public OpeningBalanceMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(ADD_OPENING_BALANCE_SAVED);
            jdbcTemplate.execute(BACKFILL_OPENING_BALANCE_SAVED);
            jdbcTemplate.execute(LOCK_OPENING_BALANCE_SAVED);
            log.info("Ensured business_bank_statement_tables.opening_balance_saved exists and is backfilled");
        } catch (Exception ex) {
            log.warn("Could not add opening_balance_saved column; startup will continue: {}", ex.getMessage(), ex);
        }
    }
}
