package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkspaceAdmissionRequest(String cycleId,MemoryScopeId scopeId,int capacityUnits,int maximumCandidates,
                                        Set<String> requiredCoverage,Map<String,Double> coverageValue,
                                        WorkspaceScoringPolicy scoringPolicy,List<WorkspaceCandidate> candidates,Instant createdAt,
                                        UncertaintyAssessment.Reference uncertaintyAssessmentReference,
                                        String experimentUnitFingerprint, UncertaintyGovernanceDirective uncertaintyDirective){
    public WorkspaceAdmissionRequest{
        cycleId=require(cycleId,"cycleId");scopeId=Objects.requireNonNull(scopeId,"scopeId");
        if(capacityUnits<1||maximumCandidates<1)throw new IllegalArgumentException("workspace capacity and maximum candidates must be positive");
        requiredCoverage=Set.copyOf(Objects.requireNonNull(requiredCoverage,"requiredCoverage"));
        coverageValue=Map.copyOf(Objects.requireNonNull(coverageValue,"coverageValue"));
        coverageValue.forEach((k,v)->{if(k==null||k.isBlank()||v==null||!Double.isFinite(v)||v<0)throw new IllegalArgumentException("coverage values must be finite and nonnegative");});
        if(!coverageValue.keySet().containsAll(requiredCoverage))throw new IllegalArgumentException("every required coverage tag must have a value");
        scoringPolicy=Objects.requireNonNull(scoringPolicy,"scoringPolicy");candidates=List.copyOf(Objects.requireNonNull(candidates,"candidates"));
        createdAt=Objects.requireNonNull(createdAt,"createdAt");
        experimentUnitFingerprint=experimentUnitFingerprint==null?"":experimentUnitFingerprint.trim();
        if(!experimentUnitFingerprint.isEmpty()&&!experimentUnitFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("experimentUnitFingerprint must be an empty value or SHA-256");
        if (uncertaintyAssessmentReference != null
                && (!uncertaintyAssessmentReference.memoryScopeId().equals(scopeId.value()))) {
            throw new SecurityException("workspace uncertainty assessment belongs to a different memory scope");
        }
        uncertaintyDirective = uncertaintyDirective == null ? neutralDirective(scopeId, uncertaintyAssessmentReference) : uncertaintyDirective;
        if (!uncertaintyDirective.assessmentReference().memoryScopeId().equals(scopeId.value()))
            throw new SecurityException("B3 uncertainty directive belongs to another scope");
        if (uncertaintyAssessmentReference != null && !uncertaintyDirective.assessmentReference().equals(uncertaintyAssessmentReference))
            throw new SecurityException("B3 uncertainty directive differs from the exact assessment reference");
        var ids=new java.util.HashSet<String>();for(var c:candidates){if(!scopeId.equals(c.scopeId()))throw new SecurityException("workspace candidate scope mismatch");if(!ids.add(c.id()))throw new IllegalArgumentException("duplicate workspace candidate: "+c.id());}
    }
    /** Source-compatible constructor before calibrated B6 workspace governance was propagated. */
    public WorkspaceAdmissionRequest(String cycleId, MemoryScopeId scopeId, int capacityUnits, int maximumCandidates,
            Set<String> requiredCoverage, Map<String,Double> coverageValue, WorkspaceScoringPolicy scoringPolicy,
            List<WorkspaceCandidate> candidates, Instant createdAt,
            UncertaintyAssessment.Reference uncertaintyAssessmentReference, String experimentUnitFingerprint) {
        this(cycleId,scopeId,capacityUnits,maximumCandidates,requiredCoverage,coverageValue,scoringPolicy,candidates,
                createdAt,uncertaintyAssessmentReference,experimentUnitFingerprint,neutralDirective(scopeId,uncertaintyAssessmentReference));
    }

    /** Compatibility constructor for standalone mechanism tests and non-agent admission fixtures. */
    public WorkspaceAdmissionRequest(String cycleId, MemoryScopeId scopeId, int capacityUnits, int maximumCandidates,
                                     Set<String> requiredCoverage, Map<String, Double> coverageValue,
                                     WorkspaceScoringPolicy scoringPolicy, List<WorkspaceCandidate> candidates,
                                     Instant createdAt) {
        this(cycleId, scopeId, capacityUnits, maximumCandidates, requiredCoverage, coverageValue,
                scoringPolicy, candidates, createdAt, null, "", neutralDirective(scopeId, null));
    }

    /** Compatibility constructor for callers already supplying the active uncertainty reference. */
    public WorkspaceAdmissionRequest(String cycleId, MemoryScopeId scopeId, int capacityUnits, int maximumCandidates,
                                     Set<String> requiredCoverage, Map<String, Double> coverageValue,
                                     WorkspaceScoringPolicy scoringPolicy, List<WorkspaceCandidate> candidates,
                                     Instant createdAt, UncertaintyAssessment.Reference uncertaintyAssessmentReference) {
        this(cycleId, scopeId, capacityUnits, maximumCandidates, requiredCoverage, coverageValue,
                scoringPolicy, candidates, createdAt, uncertaintyAssessmentReference, "", neutralDirective(scopeId, uncertaintyAssessmentReference));
    }

    public WorkspaceAdmissionRequest withCandidates(List<WorkspaceCandidate> replacements) {
        return new WorkspaceAdmissionRequest(cycleId, scopeId, capacityUnits, maximumCandidates, requiredCoverage,
                coverageValue, scoringPolicy, replacements, createdAt, uncertaintyAssessmentReference, experimentUnitFingerprint, uncertaintyDirective);
    }

    public WorkspaceAdmissionRequest withExperimentUnitFingerprint(String fingerprint) {
        return new WorkspaceAdmissionRequest(cycleId, scopeId, capacityUnits, maximumCandidates, requiredCoverage,
                coverageValue, scoringPolicy, candidates, createdAt, uncertaintyAssessmentReference, fingerprint, uncertaintyDirective);
    }

    public WorkspaceAdmissionRequest withUncertaintyDirective(UncertaintyGovernanceDirective directive) {
        return new WorkspaceAdmissionRequest(cycleId,scopeId,capacityUnits,maximumCandidates,requiredCoverage,
                coverageValue,scoringPolicy,candidates,createdAt,uncertaintyAssessmentReference,experimentUnitFingerprint,directive);
    }

    private static UncertaintyGovernanceDirective neutralDirective(MemoryScopeId scope, UncertaintyAssessment.Reference existing) {
        var ref = existing == null ? new UncertaintyAssessment.Reference("b3-standalone-neutral",1,"b3-standalone",scope.value()) : existing;
        return new UncertaintyGovernanceDirective(ref,UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED,
                "","",0,0,0,0,1,1,false,false);
    }

    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
