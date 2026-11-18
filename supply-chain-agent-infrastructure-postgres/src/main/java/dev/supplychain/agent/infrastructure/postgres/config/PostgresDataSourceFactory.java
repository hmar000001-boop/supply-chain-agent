package dev.supplychain.agent.infrastructure.postgres.config;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.Objects;

/** Creates a PostgreSQL DataSource. Production hosts may wrap it in their approved connection pool. */
public final class PostgresDataSourceFactory {
    private PostgresDataSourceFactory() { }

    public static DataSource create(PostgresDatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[]{config.host()});
        source.setPortNumbers(new int[]{config.port()});
        source.setDatabaseName(config.database());
        source.setUser(config.username());
        source.setPassword(config.password());
        source.setSslMode(switch (config.sslMode()) {
            case DISABLE -> "disable";
            case PREFER -> "prefer";
            case REQUIRE -> "require";
            case VERIFY_CA -> "verify-ca";
            case VERIFY_FULL -> "verify-full";
        });
        source.setConnectTimeout(config.connectTimeoutSeconds());
        source.setSocketTimeout(config.socketTimeoutSeconds());
        source.setApplicationName(config.applicationName());
        source.setTcpKeepAlive(true);
        return source;
    }
}
