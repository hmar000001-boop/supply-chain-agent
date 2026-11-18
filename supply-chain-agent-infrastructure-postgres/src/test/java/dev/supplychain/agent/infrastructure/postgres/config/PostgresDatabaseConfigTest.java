package dev.supplychain.agent.infrastructure.postgres.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PostgresDatabaseConfigTest {
    @Test void readsDeploymentSettingsAndNeverPrintsPassword() {
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(Map.of(
                "PGHOST", "db.internal", "PGPORT", "5433", "PGDATABASE", "supply_chain_agent",
                "PGUSER", "agent", "PGPASSWORD", "a-sensitive-password", "PGSSLMODE", "verify-full"));
        assertEquals("db.internal", config.host());
        assertEquals(5433, config.port());
        assertEquals(PostgresDatabaseConfig.SslMode.VERIFY_FULL, config.sslMode());
        assertFalse(config.toString().contains("a-sensitive-password"));
        assertTrue(config.toString().contains("<redacted>"));
    }

    @Test void rejectsMalformedPortsAndSslModes() {
        assertThrows(IllegalArgumentException.class, () -> PostgresDatabaseConfig.fromEnvironment(
                Map.of("PGPORT", "not-a-port")));
        assertThrows(IllegalArgumentException.class, () -> PostgresDatabaseConfig.fromEnvironment(
                Map.of("PGSSLMODE", "trust-everything")));
    }
}
