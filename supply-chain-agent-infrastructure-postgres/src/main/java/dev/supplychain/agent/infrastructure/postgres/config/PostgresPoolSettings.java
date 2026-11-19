package dev.supplychain.agent.infrastructure.postgres.config;

/** Bounded JDBC pool parameters; values are validated before HikariCP is constructed. */
public record PostgresPoolSettings(int maximumPoolSize,int minimumIdle,long connectionTimeoutMillis,
                                   long validationTimeoutMillis,long idleTimeoutMillis,long maxLifetimeMillis,
                                   long leakDetectionThresholdMillis) {
    public PostgresPoolSettings {
        if(maximumPoolSize<1||maximumPoolSize>128)throw new IllegalArgumentException("maximumPoolSize must be in [1,128]");
        if(minimumIdle<0||minimumIdle>maximumPoolSize)throw new IllegalArgumentException("minimumIdle must be in [0,maximumPoolSize]");
        if(connectionTimeoutMillis<250||validationTimeoutMillis<250||idleTimeoutMillis<0||maxLifetimeMillis<30000||leakDetectionThresholdMillis<0)
            throw new IllegalArgumentException("invalid PostgreSQL pool timeout configuration");
        if(validationTimeoutMillis>=connectionTimeoutMillis)throw new IllegalArgumentException("validation timeout must be below acquisition timeout");
    }
    public static PostgresPoolSettings productionDefaults(){return new PostgresPoolSettings(10,2,10_000,5_000,60_000,1_800_000,0);}
}
