package dev.supplychain.agent.mechanisms.b2;

import java.time.Instant;

/** Host authorization must inspect the exact typed draft, including every registered capability reference. */
@FunctionalInterface
public interface RecoveryProcedureApprovalAuthority {
    boolean isAuthorized(RecoveryProcedureDraft draft,String approverId,String approvalReference,Instant at);
}
