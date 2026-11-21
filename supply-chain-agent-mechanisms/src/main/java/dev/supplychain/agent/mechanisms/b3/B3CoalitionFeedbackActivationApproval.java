package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;

public record B3CoalitionFeedbackActivationApproval(MemoryScopeId scopeId,String policyId,long policyVersion,
        String evaluationFingerprint,String approverId,String approvalReference,Instant approvedAt) {
    public B3CoalitionFeedbackActivationApproval {
        scopeId=Objects.requireNonNull(scopeId,"scopeId");policyId=req(policyId,"policyId");if(policyVersion<1)throw new IllegalArgumentException("policyVersion must be positive");
        sha(evaluationFingerprint,"evaluationFingerprint");approverId=req(approverId,"approverId");approvalReference=req(approvalReference,"approvalReference");
        approvedAt=Objects.requireNonNull(approvedAt,"approvedAt");
    }
    private static String req(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
    private static void sha(String v,String n){if(v==null||!v.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(n+" must be SHA-256");}
}
