package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Lease/fencing-protected B2 step receipts. Child investigation is a read-only operation; an expired
 * uncommitted claim can therefore be retried with a higher fencing token. A completed receipt is never replayed. */
public final class PostgresB2ChildStepReceiptStore implements ChildInvestigationStepStore {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<List<ResolutionFinding>> findingsCodec;
    private final Duration leaseDuration;

    public PostgresB2ChildStepReceiptStore(DataSource dataSource, String scopeId,
                                           JsonCodec<List<ResolutionFinding>> findingsCodec, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId); this.findingsCodec = Objects.requireNonNull(findingsCodec, "findingsCodec");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isNegative() || leaseDuration.isZero()) throw new IllegalArgumentException("leaseDuration must be positive");
    }

    @Override public StepClaim claim(String caseId, String stepId, String inputFingerprint, Instant at) {
        String caseKey=required(caseId), stepKey=required(stepId), fingerprint=required(inputFingerprint);
        Objects.requireNonNull(at,"at"); String owner=UUID.randomUUID().toString();
        return transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO b2_child_step_receipt(scope_id,case_id,step_id,input_fingerprint,state,lease_owner,lease_until,
                        fencing_token,created_at,updated_at)
                    VALUES(?,?,?,?,'CLAIMED',?,clock_timestamp() + (? * interval '1 millisecond'),1,?,?) ON CONFLICT(scope_id,case_id,step_id) DO NOTHING
                    """)) {
                insert.setString(1,scopeId); insert.setString(2,caseKey); insert.setString(3,stepKey); insert.setString(4,fingerprint);
                insert.setString(5,owner); insert.setLong(6,leaseDuration.toMillis()); PostgresJdbc.setInstant(insert, 7, at); PostgresJdbc.setInstant(insert, 8, at);
                if (insert.executeUpdate()==1) return new StepClaim(Status.ACQUIRED,1,List.of(),"");
            }
            try (PreparedStatement select=connection.prepareStatement("""
                    SELECT input_fingerprint,state,lease_until,fencing_token,result_payload::text,failure_code,
                           (lease_until > clock_timestamp()) AS lease_live
                    FROM b2_child_step_receipt WHERE scope_id=? AND case_id=? AND step_id=? FOR UPDATE
                    """)) {
                select.setString(1,scopeId); select.setString(2,caseKey); select.setString(3,stepKey);
                try (ResultSet row=select.executeQuery()) {
                    if(!row.next()) throw new IllegalStateException("B2 step claim row disappeared");
                    if(!fingerprint.equals(row.getString(1).trim())) throw new SecurityException("B2 step identity was reused for different inputs");
                    String state=row.getString(2); long token=row.getLong(4); boolean leaseLive=row.getBoolean(7);
                    if("COMPLETED".equals(state)) return new StepClaim(Status.COMPLETED,0,findingsCodec.decode(row.getString(5)),"");
                    if("UNKNOWN_OUTCOME".equals(state)) return new StepClaim(Status.UNKNOWN_OUTCOME,0,List.of(),"previous child read outcome must be reconciled");
                    if("CLAIMED".equals(state) && leaseLive) {
                        return new StepClaim(Status.IN_PROGRESS,0,List.of(),"another worker owns the live child-step lease");
                    }
                    if("CLAIMED".equals(state)) {
                        try (PreparedStatement expire=connection.prepareStatement("""
                                UPDATE b2_child_step_receipt SET state='UNKNOWN_OUTCOME',lease_owner=NULL,lease_until=NULL,
                                    failure_code='LEASE_EXPIRED_REQUIRES_RECONCILIATION',updated_at=clock_timestamp()
                                WHERE scope_id=? AND case_id=? AND step_id=? AND state='CLAIMED' AND fencing_token=?
                                """)) {
                            expire.setString(1,scopeId); expire.setString(2,caseKey); expire.setString(3,stepKey); expire.setLong(4,token);
                            if (expire.executeUpdate()!=1) throw new IllegalStateException("expired B2 step lease changed during reconciliation transition");
                        }
                        return new StepClaim(Status.UNKNOWN_OUTCOME,0,List.of(),"child-step lease expired before a durable receipt; reconcile before resuming");
                    }
                    // Only a definitively failed read step can be retried; the fencing token changes.
                    long next=token+1;
                    try (PreparedStatement retry=connection.prepareStatement("""
                            UPDATE b2_child_step_receipt SET state='CLAIMED',lease_owner=?,lease_until=clock_timestamp() + (? * interval '1 millisecond'),fencing_token=?,
                                failure_code=NULL,updated_at=? WHERE scope_id=? AND case_id=? AND step_id=? AND state='FAILED' AND fencing_token=?
                            """)) {
                        retry.setString(1,owner); retry.setLong(2,leaseDuration.toMillis()); retry.setLong(3,next); PostgresJdbc.setInstant(retry, 4, at);
                        retry.setString(5,scopeId); retry.setString(6,caseKey); retry.setString(7,stepKey); retry.setLong(8,token);
                        if(retry.executeUpdate()!=1) throw new IllegalStateException("B2 failed-step retry lost a fencing race");
                    }
                    return new StepClaim(Status.ACQUIRED,next,List.of(),"");
                }
            }
        });
    }

    @Override public void complete(String caseId, String stepId, long fencingToken,
                                   List<ResolutionFinding> findings, Instant at) {
        String json=PostgresJsonArtifactRepository.validJson(findingsCodec.encode(List.copyOf(findings)));
        String fingerprint=PostgresJsonArtifactRepository.sha256(json);
        transaction(connection -> {
            try(PreparedStatement update=connection.prepareStatement("""
                    UPDATE b2_child_step_receipt SET state='COMPLETED',result_payload=CAST(? AS jsonb),result_fingerprint=?,
                        lease_owner=NULL,lease_until=NULL,updated_at=? WHERE scope_id=? AND case_id=? AND step_id=? AND state='CLAIMED'
                        AND fencing_token=? AND lease_until>clock_timestamp()
                    """)) {
                update.setString(1,json); update.setString(2,fingerprint); PostgresJdbc.setInstant(update, 3, at); update.setString(4,scopeId);
                update.setString(5,required(caseId)); update.setString(6,required(stepId)); update.setLong(7,fencingToken);
                if(update.executeUpdate()!=1) throw new SecurityException("B2 step completion rejected: expired or superseded claim");
            }
            return null;
        });
    }

    @Override public void fail(String caseId, String stepId, long fencingToken, String reason, boolean outcomeUnknown, Instant at) {
        String code=outcomeUnknown?"UNKNOWN_OUTCOME":required(reason);
        transaction(connection -> {
            try(PreparedStatement update=connection.prepareStatement("""
                    UPDATE b2_child_step_receipt SET state=?,failure_code=?,lease_owner=NULL,lease_until=NULL,updated_at=?
                    WHERE scope_id=? AND case_id=? AND step_id=? AND state='CLAIMED' AND fencing_token=? AND lease_until>clock_timestamp()
                    """)) {
                update.setString(1,outcomeUnknown?"UNKNOWN_OUTCOME":"FAILED"); update.setString(2,code); PostgresJdbc.setInstant(update, 3, at);
                update.setString(4,scopeId); update.setString(5,required(caseId)); update.setString(6,required(stepId)); update.setLong(7,fencingToken);
                if(update.executeUpdate()!=1) throw new SecurityException("B2 step failure rejected: claim is no longer owned");
            }
            return null;
        });
    }

    /** Record a completed step after an operator/reconciler has independently recovered its exact result. */
    public void reconcileUnknownAsCompleted(String caseId, String stepId, List<ResolutionFinding> findings, Instant at) {
        String json=PostgresJsonArtifactRepository.validJson(findingsCodec.encode(List.copyOf(findings)));
        transaction(connection -> {
            try (PreparedStatement update=connection.prepareStatement("""
                    UPDATE b2_child_step_receipt SET state='COMPLETED',result_payload=CAST(? AS jsonb),result_fingerprint=?,
                        failure_code=NULL,updated_at=? WHERE scope_id=? AND case_id=? AND step_id=? AND state='UNKNOWN_OUTCOME'
                    """)) {
                update.setString(1,json); update.setString(2,PostgresJsonArtifactRepository.sha256(json)); PostgresJdbc.setInstant(update, 3, at);
                update.setString(4,scopeId); update.setString(5,required(caseId)); update.setString(6,required(stepId));
                if(update.executeUpdate()!=1) throw new IllegalStateException("B2 step is not awaiting unknown-outcome reconciliation");
            }
            return null;
        });
    }

    /** Permit retry only after an authoritative reconciliation proves that the step did not complete. */
    public void reconcileUnknownAsNotApplied(String caseId, String stepId, String evidenceCode, Instant at) {
        transaction(connection -> {
            try (PreparedStatement update=connection.prepareStatement("""
                    UPDATE b2_child_step_receipt SET state='FAILED',failure_code=?,updated_at=?
                    WHERE scope_id=? AND case_id=? AND step_id=? AND state='UNKNOWN_OUTCOME'
                    """)) {
                update.setString(1,required(evidenceCode)); PostgresJdbc.setInstant(update, 2, at); update.setString(3,scopeId);
                update.setString(4,required(caseId)); update.setString(5,required(stepId));
                if(update.executeUpdate()!=1) throw new IllegalStateException("B2 step is not awaiting unknown-outcome reconciliation");
            }
            return null;
        });
    }

    private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work){
        try(Connection c=dataSource.getConnection()) { c.setAutoCommit(false); try{T v=work.run(c);c.commit();return v;}
            catch(SQLException|RuntimeException ex){try{c.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}if(ex instanceof SQLException sql)throw sql;throw ex;}}
        catch(SQLException ex){throw new PostgresPersistenceException("PostgreSQL B2 step-receipt transaction failed",ex);}
    }
    private static String required(String v){String s=Objects.requireNonNull(v,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
}
