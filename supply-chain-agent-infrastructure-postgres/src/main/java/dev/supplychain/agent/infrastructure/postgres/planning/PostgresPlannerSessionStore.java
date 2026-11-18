package dev.supplychain.agent.infrastructure.postgres.planning;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.session.PlannerCheckpointConflictException;
import dev.supplychain.agent.planning.session.PlannerNodeCheckpoint;
import dev.supplychain.agent.planning.session.PlannerPathCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStatus;
import dev.supplychain.agent.planning.session.PlannerSessionStore;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL-backed, revision-checked persistence for ToT/MCTS resumable search state. */
public final class PostgresPlannerSessionStore implements PlannerSessionStore {
    private final DataSource dataSource;

    public PostgresPlannerSessionStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override public Optional<PlannerSessionCheckpoint> find(String sessionId) {
        String id = required(sessionId, "sessionId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            PlannerSessionCheckpoint header;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM planner_session WHERE session_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    header = mapHeader(rows);
                }
            }
            Optional<PlannerSessionCheckpoint> result = Optional.of(new PlannerSessionCheckpoint(header.sessionId(), header.plannerId(),
                    header.requestFingerprint(), header.algorithmConfigurationFingerprint(), header.revision(), header.status(),
                    loadFrontier(connection, id), loadNodes(connection, id), header.expansions(), header.evaluations(),
                    header.modelCalls(), header.rollouts(), header.prunedBranches(), header.backtracks(),
                    header.cumulativeElapsed(), header.updatedAt()));
            connection.commit();
            return result;
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("Could not load planner session " + id, ex);
        }
    }

    @Override public PlannerSessionCheckpoint save(PlannerSessionCheckpoint checkpoint, long expectedRevision) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (expectedRevision < 0 || checkpoint.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("planner session revision must be expectedRevision + 1");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long actual = currentRevision(connection, checkpoint.sessionId());
                if (actual != expectedRevision) {
                    throw new PlannerCheckpointConflictException("planner session revision conflict for "
                            + checkpoint.sessionId() + ": expected " + expectedRevision + ", actual " + actual);
                }
                if (actual == 0) insertHeader(connection, checkpoint);
                else updateHeader(connection, checkpoint, expectedRevision);
                replaceChildren(connection, checkpoint);
                connection.commit();
                return checkpoint;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                if (ex instanceof SQLException sql) throw translate(sql);
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw translate(ex);
        }
    }

    private static long currentRevision(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT revision FROM planner_session WHERE session_id = ? FOR UPDATE")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    private static void insertHeader(Connection connection, PlannerSessionCheckpoint value) throws SQLException {
        String sql = "INSERT INTO planner_session(session_id,planner_id,request_fingerprint,algorithm_configuration_fingerprint,revision,status,"
                + "expansions,evaluations,model_calls,rollouts,pruned_branches,backtracks,cumulative_elapsed,updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindHeader(statement, value); statement.executeUpdate();
        }
    }

    private static void updateHeader(Connection connection, PlannerSessionCheckpoint value, long expected) throws SQLException {
        String sql = "UPDATE planner_session SET planner_id=?,request_fingerprint=?,algorithm_configuration_fingerprint=?,revision=?,status=?,"
                + "expansions=?,evaluations=?,model_calls=?,rollouts=?,pruned_branches=?,backtracks=?,cumulative_elapsed=?,updated_at=? "
                + "WHERE session_id=? AND revision=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.plannerId().name()); statement.setString(2, value.requestFingerprint());
            statement.setString(3, value.algorithmConfigurationFingerprint()); statement.setLong(4, value.revision());
            statement.setString(5, value.status().name()); statement.setLong(6, value.expansions());
            statement.setLong(7, value.evaluations()); statement.setLong(8, value.modelCalls());
            statement.setLong(9, value.rollouts()); statement.setLong(10, value.prunedBranches());
            statement.setLong(11, value.backtracks()); statement.setString(12, value.cumulativeElapsed().toString());
            statement.setTimestamp(13, Timestamp.from(value.updatedAt())); statement.setString(14, value.sessionId());
            statement.setLong(15, expected);
            if (statement.executeUpdate() != 1) throw new PlannerCheckpointConflictException("planner session revision changed during update");
        }
    }

    private static void bindHeader(PreparedStatement statement, PlannerSessionCheckpoint value) throws SQLException {
        statement.setString(1, value.sessionId()); statement.setString(2, value.plannerId().name());
        statement.setString(3, value.requestFingerprint()); statement.setString(4, value.algorithmConfigurationFingerprint());
        statement.setLong(5, value.revision()); statement.setString(6, value.status().name());
        statement.setLong(7, value.expansions()); statement.setLong(8, value.evaluations());
        statement.setLong(9, value.modelCalls()); statement.setLong(10, value.rollouts());
        statement.setLong(11, value.prunedBranches()); statement.setLong(12, value.backtracks());
        statement.setString(13, value.cumulativeElapsed().toString()); statement.setTimestamp(14, Timestamp.from(value.updatedAt()));
    }

    private static void replaceChildren(Connection connection, PlannerSessionCheckpoint checkpoint) throws SQLException {
        try (PreparedStatement deleteFrontier = connection.prepareStatement("DELETE FROM planner_session_frontier WHERE session_id = ?");
             PreparedStatement deleteNodes = connection.prepareStatement("DELETE FROM planner_session_tree_node WHERE session_id = ?")) {
            deleteFrontier.setString(1, checkpoint.sessionId()); deleteFrontier.executeUpdate();
            deleteNodes.setString(1, checkpoint.sessionId()); deleteNodes.executeUpdate();
        }
        String frontierSql = "INSERT INTO planner_session_frontier(session_id,ordinal,node_id,action_ids,score) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(frontierSql)) {
            int ordinal = 0;
            for (PlannerPathCheckpoint path : checkpoint.frontier()) {
                statement.setString(1, checkpoint.sessionId()); statement.setInt(2, ordinal++);
                statement.setString(3, path.nodeId()); statement.setArray(4, textArray(connection, path.actionIds()));
                statement.setDouble(5, path.score()); statement.addBatch();
            }
            statement.executeBatch();
        }
        String nodeSql = "INSERT INTO planner_session_tree_node(session_id,ordinal,node_id,parent_node_id,action_ids,visits,total_reward,expanded_action_ids,fully_expanded) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(nodeSql)) {
            int ordinal = 0;
            for (PlannerNodeCheckpoint node : checkpoint.treeNodes()) {
                statement.setString(1, checkpoint.sessionId()); statement.setInt(2, ordinal++); statement.setString(3, node.nodeId());
                statement.setString(4, node.parentNodeId()); statement.setArray(5, textArray(connection, node.actionIds()));
                statement.setLong(6, node.visits()); statement.setDouble(7, node.totalReward());
                statement.setArray(8, textArray(connection, node.expandedActionIds()));
                statement.setBoolean(9, node.fullyExpanded()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Array textArray(Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray(String[]::new));
    }

    private static List<PlannerPathCheckpoint> loadFrontier(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT node_id,action_ids,score FROM planner_session_frontier WHERE session_id = ? ORDER BY ordinal")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                List<PlannerPathCheckpoint> values = new ArrayList<>();
                while (rows.next()) values.add(new PlannerPathCheckpoint(rows.getString(1), stringList(rows.getArray(2)), rows.getDouble(3)));
                return List.copyOf(values);
            }
        }
    }

    private static List<PlannerNodeCheckpoint> loadNodes(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT node_id,parent_node_id,action_ids,visits,total_reward,expanded_action_ids,fully_expanded "
                + "FROM planner_session_tree_node WHERE session_id = ? ORDER BY ordinal")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                List<PlannerNodeCheckpoint> values = new ArrayList<>();
                while (rows.next()) values.add(new PlannerNodeCheckpoint(rows.getString(1), rows.getString(2),
                        stringList(rows.getArray(3)), rows.getLong(4), rows.getDouble(5),
                        stringList(rows.getArray(6)), rows.getBoolean(7)));
                return List.copyOf(values);
            }
        }
    }

    private static List<String> stringList(Array array) throws SQLException {
        if (array == null) return List.of();
        try {
            Object raw = array.getArray();
            if (!(raw instanceof Object[] values)) throw new SQLException("PostgreSQL text[] returned an unexpected representation");
            List<String> result = new ArrayList<>(values.length);
            for (Object value : values) result.add(Objects.toString(value));
            return List.copyOf(result);
        } finally { array.free(); }
    }

    private static PlannerSessionCheckpoint mapHeader(ResultSet rows) throws SQLException {
        return new PlannerSessionCheckpoint(rows.getString("session_id"), PlannerId.valueOf(rows.getString("planner_id")),
                rows.getString("request_fingerprint").trim(), rows.getString("algorithm_configuration_fingerprint").trim(),
                rows.getLong("revision"), PlannerSessionStatus.valueOf(rows.getString("status")), List.of(), List.of(),
                rows.getLong("expansions"), rows.getLong("evaluations"), rows.getLong("model_calls"),
                rows.getLong("rollouts"), rows.getLong("pruned_branches"), rows.getLong("backtracks"),
                Duration.parse(rows.getString("cumulative_elapsed")), rows.getTimestamp("updated_at").toInstant());
    }

    private static RuntimeException translate(SQLException ex) {
        if ("23505".equals(ex.getSQLState())) return new PlannerCheckpointConflictException("planner session already exists or contains duplicate checkpoint state");
        return new PostgresPersistenceException("PostgreSQL planner-session operation failed", ex);
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
