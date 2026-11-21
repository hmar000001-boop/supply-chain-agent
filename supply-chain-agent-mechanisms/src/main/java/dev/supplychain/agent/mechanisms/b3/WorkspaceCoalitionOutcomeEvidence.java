package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Append-only downstream result attributed to one exact selected coalition proposal. */
public record WorkspaceCoalitionOutcomeEvidence(String idempotencyKey,String receiptId,MemoryScopeId scopeId,
        String workspaceId,String cycleId,String consumerId,String coalitionId,String proposalFingerprint,String preferenceKey,
        Set<String> candidateIds,DownstreamOutcomeSignal signal,String verifierId,List<EvidenceReference> evidence,
        String experimentUnitFingerprint,String admissionPolicyVersion,Instant verifiedAt) {
    public WorkspaceCoalitionOutcomeEvidence {
        idempotencyKey=req(idempotencyKey,"idempotencyKey");receiptId=req(receiptId,"receiptId");
        scopeId=Objects.requireNonNull(scopeId,"scopeId");workspaceId=req(workspaceId,"workspaceId");
        cycleId=req(cycleId,"cycleId");consumerId=req(consumerId,"consumerId");coalitionId=req(coalitionId,"coalitionId");
        proposalFingerprint=sha(proposalFingerprint,"proposalFingerprint");preferenceKey=sha(preferenceKey,"preferenceKey");
        candidateIds=Set.copyOf(Objects.requireNonNull(candidateIds,"candidateIds"));
        if(candidateIds.size()<2||candidateIds.stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("coalition requires exact member IDs");
        signal=Objects.requireNonNull(signal,"signal");verifierId=req(verifierId,"verifierId");
        evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));if(evidence.isEmpty())throw new IllegalArgumentException("source evidence is required");
        experimentUnitFingerprint=sha(experimentUnitFingerprint,"experimentUnitFingerprint");
        admissionPolicyVersion=req(admissionPolicyVersion,"admissionPolicyVersion");verifiedAt=Objects.requireNonNull(verifiedAt,"verifiedAt");
    }
    private static String sha(String v,String n){String s=req(v,n);if(!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(n+" must be SHA-256");return s;}
    private static String req(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
