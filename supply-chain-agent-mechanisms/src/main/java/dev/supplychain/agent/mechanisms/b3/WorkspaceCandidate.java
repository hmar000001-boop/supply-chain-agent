package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorkspaceCandidate(String id, MemoryScopeId scopeId, WorkspaceCandidateKind kind,
                                 double utilityPoints, double reliability, double risk,
                                 int contextUnits, Set<String> coverageTags, Set<String> conflictsWith,
                                 boolean mandatory, List<EvidenceReference> evidence,
                                 List<B1MemoryAttribution> memoryAttributions) {
    public WorkspaceCandidate {
        id=require(id,"id");scopeId=Objects.requireNonNull(scopeId,"scopeId");kind=Objects.requireNonNull(kind,"kind");
        if(!Double.isFinite(utilityPoints)||utilityPoints<0)throw new IllegalArgumentException("utilityPoints must be finite and nonnegative");
        if(!unit(reliability)||!unit(risk))throw new IllegalArgumentException("reliability and risk must be in [0,1]");
        if(contextUnits<1)throw new IllegalArgumentException("contextUnits must be positive");
        coverageTags=normalized(coverageTags);conflictsWith=normalized(conflictsWith);evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));
        memoryAttributions=List.copyOf(Objects.requireNonNull(memoryAttributions,"memoryAttributions"));
        for (var attribution : memoryAttributions) if (!scopeId.equals(attribution.reference().scopeId()))
            throw new SecurityException("workspace candidate B1 attribution belongs to a different scope");
        if (memoryAttributions.stream().map(a -> a.reference()).distinct().count() != memoryAttributions.size())
            throw new IllegalArgumentException("workspace candidate contains duplicate B1 memory attributions");
        if(conflictsWith.contains(id))throw new IllegalArgumentException("candidate cannot conflict with itself");
    }
    /** Source-compatible candidate shape before B1 outcome attribution was added. */
    public WorkspaceCandidate(String id, MemoryScopeId scopeId, WorkspaceCandidateKind kind,
            double utilityPoints, double reliability, double risk, int contextUnits,
            Set<String> coverageTags, Set<String> conflictsWith, boolean mandatory,
            List<EvidenceReference> evidence) {
        this(id, scopeId, kind, utilityPoints, reliability, risk, contextUnits, coverageTags,
                conflictsWith, mandatory, evidence, List.of());
    }
    public WorkspaceCandidate withMemoryAttributions(List<B1MemoryAttribution> attributions) {
        return new WorkspaceCandidate(id, scopeId, kind, utilityPoints, reliability, risk, contextUnits,
                coverageTags, conflictsWith, mandatory, evidence, attributions);
    }
    private static boolean unit(double v){return Double.isFinite(v)&&v>=0&&v<=1;}
    private static Set<String> normalized(Set<String> values){return values.stream().map(v->require(v,"tag")).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
