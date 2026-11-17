package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Objects;

public record ParentContextSnapshot(MemoryScopeId scopeId, String requestId, long workingMemoryVersion,
                                    String objectiveFingerprint) {
    public ParentContextSnapshot {
        scopeId=Objects.requireNonNull(scopeId,"scopeId");
        requestId=require(requestId,"requestId");
        if(workingMemoryVersion<0) throw new IllegalArgumentException("workingMemoryVersion cannot be negative");
        objectiveFingerprint=require(objectiveFingerprint,"objectiveFingerprint");
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
