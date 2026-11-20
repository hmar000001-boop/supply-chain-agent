package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;

/** Host-issued approval is bound to one scope, policy version and exact held-out report fingerprint. */
public record FeedbackPolicyActivationApproval(MemoryScopeId scopeId,String policyId,long policyVersion,
        String evaluationFingerprint,String approverId,String approvalReference,Instant approvedAt) {
    public FeedbackPolicyActivationApproval {
        scopeId=Objects.requireNonNull(scopeId,"scopeId");policyId=required(policyId,"policyId");
        if(policyVersion<1)throw new IllegalArgumentException("policyVersion must be positive");
        evaluationFingerprint=required(evaluationFingerprint,"evaluationFingerprint");
        if(!evaluationFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("evaluationFingerprint must be SHA-256");
        approverId=required(approverId,"approverId");approvalReference=required(approvalReference,"approvalReference");
        approvedAt=Objects.requireNonNull(approvedAt,"approvedAt");
    }
    private static String required(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
