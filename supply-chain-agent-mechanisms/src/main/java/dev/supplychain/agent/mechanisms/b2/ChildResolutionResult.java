package dev.supplychain.agent.mechanisms.b2;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Deliberately contains findings only: no parent objective, capability or domain mutation can be returned. */
public record ChildResolutionResult(String caseId, String parentRequestId, long parentWorkingMemoryVersion,
                                    String objectiveFingerprint, ResolutionDisposition disposition,
                                    List<ResolutionFinding> findings, List<String> assumptions, Instant producedAt) {
    public ChildResolutionResult {
        caseId=require(caseId,"caseId"); parentRequestId=require(parentRequestId,"parentRequestId");
        if(parentWorkingMemoryVersion<0) throw new IllegalArgumentException("parent version cannot be negative");
        objectiveFingerprint=require(objectiveFingerprint,"objectiveFingerprint");
        disposition=Objects.requireNonNull(disposition,"disposition");
        findings=List.copyOf(Objects.requireNonNull(findings,"findings"));
        assumptions=List.copyOf(Objects.requireNonNull(assumptions,"assumptions"));
        if(assumptions.stream().anyMatch(s->s==null||s.isBlank())) throw new IllegalArgumentException("assumptions cannot be blank");
        producedAt=Objects.requireNonNull(producedAt,"producedAt");
        if(disposition==ResolutionDisposition.UNRESOLVED && !findings.isEmpty()) throw new IllegalArgumentException("unresolved result cannot claim findings");
        if(disposition!=ResolutionDisposition.UNRESOLVED && findings.isEmpty()) throw new IllegalArgumentException("resolved result requires evidence-backed findings");
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
