package com.ledgerflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/payment_engine?options=-c%20TimeZone=UTC",
        "spring.datasource.username=payment_user",
        "spring.datasource.password=payment_secret",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false"
})
class FlywayMigrationPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway V1 migration successfully creates all domain tables in PostgreSQL")
    void testFlywayMigrationCreatesAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );

        assertTrue(tables.contains("merchants"), "merchants table missing");
        assertTrue(tables.contains("api_keys"), "api_keys table missing");
        assertTrue(tables.contains("accounts"), "accounts table missing");
        assertTrue(tables.contains("wallets"), "wallets table missing");
        assertTrue(tables.contains("journal_entries"), "journal_entries table missing");
        assertTrue(tables.contains("journal_lines"), "journal_lines table missing");
        assertTrue(tables.contains("payments"), "payments table missing");
        assertTrue(tables.contains("refunds"), "refunds table missing");
        assertTrue(tables.contains("idempotency_records"), "idempotency_records table missing");
        assertTrue(tables.contains("outbox_events"), "outbox_events table missing");
        assertTrue(tables.contains("consumed_events"), "consumed_events table missing");
        assertTrue(tables.contains("webhook_subscriptions"), "webhook_subscriptions table missing");
        assertTrue(tables.contains("webhook_deliveries"), "webhook_deliveries table missing");
        assertTrue(tables.contains("reconciliation_runs"), "reconciliation_runs table missing");
        assertTrue(tables.contains("reconciliation_discrepancies"), "reconciliation_discrepancies table missing");
    }
}
