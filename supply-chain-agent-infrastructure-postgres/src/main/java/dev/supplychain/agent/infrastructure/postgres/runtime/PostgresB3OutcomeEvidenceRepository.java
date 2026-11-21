package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceRepository;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Scope-bound append-only PostgreSQL receipts and independently verified downstream outcomes. */
public final class PostgresB3OutcomeEvidenceRepository implements B3OutcomeEvidenceRepository {
    private final DataSource dataSource; private final String scopeId;
    private final JsonCodec<WorkspaceReceiptEvidence> receiptCodec;
    private final JsonCodec<DownstreamOutcomeEvidence> outcomeCodec;
    private final JsonCodec<WorkspaceCoalitionOutcomeEvidence> coalitionOutcomeCodec;

    public PostgresB3OutcomeEvidenceRepository(DataSource dataSource,String scopeId,
            JsonCodec<WorkspaceReceiptEvidence> receiptCodec,JsonCodec<DownstreamOutcomeEvidence> outcomeCodec) {
        this(dataSource,scopeId,receiptCodec,outcomeCodec,null);
    }
    public PostgresB3OutcomeEvidenceRepository(DataSource dataSource,String scopeId,
            JsonCodec<WorkspaceReceiptEvidence> receiptCodec,JsonCodec<DownstreamOutcomeEvidence> outcomeCodec,
            JsonCodec<WorkspaceCoalitionOutcomeEvidence> coalitionOutcomeCodec) {
        this.dataSource=Objects.requireNonNull(dataSource,"dataSource");this.scopeId=required(scopeId);
        this.receiptCodec=Objects.requireNonNull(receiptCodec,"receiptCodec");this.outcomeCodec=Objects.requireNonNull(outcomeCodec,"outcomeCodec");
        this.coalitionOutcomeCodec=coalitionOutcomeCodec;
    }

    @Override public void appendReceipt(WorkspaceReceiptEvidence receipt) {
        requireScope(receipt.scopeId());String payload=PostgresJsonArtifactRepository.validJson(receiptCodec.encode(receipt));
        transaction(c->{
            try(PreparedStatement insert=c.prepareStatement("""
                    INSERT INTO b3_workspace_consumer_receipt(scope_id,receipt_id,cycle_id,workspace_id,consumer_id,status,payload,received_at)
                    VALUES(?,?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT(scope_id,receipt_id) DO NOTHING
                    """)) {
                insert.setString(1,scopeId);insert.setString(2,receipt.receiptId());insert.setString(3,receipt.cycleId());
                insert.setString(4,receipt.workspaceId());insert.setString(5,receipt.consumerId());insert.setString(6,receipt.status().name());
                insert.setString(7,payload);PostgresJdbc.setInstant(insert,8,receipt.receivedAt());insert.executeUpdate();
            }
            WorkspaceReceiptEvidence existing=selectReceipt(c,receipt.receiptId(),true).orElseThrow();
            if(!existing.equals(receipt))throw new SecurityException("B3 receipt idempotency key conflicts with different evidence");
            return null;
        });
    }

    @Override public Optional<WorkspaceReceiptEvidence> findReceipt(MemoryScopeId scope,String receiptId) {
        requireScope(scope);
        try(Connection c=dataSource.getConnection()) {return selectReceipt(c,required(receiptId),false);}
        catch(SQLException e){throw new PostgresPersistenceException("PostgreSQL B3 receipt lookup failed",e);}
    }

    @Override public void appendOutcome(DownstreamOutcomeEvidence outcome) {
        requireScope(outcome.scopeId());String payload=PostgresJsonArtifactRepository.validJson(outcomeCodec.encode(outcome));
        transaction(c->{
            WorkspaceReceiptEvidence receipt=selectReceipt(c,outcome.receiptId(),true)
                    .orElseThrow(()->new SecurityException("B3 outcome references a missing receipt"));
            String candidateFingerprint=receipt.consumedCandidateFingerprints().get(outcome.candidateId());
            if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED||!receipt.cycleId().equals(outcome.cycleId())
                    ||!receipt.workspaceId().equals(outcome.workspaceId())||!receipt.consumerId().equals(outcome.consumerId())
                    ||!outcome.candidateFingerprint().equals(candidateFingerprint))
                throw new SecurityException("B3 outcome does not match the persisted accepted-consumption receipt");
            try(PreparedStatement insert=c.prepareStatement("""
                    INSERT INTO b3_downstream_outcome(scope_id,idempotency_key,receipt_id,workspace_id,cycle_id,candidate_id,
                        signal,payload,verified_at) VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),?)
                    ON CONFLICT(scope_id,idempotency_key) DO NOTHING
                    """)) {
                insert.setString(1,scopeId);insert.setString(2,outcome.idempotencyKey());insert.setString(3,outcome.receiptId());
                insert.setString(4,outcome.workspaceId());insert.setString(5,outcome.cycleId());insert.setString(6,outcome.candidateId());
                insert.setString(7,outcome.signal().name());insert.setString(8,payload);PostgresJdbc.setInstant(insert,9,outcome.verifiedAt());insert.executeUpdate();
            }
            try(PreparedStatement q=c.prepareStatement("SELECT payload::text FROM b3_downstream_outcome WHERE scope_id=? AND idempotency_key=? FOR UPDATE")) {
                q.setString(1,scopeId);q.setString(2,outcome.idempotencyKey());
                try(ResultSet r=q.executeQuery()){if(!r.next()||!outcome.equals(outcomeCodec.decode(r.getString(1))))
                    throw new SecurityException("B3 outcome idempotency key conflicts with different evidence");}
            }
            return null;
        });
    }

    @Override public List<DownstreamOutcomeEvidence> findOutcomes(MemoryScopeId scope,int maximum) {
        requireScope(scope);if(maximum<1||maximum>10000)throw new IllegalArgumentException("maximum must be in 1..10000");
        try(Connection c=dataSource.getConnection();PreparedStatement q=c.prepareStatement("""
                SELECT payload::text FROM b3_downstream_outcome WHERE scope_id=?
                ORDER BY verified_at ASC,idempotency_key ASC LIMIT ?
                """)) {
            q.setString(1,scopeId);q.setInt(2,maximum);List<DownstreamOutcomeEvidence> result=new ArrayList<>();
            try(ResultSet r=q.executeQuery()){while(r.next())result.add(outcomeCodec.decode(r.getString(1)));}
            return List.copyOf(result);
        } catch(SQLException e){throw new PostgresPersistenceException("PostgreSQL B3 outcome query failed",e);}
    }

    @Override public void appendCoalitionOutcome(WorkspaceCoalitionOutcomeEvidence outcome) {
        requireScope(outcome.scopeId());
        if (coalitionOutcomeCodec == null) throw new IllegalStateException("coalition outcome JSON codec is required for B3 coalition learning persistence");
        String payload=PostgresJsonArtifactRepository.validJson(coalitionOutcomeCodec.encode(outcome));
        transaction(c->{
            WorkspaceReceiptEvidence receipt=selectReceipt(c,outcome.receiptId(),true)
                    .orElseThrow(()->new SecurityException("coalition outcome references a missing receipt"));
            var coalition=receipt.selectedCoalitions().get(outcome.coalitionId());
            if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED || coalition==null
                    || !coalition.proposalFingerprint().equals(outcome.proposalFingerprint())
                    || !coalition.candidateIds().equals(outcome.candidateIds())
                    || !receipt.workspaceId().equals(outcome.workspaceId()) || !receipt.cycleId().equals(outcome.cycleId())
                    || !receipt.consumerId().equals(outcome.consumerId()))
                throw new SecurityException("B3 coalition outcome does not match the exact selected receipt");
            try(PreparedStatement insert=c.prepareStatement("""
                    INSERT INTO b3_coalition_downstream_outcome(scope_id,idempotency_key,receipt_id,workspace_id,cycle_id,
                        coalition_id,proposal_fingerprint,signal,payload,verified_at)
                    VALUES(?,?,?,?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT(scope_id,idempotency_key) DO NOTHING
                    """)) {
                insert.setString(1,scopeId);insert.setString(2,outcome.idempotencyKey());insert.setString(3,outcome.receiptId());
                insert.setString(4,outcome.workspaceId());insert.setString(5,outcome.cycleId());insert.setString(6,outcome.coalitionId());
                insert.setString(7,outcome.proposalFingerprint());insert.setString(8,outcome.signal().name());insert.setString(9,payload);
                PostgresJdbc.setInstant(insert,10,outcome.verifiedAt());insert.executeUpdate();
            }
            try(PreparedStatement q=c.prepareStatement("SELECT payload::text FROM b3_coalition_downstream_outcome WHERE scope_id=? AND idempotency_key=? FOR UPDATE")) {
                q.setString(1,scopeId);q.setString(2,outcome.idempotencyKey());
                try(ResultSet r=q.executeQuery()){if(!r.next()||!outcome.equals(coalitionOutcomeCodec.decode(r.getString(1))))
                    throw new SecurityException("B3 coalition outcome key conflicts with different evidence");}
            }
            return null;
        });
    }

    @Override public List<WorkspaceCoalitionOutcomeEvidence> findCoalitionOutcomes(MemoryScopeId scope,int maximum) {
        requireScope(scope);if(maximum<1||maximum>10000)throw new IllegalArgumentException("maximum must be in 1..10000");
        if (coalitionOutcomeCodec == null) throw new IllegalStateException("coalition outcome JSON codec is required for B3 coalition learning persistence");
        try(Connection c=dataSource.getConnection();PreparedStatement q=c.prepareStatement("""
                SELECT payload::text FROM b3_coalition_downstream_outcome WHERE scope_id=?
                ORDER BY verified_at ASC,idempotency_key ASC LIMIT ?
                """)) {
            q.setString(1,scopeId);q.setInt(2,maximum);List<WorkspaceCoalitionOutcomeEvidence> result=new ArrayList<>();
            try(ResultSet r=q.executeQuery()){while(r.next())result.add(coalitionOutcomeCodec.decode(r.getString(1)));}
            return List.copyOf(result);
        } catch(SQLException e){throw new PostgresPersistenceException("PostgreSQL B3 coalition outcome query failed",e);}
    }

    private Optional<WorkspaceReceiptEvidence> selectReceipt(Connection c,String receiptId,boolean lock)throws SQLException {
        String sql="SELECT payload::text FROM b3_workspace_consumer_receipt WHERE scope_id=? AND receipt_id=?"+(lock?" FOR UPDATE":"");
        try(PreparedStatement q=c.prepareStatement(sql)){q.setString(1,scopeId);q.setString(2,receiptId);
            try(ResultSet r=q.executeQuery()){return r.next()?Optional.of(receiptCodec.decode(r.getString(1))):Optional.empty();}}
    }
    private void requireScope(MemoryScopeId scope){if(!scopeId.equals(Objects.requireNonNull(scope,"scopeId").value()))throw new SecurityException("B3 evidence scope mismatch");}
    private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work){
        try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{T v=work.run(c);c.commit();return v;}
            catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}if(e instanceof SQLException sql)throw sql;throw e;}}
        catch(SQLException e){throw new PostgresPersistenceException("PostgreSQL B3 outcome evidence transaction failed",e);}
    }
    private static String required(String value){String s=Objects.requireNonNull(value,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
}
