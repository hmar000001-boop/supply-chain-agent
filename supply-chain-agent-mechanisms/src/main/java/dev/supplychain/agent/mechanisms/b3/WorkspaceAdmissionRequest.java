package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkspaceAdmissionRequest(String cycleId,MemoryScopeId scopeId,int capacityUnits,int maximumCandidates,
                                        Set<String> requiredCoverage,Map<String,Double> coverageValue,
                                        WorkspaceScoringPolicy scoringPolicy,List<WorkspaceCandidate> candidates,Instant createdAt){
    public WorkspaceAdmissionRequest{
        cycleId=require(cycleId,"cycleId");scopeId=Objects.requireNonNull(scopeId,"scopeId");
        if(capacityUnits<1||maximumCandidates<1)throw new IllegalArgumentException("workspace capacity and maximum candidates must be positive");
        requiredCoverage=Set.copyOf(Objects.requireNonNull(requiredCoverage,"requiredCoverage"));
        coverageValue=Map.copyOf(Objects.requireNonNull(coverageValue,"coverageValue"));
        coverageValue.forEach((k,v)->{if(k==null||k.isBlank()||v==null||!Double.isFinite(v)||v<0)throw new IllegalArgumentException("coverage values must be finite and nonnegative");});
        if(!coverageValue.keySet().containsAll(requiredCoverage))throw new IllegalArgumentException("every required coverage tag must have a value");
        scoringPolicy=Objects.requireNonNull(scoringPolicy,"scoringPolicy");candidates=List.copyOf(Objects.requireNonNull(candidates,"candidates"));
        createdAt=Objects.requireNonNull(createdAt,"createdAt");
        var ids=new java.util.HashSet<String>();for(var c:candidates){if(!scopeId.equals(c.scopeId()))throw new SecurityException("workspace candidate scope mismatch");if(!ids.add(c.id()))throw new IllegalArgumentException("duplicate workspace candidate: "+c.id());}
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
