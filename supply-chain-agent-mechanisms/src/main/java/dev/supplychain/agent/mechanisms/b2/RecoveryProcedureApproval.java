package dev.supplychain.agent.mechanisms.b2;

import java.time.Instant;
import java.util.Objects;

/** Host-issued approval proof. The mechanism never fabricates an approver or authority. */
public record RecoveryProcedureApproval(String approverId, String approvalReference, Instant approvedAt) {
    public RecoveryProcedureApproval {
        approverId = required(approverId, "approverId");
        approvalReference = required(approvalReference, "approvalReference");
        approvedAt = Objects.requireNonNull(approvedAt, "approvedAt");
    }
    private static String required(String value, String name) { String v=Objects.requireNonNull(value,name).trim();if(v.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return v; }
}
