package dev.supplychain.agent.infrastructure.postgres.runtime;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * JDBC 4.2 bindings used by the PostgreSQL runtime repositories.
 *
 * <p>pgJDBC does not infer a PostgreSQL type for {@link Instant}. PostgreSQL
 * {@code timestamptz} values must be bound as {@code OffsetDateTime}; UTC is
 * used as the explicit offset so the stored instant is independent of the
 * JVM and database session time zones.</p>
 */
final class PostgresJdbc {
    private PostgresJdbc() { }

    static void setInstant(PreparedStatement statement, int parameterIndex, Instant value)
            throws SQLException {
        Objects.requireNonNull(statement, "statement");
        if (value == null) {
            statement.setNull(parameterIndex, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(parameterIndex, value.atOffset(ZoneOffset.UTC));
    }
}
