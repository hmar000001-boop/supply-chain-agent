package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B1RetrievalSelectorTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-a");
    private final B1RetrievalSelector selector=new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.5,1.0),
            new ExponentialActivationLatencyModel(1000,0.5,10,5000));
    private B1MemoryCandidate candidate(String id,double utility,int units,List<Instant> history,Map<String,Double> cues,MaintenanceProfile maintenance,Duration io){
        return new B1MemoryCandidate(id,SCOPE,B1MemoryCandidateKind.EPISODIC,"episode:"+id,history,cues,utility,units,io,maintenance);
    }
    private MaintenanceProfile maintenance(double base,double refresh,double daily,Instant last){return new MaintenanceProfile(base,refresh,daily,Duration.ofDays(30),last);}
    private B1SelectionRequest request(int budget,int maximum,double minProbability,double minUtility){return new B1SelectionRequest(SCOPE,Set.of("supplier-outage"),NOW,budget,maximum,Duration.ofDays(10),new B1ScoringPolicy(minProbability,0.001,0.001,minUtility));}

    @Test void recentAndFrequentAccessProducesGreaterActivation(){
        var estimator=new RecencyFrequencyActivationEstimator(0.5,0.0);
        var recent=estimator.estimate(new ActivationInput(List.of(NOW.minusSeconds(3600),NOW.minusSeconds(7200)),Map.of(),Set.of(),NOW));
        var old=estimator.estimate(new ActivationInput(List.of(NOW.minus(Duration.ofDays(200))),Map.of(),Set.of(),NOW));
        assertTrue(recent.totalActivation()>old.totalActivation());
    }
    @Test void cueAssociationContributesToActivation(){
        var estimator=new RecencyFrequencyActivationEstimator(0.5,2.0);
        var noCue=estimator.estimate(new ActivationInput(List.of(),Map.of("supplier-outage",0.8),Set.of(),NOW));
        var cue=estimator.estimate(new ActivationInput(List.of(),Map.of("supplier-outage",0.8),Set.of("supplier-outage"),NOW));
        assertTrue(cue.totalActivation()>noCue.totalActivation());
    }
    @Test void strongerActivationReducesPredictedCognitiveLatency(){
        var model=new ExponentialActivationLatencyModel(1000,0.5,5,5000);
        var low=new ActivationEstimate(-2,0,-2);var high=new ActivationEstimate(2,0,2);
        assertTrue(model.predict(high).compareTo(model.predict(low))<0);
    }
    @Test void selectorRanksByExpectedDownstreamValueAndFitsContextBudget(){
        var recent=List.of(NOW.minusSeconds(120),NOW.minusSeconds(300));
        var a=candidate("a",100,3,recent,Map.of("supplier-outage",1.0),maintenance(0,0,0,NOW),Duration.ofMillis(1));
        var b=candidate("b",30,2,recent,Map.of("supplier-outage",0.5),maintenance(0,0,0,NOW),Duration.ofMillis(1));
        var result=selector.select(request(3,5,0.1,0),List.of(b,a));
        assertEquals(List.of("a"),result.selectedCandidates().stream().map(B1MemoryCandidate::candidateId).toList());
        assertEquals(3,result.usedContextUnits());
        assertTrue(result.scores().stream().filter(s->s.candidateId().equals("a")).findFirst().orElseThrow().selected());
    }
    @Test void measuredInfrastructureLatencyIsSeparateFromCognitiveLatency(){
        var recent=List.of(NOW.minusSeconds(10));
        var fast=candidate("fast",20,1,recent,Map.of(),maintenance(0,0,0,NOW),Duration.ofMillis(1));
        var slow=candidate("slow",20,1,recent,Map.of(),maintenance(0,0,0,NOW),Duration.ofMillis(10000));
        var result=selector.select(request(2,2,0, -100),List.of(fast,slow));
        var fastScore=result.scores().stream().filter(s->s.candidateId().equals("fast")).findFirst().orElseThrow();
        var slowScore=result.scores().stream().filter(s->s.candidateId().equals("slow")).findFirst().orElseThrow();
        assertEquals(Duration.ofMillis(1),fastScore.measuredInfrastructureLatency());
        assertEquals(Duration.ofMillis(10000),slowScore.measuredInfrastructureLatency());
        assertTrue(fastScore.netUtility()>slowScore.netUtility());
    }
    @Test void maintenanceCostIsIncludedInNetUtility(){
        var recent=List.of(NOW.minusSeconds(10));
        var cheap=candidate("cheap",40,1,recent,Map.of(),maintenance(0,0,0,NOW),Duration.ZERO);
        var expensive=candidate("expensive",40,1,recent,Map.of(),maintenance(50,0,0,NOW),Duration.ZERO);
        var result=selector.select(request(2,2,0,-100),List.of(cheap,expensive));
        var sCheap=result.scores().stream().filter(s->s.candidateId().equals("cheap")).findFirst().orElseThrow();
        var sExp=result.scores().stream().filter(s->s.candidateId().equals("expensive")).findFirst().orElseThrow();
        assertTrue(sCheap.netUtility()>sExp.netUtility());
        assertEquals(50.0,sExp.estimatedMaintenanceCost(),0.0001);
    }
    @Test void scopeMismatchIsRejected(){
        var foreign=new B1MemoryCandidate("foreign",new MemoryScopeId("tenant-b"),B1MemoryCandidateKind.SEMANTIC,"fact:1",List.of(),Map.of(),1,1,Duration.ZERO,maintenance(0,0,0,NOW));
        assertThrows(SecurityException.class,()->selector.select(request(2,2,0,0),List.of(foreign)));
    }
    @Test void duplicateCandidateIdsAreRejected(){
        var c=candidate("same",10,1,List.of(),Map.of(),maintenance(0,0,0,NOW),Duration.ZERO);
        assertThrows(IllegalArgumentException.class,()->selector.select(request(2,2,0,0),List.of(c,c)));
    }
    @Test void maintenanceProfileChargesRefreshWhenDueAndRecurringHorizonCost(){
        var profile=maintenance(2,5,1,NOW.minus(Duration.ofDays(40)));
        assertEquals(17.0,profile.estimateCost(NOW,Duration.ofDays(10)),0.0001);
    }
    @Test void maintenanceLedgerIsIdempotentAndAggregatesActualCost(){
        var ledger=new InMemoryMemoryMaintenanceLedger();var service=new MemoryMaintenanceAccountingService(ledger);
        var event=new MemoryMaintenanceEvent("e1","candidate-1",MaintenanceEventType.REFRESHED,3.5,NOW,"audit:1");
        assertTrue(service.record(event));assertFalse(service.record(event));
        assertEquals(3.5,service.actualCost("candidate-1",NOW.minusSeconds(1),NOW.plusSeconds(1)),0.0001);
        assertThrows(IllegalStateException.class,()->service.record(new MemoryMaintenanceEvent("e1","candidate-1",MaintenanceEventType.REPAIRED,4,NOW,"audit:2")));
    }
}
