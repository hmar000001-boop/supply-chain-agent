package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable per-step B2 receipt port; a completed read step is replayed from its receipt, never re-executed. */
public interface ChildInvestigationStepStore {
    StepClaim claim(String caseId, String stepId, String inputFingerprint, Instant at);
    void complete(String caseId, String stepId, long fencingToken, List<ResolutionFinding> findings, Instant at);
    void fail(String caseId, String stepId, long fencingToken, String reason, boolean outcomeUnknown, Instant at);

    record StepClaim(Status status, long fencingToken, List<ResolutionFinding> findings, String reason) {
        public StepClaim {
            status = Objects.requireNonNull(status, "status");
            if (fencingToken < 0) throw new IllegalArgumentException("fencingToken cannot be negative");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
            reason = reason == null ? "" : reason.trim();
            if (status == Status.COMPLETED && fencingToken != 0) throw new IllegalArgumentException("completed step has no lease token");
            if (status != Status.COMPLETED && !findings.isEmpty()) throw new IllegalArgumentException("only completed steps carry findings");
        }
    }
    enum Status { ACQUIRED, COMPLETED, IN_PROGRESS, UNKNOWN_OUTCOME }
}
