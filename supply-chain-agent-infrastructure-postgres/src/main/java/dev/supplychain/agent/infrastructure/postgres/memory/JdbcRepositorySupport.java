package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

abstract class JdbcRepositorySupport {
    @FunctionalInterface
    protected interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    protected final DataSource dataSource;

    JdbcRepositorySupport(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); }

    protected <T> T read(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL repository read failed", ex);
        }
    }

    protected <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                if (ex instanceof SQLException sql) throw translate(sql);
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw translate(ex);
        }
    }

    protected RuntimeException translate(SQLException ex) {
        return new PostgresPersistenceException("PostgreSQL repository operation failed", ex);
    }

    protected static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    protected static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    protected static String optional(String value) { return value == null || value.isBlank() ? null : value; }
}
