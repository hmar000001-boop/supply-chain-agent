package dev.supplychain.agent.infrastructure.postgres.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Objects;

/** Production HikariCP DataSource factory with explicit finite pool and acquisition bounds. */
public final class PostgresPooledDataSourceFactory {
    private PostgresPooledDataSourceFactory(){ }
    public static HikariDataSource create(PostgresDatabaseConfig database,PostgresPoolSettings pool){
        Objects.requireNonNull(database,"database");Objects.requireNonNull(pool,"pool");
        HikariConfig config=new HikariConfig();
        config.setPoolName("supply-chain-agent-postgres");
        config.setJdbcUrl("jdbc:postgresql://"+database.host()+":"+database.port()+"/"+database.database());
        config.setUsername(database.username());config.setPassword(database.password());
        config.setMaximumPoolSize(pool.maximumPoolSize());config.setMinimumIdle(pool.minimumIdle());
        config.setConnectionTimeout(pool.connectionTimeoutMillis());config.setValidationTimeout(pool.validationTimeoutMillis());
        config.setIdleTimeout(pool.idleTimeoutMillis());config.setMaxLifetime(pool.maxLifetimeMillis());
        config.setLeakDetectionThreshold(pool.leakDetectionThresholdMillis());
        config.setInitializationFailTimeout(1);
        config.addDataSourceProperty("sslmode",switch(database.sslMode()){
            case DISABLE->"disable";case PREFER->"prefer";case REQUIRE->"require";case VERIFY_CA->"verify-ca";case VERIFY_FULL->"verify-full";});
        config.addDataSourceProperty("connectTimeout",database.connectTimeoutSeconds());
        config.addDataSourceProperty("socketTimeout",database.socketTimeoutSeconds());
        config.addDataSourceProperty("ApplicationName",database.applicationName());
        return new HikariDataSource(config);
    }
}
