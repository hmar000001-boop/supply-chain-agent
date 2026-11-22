package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** SHADOW-only B3 candidate plus the exact receipt/outcome ledger from which it was trained. */
public record B3CoalitionTrainingArtifact(
        B3CoalitionFeedbackCandidate candidate,
        List<WorkspaceCoalitionOutcomeEvidence> outcomes,
        Map<String, WorkspaceReceiptEvidence> receiptsById,
        String evidenceFingerprint) {
    public B3CoalitionTrainingArtifact {
        candidate = Objects.requireNonNull(candidate, "candidate");
        if (candidate.disposition() != B3CoalitionFeedbackCandidate.Disposition.SHADOW)
            throw new SecurityException("B3 training artifact cannot contain an activated policy");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        var receipts = new TreeMap<String, WorkspaceReceiptEvidence>();
        Objects.requireNonNull(receiptsById, "receiptsById").forEach((id, receipt) -> {
            if (id == null || receipt == null || !id.equals(receipt.receiptId()))
                throw new SecurityException("B3 receipt map key does not match receipt identity");
            receipts.put(id, receipt);
        });
        receiptsById = Map.copyOf(receipts);
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (outcomes.isEmpty()) throw new IllegalArgumentException("B3 coalition candidate requires source outcomes");
        var ids = outcomes.stream().map(WorkspaceCoalitionOutcomeEvidence::idempotencyKey).collect(java.util.stream.Collectors.toSet());
        var units = outcomes.stream().map(WorkspaceCoalitionOutcomeEvidence::experimentUnitFingerprint).collect(java.util.stream.Collectors.toSet());
        if (!ids.equals(SetView.of(candidate.sourceOutcomeIds())) || !units.equals(candidate.trainingUnitFingerprints()))
            throw new SecurityException("B3 candidate source IDs/units do not exactly match its coalition outcome ledger");
    }

    private static final class SetView {
        static java.util.Set<String> of(List<String> values) { return java.util.Set.copyOf(values); }
    }
}
