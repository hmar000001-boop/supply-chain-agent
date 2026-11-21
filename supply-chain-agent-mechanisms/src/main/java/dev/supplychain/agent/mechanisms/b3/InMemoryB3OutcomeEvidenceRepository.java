package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic test/embedded adapter. Production uses PostgresB3OutcomeEvidenceRepository. */
public final class InMemoryB3OutcomeEvidenceRepository implements B3OutcomeEvidenceRepository {
    private final Map<String,WorkspaceReceiptEvidence> receipts=new LinkedHashMap<>();
    private final Map<String,DownstreamOutcomeEvidence> outcomes=new LinkedHashMap<>();
    private final Map<String,WorkspaceCoalitionOutcomeEvidence> coalitionOutcomes=new LinkedHashMap<>();
    @Override public synchronized void appendReceipt(WorkspaceReceiptEvidence receipt) {
        String key=receipt.scopeId().value()+"|"+receipt.receiptId();
        var existing=receipts.putIfAbsent(key,receipt);
        if(existing!=null&&!existing.equals(receipt)) throw new SecurityException("receipt idempotency key conflicts with existing evidence");
    }
    @Override public synchronized Optional<WorkspaceReceiptEvidence> findReceipt(MemoryScopeId scopeId,String receiptId) {
        return Optional.ofNullable(receipts.get(scopeId.value()+"|"+receiptId));
    }
    @Override public synchronized void appendOutcome(DownstreamOutcomeEvidence outcome) {
        String key=outcome.scopeId().value()+"|"+outcome.idempotencyKey();
        var receipt=receipts.get(outcome.scopeId().value()+"|"+outcome.receiptId());
        if(receipt==null) throw new SecurityException("outcome cannot be recorded without its durable receipt");
        if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED
                ||!receipt.consumedCandidateFingerprints().containsKey(outcome.candidateId())
                ||!receipt.consumedCandidateFingerprints().get(outcome.candidateId()).equals(outcome.candidateFingerprint()))
            throw new SecurityException("outcome does not match an accepted candidate-consumption receipt");
        var existing=outcomes.putIfAbsent(key,outcome);
        if(existing!=null&&!existing.equals(outcome)) throw new SecurityException("outcome idempotency key conflicts with existing evidence");
    }
    @Override public synchronized void appendCoalitionOutcome(WorkspaceCoalitionOutcomeEvidence outcome) {
        String key=outcome.scopeId().value()+"|"+outcome.idempotencyKey();
        var receipt=receipts.get(outcome.scopeId().value()+"|"+outcome.receiptId());
        if(receipt==null || receipt.status()!=ConsumerReceiptStatus.ACCEPTED)
            throw new SecurityException("coalition outcome requires its accepted durable receipt");
        var coalition=receipt.selectedCoalitions().get(outcome.coalitionId());
        if(coalition==null || !coalition.proposalFingerprint().equals(outcome.proposalFingerprint())
                || !coalition.candidateIds().equals(outcome.candidateIds()))
            throw new SecurityException("coalition outcome does not match the exact selected coalition receipt");
        var existing=coalitionOutcomes.putIfAbsent(key,outcome);
        if(existing!=null&&!existing.equals(outcome))throw new SecurityException("coalition outcome idempotency key conflicts with existing evidence");
    }
    @Override public synchronized List<WorkspaceCoalitionOutcomeEvidence> findCoalitionOutcomes(MemoryScopeId scopeId,int maximum) {
        if(maximum<1||maximum>10000)throw new IllegalArgumentException("maximum must be in 1..10000");
        return coalitionOutcomes.values().stream().filter(v->v.scopeId().equals(scopeId))
                .sorted(Comparator.comparing(WorkspaceCoalitionOutcomeEvidence::verifiedAt)
                        .thenComparing(WorkspaceCoalitionOutcomeEvidence::idempotencyKey)).limit(maximum).toList();
    }
    @Override public synchronized List<DownstreamOutcomeEvidence> findOutcomes(MemoryScopeId scopeId,int maximum) {
        if(maximum<1||maximum>10000) throw new IllegalArgumentException("maximum must be in 1..10000");
        return outcomes.values().stream().filter(v->v.scopeId().equals(scopeId))
                .sorted(Comparator.comparing(DownstreamOutcomeEvidence::verifiedAt)
                        .thenComparing(DownstreamOutcomeEvidence::idempotencyKey))
                .limit(maximum).toList();
    }
}
