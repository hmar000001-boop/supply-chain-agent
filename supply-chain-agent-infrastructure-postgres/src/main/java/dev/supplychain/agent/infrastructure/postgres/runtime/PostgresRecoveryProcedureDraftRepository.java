package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraft;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftRepository;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** Scope-bound, version-CAS repository with append-only transition history for B2 procedure drafts. */
public final class PostgresRecoveryProcedureDraftRepository implements RecoveryProcedureDraftRepository {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<RecoveryProcedureDraft> codec;

    public PostgresRecoveryProcedureDraftRepository(DataSource dataSource,String scopeId,JsonCodec<RecoveryProcedureDraft> codec) {
        this.dataSource=Objects.requireNonNull(dataSource,"dataSource"); this.scopeId=required(scopeId);
        this.codec=Objects.requireNonNull(codec,"codec");
    }

    @Override public void saveNew(RecoveryProcedureDraft draft) {
        requireScope(draft); String payload=PostgresJsonArtifactRepository.validJson(codec.encode(draft));
        transaction(c->{
            try(PreparedStatement insert=c.prepareStatement("""
                    INSERT INTO b2_recovery_procedure_draft(scope_id,draft_id,version,status,execution_id,impasse_case_id,
                        objective_fingerprint,source_fingerprint,payload,created_at,updated_at,expires_at)
                    VALUES(?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)
                    """)) {
                insert.setString(1,scopeId);insert.setString(2,draft.id());insert.setLong(3,draft.version());
                insert.setString(4,draft.status().name());insert.setString(5,draft.executionId());insert.setString(6,draft.impasseCaseId());
                insert.setString(7,draft.objectiveFingerprint());insert.setString(8,draft.sourceFingerprint());insert.setString(9,payload);
                PostgresJdbc.setInstant(insert,10,draft.createdAt());PostgresJdbc.setInstant(insert,11,draft.createdAt());PostgresJdbc.setInstant(insert,12,draft.expiresAt());
                insert.executeUpdate();
            }
            appendHistory(c,draft,payload);return null;
        });
    }

    @Override public Optional<RecoveryProcedureDraft> findById(String id) {
        try(Connection c=dataSource.getConnection();PreparedStatement q=c.prepareStatement("""
                SELECT payload::text,version,status,objective_fingerprint,source_fingerprint
                FROM b2_recovery_procedure_draft WHERE scope_id=? AND draft_id=?
                """)) {
            q.setString(1,scopeId);q.setString(2,required(id));
            try(ResultSet r=q.executeQuery()) {
                if(!r.next()) return Optional.empty();
                RecoveryProcedureDraft value=codec.decode(r.getString(1));
                if(!value.id().equals(id)||value.version()!=r.getLong(2)||!value.status().name().equals(r.getString(3))
                        ||!value.objectiveFingerprint().equals(r.getString(4))||!value.sourceFingerprint().equals(r.getString(5))
                        ||!scopeId.equals(value.scopeId().value()))
                    throw new SecurityException("B2 procedure draft payload/header consistency check failed");
                return Optional.of(value);
            }
        } catch(SQLException ex) {throw new PostgresPersistenceException("PostgreSQL B2 recovery procedure lookup failed",ex);}
    }

    @Override public boolean compareAndSet(String id,long expectedVersion,RecoveryProcedureDraft replacement) {
        requireScope(replacement);
        if(!required(id).equals(replacement.id())||replacement.version()!=expectedVersion+1)
            throw new IllegalArgumentException("B2 recovery procedure CAS identity/version is invalid");
        String payload=PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        return transaction(c->{
            int changed;
            try(PreparedStatement u=c.prepareStatement("""
                    UPDATE b2_recovery_procedure_draft SET version=?,status=?,payload=CAST(? AS jsonb),updated_at=?
                    WHERE scope_id=? AND draft_id=? AND version=? AND objective_fingerprint=? AND source_fingerprint=?
                    """)) {
                u.setLong(1,replacement.version());u.setString(2,replacement.status().name());u.setString(3,payload);
                PostgresJdbc.setInstant(u,4,replacement.updatedAt());
                u.setString(5,scopeId);u.setString(6,id);u.setLong(7,expectedVersion);
                u.setString(8,replacement.objectiveFingerprint());u.setString(9,replacement.sourceFingerprint());changed=u.executeUpdate();
            }
            if(changed==0)return false;
            appendHistory(c,replacement,payload);return true;
        });
    }

    private void requireScope(RecoveryProcedureDraft draft) {
        Objects.requireNonNull(draft,"draft");
        if(!scopeId.equals(draft.scopeId().value()))throw new SecurityException("B2 recovery procedure draft belongs to another scope");
    }
    private void appendHistory(Connection c,RecoveryProcedureDraft d,String payload)throws SQLException {
        try(PreparedStatement h=c.prepareStatement("""
                INSERT INTO b2_recovery_procedure_draft_history(scope_id,draft_id,version,status,source_fingerprint,payload,recorded_at)
                VALUES(?,?,?,?,?,CAST(? AS jsonb),?)
                """)) {
            h.setString(1,scopeId);h.setString(2,d.id());h.setLong(3,d.version());h.setString(4,d.status().name());
            h.setString(5,d.sourceFingerprint());h.setString(6,payload);
            PostgresJdbc.setInstant(h,7,d.updatedAt());h.executeUpdate();
        }
    }
    private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work){
        try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{T result=work.run(c);c.commit();return result;}
            catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}if(e instanceof SQLException sql)throw sql;throw e;}}
        catch(SQLException e){if("23505".equals(e.getSQLState()))throw new IllegalStateException("B2 recovery procedure identity/version already exists",e);
            throw new PostgresPersistenceException("PostgreSQL B2 recovery procedure transaction failed",e);}
    }
    private static String required(String value){String s=Objects.requireNonNull(value,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
}
