package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bridges M2's exact retrieval result to B1 candidates without changing M2 memory records. */
public final class B1MemoryCandidateFactory{
    public List<B1MemoryCandidate> from(MemoryScopeId expectedScope,MemoryRetrievalResult retrieved,
                                       Instant knowledgeTime,B1CandidateFeatureProvider featureProvider){
        Objects.requireNonNull(expectedScope,"expectedScope");Objects.requireNonNull(retrieved,"retrieved");
        Objects.requireNonNull(knowledgeTime,"knowledgeTime");Objects.requireNonNull(featureProvider,"featureProvider");
        var result=new ArrayList<B1MemoryCandidate>();
        retrieved.workingMemory().ifPresent(value->{
            requireScope(expectedScope,value.scopeId());
            if(!value.expiresAt().isAfter(knowledgeTime))throw new IllegalArgumentException("expired working memory cannot enter B1 selection");
            result.add(candidate(new B1MemoryCandidateReference(B1MemoryCandidateKind.WORKING,value.scopeId(),value.id().value(),value.version()),featureProvider));
        });
        retrieved.verifiedEpisodes().forEach(value->{
            requireScope(expectedScope,value.scopeId());
            if(!value.knownAsVerifiedAt(knowledgeTime))throw new IllegalArgumentException("episode was not verified at requested knowledge time");
            result.add(candidate(new B1MemoryCandidateReference(B1MemoryCandidateKind.EPISODIC,value.scopeId(),value.id().value(),value.version()),featureProvider));
        });
        retrieved.activeSemanticFacts().forEach(value->{
            requireScope(expectedScope,value.scopeId());
            if(!value.knownAsActiveAt(knowledgeTime))throw new IllegalArgumentException("semantic fact was not active at requested knowledge time");
            result.add(candidate(new B1MemoryCandidateReference(B1MemoryCandidateKind.SEMANTIC,value.scopeId(),value.id().value(),value.version()),featureProvider));
        });
        retrieved.activeProcedures().forEach(value->{
            requireScope(expectedScope,value.scopeId());
            if(!value.knownActiveAt(knowledgeTime))throw new IllegalArgumentException("procedure was not active at requested knowledge time");
            result.add(candidate(new B1MemoryCandidateReference(B1MemoryCandidateKind.PROCEDURAL,value.scopeId(),value.id().value(),value.lifecycleRevision()),featureProvider));
        });
        return List.copyOf(result);
    }
    private static B1MemoryCandidate candidate(B1MemoryCandidateReference ref,B1CandidateFeatureProvider provider){
        var features=Objects.requireNonNull(provider.featuresFor(ref),"feature provider returned null");
        return new B1MemoryCandidate(ref.candidateId(),ref.scopeId(),ref.kind(),ref.memoryReference(),features.accessHistory(),
                features.cueAssociationStrengths(),features.expectedDownstreamUtility(),features.contextUnits(),
                features.measuredInfrastructureLatency(),features.maintenanceProfile());
    }
    private static void requireScope(MemoryScopeId expected,MemoryScopeId actual){if(!expected.equals(actual))throw new SecurityException("retrieved memory item belongs to a different scope");}
}
