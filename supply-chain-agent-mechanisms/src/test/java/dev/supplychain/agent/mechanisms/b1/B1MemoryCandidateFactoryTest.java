package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.*;
import dev.supplychain.agent.memory.episodic.*;
import dev.supplychain.agent.memory.procedural.*;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.memory.semantic.*;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B1MemoryCandidateFactoryTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-a");
    private MemoryRetrievalResult allMemoryTypes(){
        var evidence=new EvidenceReference("source","1");
        var provenance=new Provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM,"source",NOW.minusSeconds(150),NOW.minusSeconds(120),"v1");
        var wm=WorkingMemorySnapshot.empty(SCOPE,NOW.minusSeconds(60),NOW.plusSeconds(3600));
        var episode=Episode.recorded(new EpisodeId("ep-1"),SCOPE,"Supplier delay","Validated alternate supplier worked",EpisodeOutcome.SUCCESS,
                NOW.minusSeconds(300),NOW.minusSeconds(180),NOW.minusSeconds(120),NOW.plusSeconds(3600),List.of(),List.of(),List.of(evidence),provenance)
                .verify("reviewer",new EvidenceReference("review","r1"),NOW.minusSeconds(60));
        var fact=SemanticFact.proposed(new FactKey(SCOPE,new EntityReference("supplier","SUP-1"),"qualified"),new FactValue.Flag(true),
                NOW.minusSeconds(3600),null,NOW.minusSeconds(120),provenance)
                .activate("approver",new EvidenceReference("approval","a1"),NOW.minusSeconds(60),null);
        var draft=ProcedureVersion.draft(SCOPE,"supplier-outage-runbook",1,ProcedureKind.RUNBOOK,"Supplier outage procedure",
                new ProcedureArtifactReference(URI.create("https://docs.invalid/runbook"),"a".repeat(64)),provenance,NOW.minusSeconds(120),"author",null);
        var procedure=draft.validate("reviewer",NOW.minusSeconds(90)).activate("approver",NOW.minusSeconds(60));
        return new MemoryRetrievalResult(Optional.of(wm),List.of(episode),List.of(fact),List.of(procedure));
    }
    private B1CandidateFeatures features(){return new B1CandidateFeatures(List.of(NOW.minusSeconds(60)),Map.of("supplier-outage",0.8),10,2,Duration.ofMillis(3),new MaintenanceProfile(0,0,0,Duration.ofDays(30),NOW));}

    @Test void adaptsWorkingEpisodicSemanticAndProceduralMemoryWithoutChangingM2Records(){
        var refs=new java.util.ArrayList<B1MemoryCandidateReference>();
        var candidates=new B1MemoryCandidateFactory().from(SCOPE,allMemoryTypes(),NOW,ref->{refs.add(ref);return features();});
        assertEquals(4,candidates.size());
        assertEquals(Set.of(B1MemoryCandidateKind.WORKING,B1MemoryCandidateKind.EPISODIC,B1MemoryCandidateKind.SEMANTIC,B1MemoryCandidateKind.PROCEDURAL),
                candidates.stream().map(B1MemoryCandidate::kind).collect(java.util.stream.Collectors.toSet()));
        assertTrue(candidates.stream().allMatch(c->c.scopeId().equals(SCOPE)));
        assertEquals(4,refs.size());
    }
    @Test void refusesWorkingMemoryFromDifferentScope(){
        var foreign=WorkingMemorySnapshot.empty(new MemoryScopeId("tenant-b"),NOW.minusSeconds(60),NOW.plusSeconds(3600));
        var retrieved=new MemoryRetrievalResult(Optional.of(foreign),List.of(),List.of(),List.of());
        assertThrows(SecurityException.class,()->new B1MemoryCandidateFactory().from(SCOPE,retrieved,NOW,ref->features()));
    }
    @Test void expiredWorkingMemoryCannotEnterActivationSelection(){
        var expired=WorkingMemorySnapshot.empty(SCOPE,NOW.minusSeconds(600),NOW.minusSeconds(1));
        var retrieved=new MemoryRetrievalResult(Optional.of(expired),List.of(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->new B1MemoryCandidateFactory().from(SCOPE,retrieved,NOW,ref->features()));
    }
    @Test void historicalUnverifiedEpisodeIsNotAdmittedToB1Candidates(){
        var evidence=new EvidenceReference("source","1");var provenance=new Provenance(ProvenanceSource.VERIFIED_DOCUMENT,"source",NOW.minusSeconds(150),NOW.minusSeconds(120),"v1");
        var episode=Episode.recorded(new EpisodeId("ep-2"),SCOPE,"Unreviewed","not trusted",EpisodeOutcome.UNKNOWN,NOW.minusSeconds(300),NOW.minusSeconds(180),NOW.minusSeconds(120),NOW.plusSeconds(3600),List.of(),List.of(),List.of(evidence),provenance);
        var retrieved=new MemoryRetrievalResult(Optional.empty(),List.of(episode),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->new B1MemoryCandidateFactory().from(SCOPE,retrieved,NOW,ref->features()));
    }
}
