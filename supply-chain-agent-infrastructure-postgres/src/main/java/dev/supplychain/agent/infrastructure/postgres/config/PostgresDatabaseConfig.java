package dev.supplychain.agent.infrastructure.postgres.config;

import java.util.Map;
import java.util.Objects;

/** Connection settings sourced from deployment configuration; secrets are deliberately excluded from toString(). */
public record PostgresDatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        SslMode sslMode,
        int connectTimeoutSeconds,
        int socketTimeoutSeconds,
        String applicationName) {

    public enum SslMode { DISABLE, PREFER, REQUIRE, VERIFY_CA, VERIFY_FULL }

    public PostgresDatabaseConfig {
        host = required(host, "host");
        database = required(database, "database");
        username = required(username, "username");
        password = Objects.requireNonNull(password, "password");
        sslMode = Objects.requireNonNull(sslMode, "sslMode");
        applicationName = required(applicationName, "applicationName");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be in [1,65535]");
        if (connectTimeoutSeconds < 1 || socketTimeoutSeconds < 1) {
            throw new IllegalArgumentException("connection and socket timeouts must be positive");
        }
    }

    public static PostgresDatabaseConfig fromEnvironment(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        return new PostgresDatabaseConfig(
                env.getOrDefault("PGHOST", "localhost"),
                integer(env, "PGPORT", 5432),
                env.getOrDefault("PGDATABASE", "supply_chain_agent"),
                env.getOrDefault("PGUSER", "postgres"),
                env.getOrDefault("PGPASSWORD", ""),
                parseSslMode(env.getOrDefault("PGSSLMODE", "prefer")),
                integer(env, "PGCONNECT_TIMEOUT", 10),
                integer(env, "PGSOCKET_TIMEOUT", 30),
                env.getOrDefault("PGAPPNAME", "supply-chain-agent"));
    }

    private static SslMode parseSslMode(String raw) {
        String normalized = Objects.requireNonNull(raw, "PGSSLMODE").trim()
                .toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        try {
            return SslMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("PGSSLMODE must be one of disable, prefer, require, verify-ca, verify-full", ex);
        }
    }

    @Override public String toString() {
        return "PostgresDatabaseConfig[host=" + host + ", port=" + port + ", database=" + database
                + ", username=" + username + ", password=<redacted>, sslMode=" + sslMode
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds + ", socketTimeoutSeconds="
                + socketTimeoutSeconds + ", applicationName=" + applicationName + "]";
    }

    private static int integer(Map<String, String> env, String key, int fallback) {
        String value = env.get(key);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be an integer", ex); }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
