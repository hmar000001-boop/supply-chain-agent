package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Durable audit projection of a consumer receipt; active WorkspaceSnapshot state remains ephemeral. */
public record WorkspaceReceiptEvidence(String receiptId, MemoryScopeId scopeId, String cycleId,
        String workspaceId, String consumerId, ConsumerReceiptStatus status,
        Map<String,String> consumedCandidateFingerprints, String workspaceFingerprint,
        String admissionPolicyVersion, Instant receivedAt) {
    public WorkspaceReceiptEvidence {
        receiptId=required(receiptId,"receiptId"); scopeId=Objects.requireNonNull(scopeId,"scopeId");
        cycleId=required(cycleId,"cycleId"); workspaceId=required(workspaceId,"workspaceId");
        consumerId=required(consumerId,"consumerId"); status=Objects.requireNonNull(status,"status");
        var copy=new TreeMap<String,String>();
        Objects.requireNonNull(consumedCandidateFingerprints,"consumedCandidateFingerprints").forEach((id,fp)->{
            String candidate=required(id,"candidateId"), fingerprint=required(fp,"candidateFingerprint");
            if(!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("candidate fingerprint must be SHA-256");
            copy.put(candidate,fingerprint);
        });
        consumedCandidateFingerprints=Map.copyOf(copy);
        if(status!=ConsumerReceiptStatus.ACCEPTED && !consumedCandidateFingerprints.isEmpty())
            throw new IllegalArgumentException("non-accepted receipt cannot claim consumed candidates");
        workspaceFingerprint=required(workspaceFingerprint,"workspaceFingerprint");
        if(!workspaceFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("workspace fingerprint must be SHA-256");
        admissionPolicyVersion=required(admissionPolicyVersion,"admissionPolicyVersion");
        receivedAt=Objects.requireNonNull(receivedAt,"receivedAt");
    }
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
