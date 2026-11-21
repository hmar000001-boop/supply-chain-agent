package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.List;
import java.util.Optional;

/** Append-only cross-cycle audit evidence; never stores the live B3 workspace itself. */
public interface B3OutcomeEvidenceRepository {
    void appendReceipt(WorkspaceReceiptEvidence receipt);
    Optional<WorkspaceReceiptEvidence> findReceipt(MemoryScopeId scopeId, String receiptId);
    void appendOutcome(DownstreamOutcomeEvidence outcome);
    List<DownstreamOutcomeEvidence> findOutcomes(MemoryScopeId scopeId, int maximum);
    void appendCoalitionOutcome(WorkspaceCoalitionOutcomeEvidence outcome);
    List<WorkspaceCoalitionOutcomeEvidence> findCoalitionOutcomes(MemoryScopeId scopeId, int maximum);
}
