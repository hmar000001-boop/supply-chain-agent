package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceAdmissionServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-a");
    private WorkspaceCandidate candidate(String id,double utility,int cost,Set<String> tags,Set<String> conflicts,boolean mandatory){
        return new WorkspaceCandidate(id,SCOPE,WorkspaceCandidateKind.OBSERVATION,utility,1.0,0.0,cost,tags,conflicts,mandatory,List.of());
    }
    private WorkspaceAdmissionOutcome admit(WorkspaceRepository repo,int capacity,int max,Set<String> required,Map<String,Double> coverage,List<WorkspaceCandidate> candidates){
        var request=new WorkspaceAdmissionRequest("cycle-1",SCOPE,capacity,max,required,coverage,new WorkspaceScoringPolicy(5,1),candidates,NOW);
        return new WorkspaceAdmissionService(repo).admit(request);
    }

    @Test void mandatoryContentIsAdmittedBeforeOptionalContent(){
        var repo=new InMemoryWorkspaceRepository();var required=candidate("constraint",1,2,Set.of("constraints"),Set.of(),true);var optional=candidate("plan",100,2,Set.of("plan"),Set.of(),false);
        var result=admit(repo,2,5,Set.of(),Map.of(),List.of(optional,required));
        assertEquals(WorkspaceAdmissionStatus.ADMITTED,result.status());assertEquals(List.of("constraint"),result.snapshot().entries().stream().map(e->e.candidate().id()).toList());
    }
    @Test void mandatoryContentExceedingCapacityFailsWithoutPartialSnapshot(){
        var repo=new InMemoryWorkspaceRepository();var a=candidate("a",1,2,Set.of(),Set.of(),true);var b=candidate("b",1,2,Set.of(),Set.of(),true);
        var result=admit(repo,3,5,Set.of(),Map.of(),List.of(a,b));assertEquals(WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY,result.status());assertNull(result.snapshot());
    }
    @Test void mandatoryContentExceedingMaximumCountFails(){
        var repo=new InMemoryWorkspaceRepository();var a=candidate("a",1,1,Set.of(),Set.of(),true);var b=candidate("b",1,1,Set.of(),Set.of(),true);
        var result=admit(repo,4,1,Set.of(),Map.of(),List.of(a,b));assertEquals(WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY,result.status());
    }
    @Test void conflictingCandidatesAreNeverAdmittedTogether(){
        var repo=new InMemoryWorkspaceRepository();var a=candidate("a",10,1,Set.of(),Set.of("b"),false);var b=candidate("b",9,1,Set.of(),Set.of(),false);
        var result=admit(repo,2,5,Set.of(),Map.of(),List.of(a,b));assertEquals(1,result.snapshot().entries().size());assertEquals("a",result.snapshot().entries().getFirst().candidate().id());
    }
    @Test void complementaryCoverageCanMakeARequiredCandidateWorthAdmitting(){
        var repo=new InMemoryWorkspaceRepository();var high=candidate("high",5,1,Set.of(),Set.of(),false);var coverage=candidate("quality",0.2,1,Set.of("quality-status"),Set.of(),false);
        var result=admit(repo,2,2,Set.of("quality-status"),Map.of("quality-status",10.0),List.of(high,coverage));
        assertEquals(WorkspaceAdmissionStatus.ADMITTED,result.status());assertTrue(result.snapshot().coveredTags().contains("quality-status"));assertEquals(2,result.snapshot().usedUnits());
    }
    @Test void complementaryCandidatesAreRecordedAsAnExplicitWorkspaceCoalition(){
        var repo=new InMemoryWorkspaceRepository();
        var supplier=candidate("supplier",3,1,Set.of("supplier-capacity"),Set.of(),false);
        var inventory=candidate("inventory",3,1,Set.of("inventory-availability"),Set.of(),false);
        var result=admit(repo,2,2,Set.of("supplier-capacity","inventory-availability"),Map.of("supplier-capacity",1.0,"inventory-availability",1.0),List.of(supplier,inventory));
        assertEquals(1,result.snapshot().coalitions().size());
        assertEquals(Set.of("supplier","inventory"),Set.copyOf(result.snapshot().coalitions().getFirst().candidateIds()));
        assertEquals(Set.of("supplier-capacity","inventory-availability"),result.snapshot().coalitions().getFirst().jointCoverageTags());
    }
    @Test void alternativeCoalitionsCompeteAsFirstClassOptionsAndRecordTheWinner() {
        var repo = new InMemoryWorkspaceRepository();
        var anchor = candidate("anchor", 8, 1, Set.of("anchor.signal"), Set.of(), false);
        var supplier = candidate("supplier", 1, 1, Set.of("supplier.capacity"), Set.of(), false);
        var inventory = candidate("inventory", 1, 1, Set.of("inventory.available"), Set.of(), false);
        var request = new WorkspaceAdmissionRequest("cycle-coalition", SCOPE, 2, 2,
                Set.of("supplier.capacity", "inventory.available"),
                Map.of("supplier.capacity", 10.0, "inventory.available", 10.0, "anchor.signal", 1.0),
                new WorkspaceScoringPolicy(0, 1), List.of(anchor, supplier, inventory), NOW);
        var result = new WorkspaceAdmissionService(repo).admit(request);

        assertEquals(List.of("inventory", "supplier"), result.snapshot().entries().stream()
                .map(entry -> entry.candidate().id()).sorted().toList());
        assertEquals(3, result.snapshot().evaluatedCoalitionProposals().size(),
                "each feasible pair is represented as a competing alternative");
        var selectedProposal = result.snapshot().evaluatedCoalitionProposals().stream().filter(WorkspaceCoalitionProposal::selected).findFirst().orElseThrow();
        assertEquals(List.of("inventory", "supplier"), selectedProposal.candidateIds());
        assertEquals(Set.of("supplier.capacity", "inventory.available"), selectedProposal.newlyCoveredTags());
        assertEquals(1, result.snapshot().coalitions().size());
        assertFalse(result.snapshot().coalitionSearchTruncated());
        var snapshot = result.snapshot();
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceSnapshot(snapshot.id(), snapshot.cycleId(),
                snapshot.scopeId(), snapshot.capacityUnits(), snapshot.usedUnits(), snapshot.entries(), List.of(),
                snapshot.coveredTags(), snapshot.status(), snapshot.version(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.experimentUnitFingerprint(), snapshot.evaluatedCoalitionProposals(), snapshot.coalitionSearchTruncated()),
                "the durable competition trace cannot claim a selected coalition absent from the admitted snapshot");
    }

    @Test void incompleteCoalitionEnumerationFallsBackToSingletonAdmissionAndReportsTruncation() {
        var repo = new InMemoryWorkspaceRepository();
        var a = candidate("a", 5, 1, Set.of("a.signal"), Set.of(), false);
        var b = candidate("b", 4, 1, Set.of("b.signal"), Set.of(), false);
        var c = candidate("c", 3, 1, Set.of("c.signal"), Set.of(), false);
        var request = new WorkspaceAdmissionRequest("cycle-bounded", SCOPE, 2, 2,
                Set.of(), Map.of(), new WorkspaceScoringPolicy(0, 1), List.of(c, b, a), NOW);
        var service = new WorkspaceAdmissionService(repo,
                dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment.neutral(),
                new WorkspaceCoalitionCompetitionPolicy(3, 1));
        var result = service.admit(request);

        assertTrue(result.snapshot().coalitionSearchTruncated());
        assertTrue(result.snapshot().coalitions().isEmpty(), "partial coalition search must not select from an incomplete option set");
        assertTrue(result.explanation().contains("singleton fallback"));
        assertTrue(result.snapshot().evaluatedCoalitionProposals().stream().noneMatch(WorkspaceCoalitionProposal::selected));
        assertEquals(List.of("a", "b"), result.snapshot().entries().stream()
                .map(entry -> entry.candidate().id()).sorted().toList());
    }

    @Test void coalitionCompetitionIsDeterministicAcrossCandidateInputOrder() {
        var a = candidate("a", 1, 1, Set.of("a.signal"), Set.of(), false);
        var b = candidate("b", 1, 1, Set.of("b.signal"), Set.of(), false);
        var c = candidate("c", 1, 1, Set.of("c.signal"), Set.of(), false);
        var policy = new WorkspaceScoringPolicy(0, 1);
        var firstRequest = new WorkspaceAdmissionRequest("cycle-deterministic", SCOPE, 2, 2,
                Set.of(), Map.of(), policy, List.of(a, b, c), NOW);
        var secondRequest = new WorkspaceAdmissionRequest("cycle-deterministic", SCOPE, 2, 2,
                Set.of(), Map.of(), policy, List.of(c, a, b), NOW);
        var first = new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(firstRequest).snapshot();
        var second = new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(secondRequest).snapshot();
        assertEquals(first.entries().stream().map(e -> e.candidate().id()).toList(),
                second.entries().stream().map(e -> e.candidate().id()).toList());
        assertEquals(first.evaluatedCoalitionProposals().stream().map(WorkspaceCoalitionProposal::id).toList(),
                second.evaluatedCoalitionProposals().stream().map(WorkspaceCoalitionProposal::id).toList());
        assertEquals(first.evaluatedCoalitionProposals().stream().filter(WorkspaceCoalitionProposal::selected)
                        .map(WorkspaceCoalitionProposal::candidateIds).toList(),
                second.evaluatedCoalitionProposals().stream().filter(WorkspaceCoalitionProposal::selected)
                        .map(WorkspaceCoalitionProposal::candidateIds).toList());
    }

    @Test void coalitionGenerationNeverCombinesConflictingCandidates() {
        var repo = new InMemoryWorkspaceRepository();
        var a = candidate("a", 5, 1, Set.of("a.signal"), Set.of("b"), false);
        var b = candidate("b", 5, 1, Set.of("b.signal"), Set.of(), false);
        var request = new WorkspaceAdmissionRequest("cycle-conflict", SCOPE, 2, 2, Set.of(), Map.of(),
                new WorkspaceScoringPolicy(0, 1), List.of(a, b), NOW);
        var result = new WorkspaceAdmissionService(repo).admit(request);
        assertTrue(result.snapshot().evaluatedCoalitionProposals().isEmpty());
        assertTrue(result.snapshot().coalitions().isEmpty());
    }

    @Test void reportsIncompleteCoverageWhenCapacityCannotCoverAllRequiredTags(){
        var repo=new InMemoryWorkspaceRepository();var a=candidate("a",3,1,Set.of("inventory"),Set.of(),false);
        var result=admit(repo,1,2,Set.of("inventory","supplier"),Map.of("inventory",2.0,"supplier",2.0),List.of(a));
        assertEquals(WorkspaceAdmissionStatus.INCOMPLETE_COVERAGE,result.status());assertEquals(List.of("supplier"),result.missingCoverage());
    }
    @Test void candidateScopeMustMatchWorkspaceScope(){
        var foreign=new WorkspaceCandidate("foreign",new MemoryScopeId("tenant-b"),WorkspaceCandidateKind.MEMORY,1,1,0,1,Set.of(),Set.of(),false,List.of());
        assertThrows(SecurityException.class,()->new WorkspaceAdmissionRequest("cycle",SCOPE,2,2,Set.of(),Map.of(),new WorkspaceScoringPolicy(1,1),List.of(foreign),NOW));
    }
    @Test void workspaceCanBeClosedWithOptimisticVersion(){
        var repo=new InMemoryWorkspaceRepository();var outcome=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",1,1,Set.of(),Set.of(),false)));
        var service=new WorkspaceAdmissionService(repo);var closed=service.close(outcome.snapshot().id(),SCOPE,0,NOW.plusSeconds(1));assertEquals(WorkspaceStatus.CLOSED,closed.status());
        assertThrows(IllegalStateException.class,()->service.close(closed.id(),SCOPE,0,NOW.plusSeconds(2)));
    }
    @Test void broadcastReturnsReceiptForEachRegisteredAndUnknownConsumer(){
        var repo=new InMemoryWorkspaceRepository();var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",2,1,Set.of(),Set.of(),false))).snapshot();
        WorkspaceConsumer ok=message->new WorkspaceConsumerReceipt("inventory",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,List.of("a"),message.broadcastAt(),"received");
        var broadcaster=new WorkspaceBroadcastService(Map.of("inventory",ok));var report=broadcaster.broadcast(snapshot,SCOPE,Set.of("inventory","unknown"),NOW.plusSeconds(1));
        assertEquals(2,report.receipts().size());assertEquals(ConsumerReceiptStatus.ACCEPTED,report.receipts().get(0).status());assertEquals(ConsumerReceiptStatus.REJECTED,report.receipts().get(1).status());assertFalse(report.allAccepted());
    }
    @Test void broadcastRejectsConsumerReceiptForUnadmittedCandidate(){
        var repo=new InMemoryWorkspaceRepository();var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",2,1,Set.of(),Set.of(),false))).snapshot();
        WorkspaceConsumer malicious=message->new WorkspaceConsumerReceipt("consumer",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,List.of("not-in-workspace"),message.broadcastAt(),"bad receipt");
        var report=new WorkspaceBroadcastService(Map.of("consumer",malicious)).broadcast(snapshot,SCOPE,Set.of("consumer"),NOW.plusSeconds(1));
        assertEquals(ConsumerReceiptStatus.FAILED,report.receipts().getFirst().status());
    }
    @Test void workspaceCannotBeClosedFromAnotherScope(){
        var repo=new InMemoryWorkspaceRepository();var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",1,1,Set.of(),Set.of(),false))).snapshot();
        var service=new WorkspaceAdmissionService(repo);
        assertThrows(SecurityException.class,()->service.close(snapshot.id(),new MemoryScopeId("tenant-b"),0,NOW.plusSeconds(1)));
    }
    @Test void workspaceCannotBeBroadcastAcrossScopes(){
        var repo=new InMemoryWorkspaceRepository();var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",1,1,Set.of(),Set.of(),false))).snapshot();
        var service=new WorkspaceBroadcastService(Map.of("consumer",message->new WorkspaceConsumerReceipt("consumer",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,List.of("a"),message.broadcastAt(),"ok")));
        assertThrows(SecurityException.class,()->service.broadcast(snapshot,new MemoryScopeId("tenant-b"),Set.of("consumer"),NOW.plusSeconds(1)));
    }
    @Test void mandatoryCapacityOverflowIsReportedAsInsufficientWithoutCreatingSnapshot(){
        var repo=new InMemoryWorkspaceRepository();
        var small=candidate("small",1,1,Set.of(),Set.of(),true);
        var huge=candidate("huge",1,Integer.MAX_VALUE,Set.of(),Set.of(),true);
        var result=admit(repo,100,5,Set.of(),Map.of(),List.of(small,huge));
        assertEquals(WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY,result.status());
        assertNull(result.snapshot());
    }

    @Test void closedWorkspaceCannotBeBroadcastAgain(){
        var repo=new InMemoryWorkspaceRepository();
        var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",2,1,Set.of(),Set.of(),false))).snapshot();
        var closed=new WorkspaceAdmissionService(repo).close(snapshot.id(),SCOPE,0,NOW.plusSeconds(1));
        var broadcaster=new WorkspaceBroadcastService(Map.of("consumer",message->new WorkspaceConsumerReceipt("consumer",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,List.of("a"),message.broadcastAt(),"ok")));
        assertThrows(IllegalStateException.class,()->broadcaster.broadcast(closed,SCOPE,Set.of("consumer"),NOW.plusSeconds(2)));
    }

    @Test void broadcastCapturesConsumerFailureInsteadOfLosingOtherReceipts(){
        var repo=new InMemoryWorkspaceRepository();var snapshot=admit(repo,2,2,Set.of(),Map.of(),List.of(candidate("a",2,1,Set.of(),Set.of(),false))).snapshot();
        WorkspaceConsumer bad=message->{throw new IllegalStateException("consumer down");};
        var report=new WorkspaceBroadcastService(Map.of("bad",bad)).broadcast(snapshot,SCOPE,Set.of("bad"),NOW.plusSeconds(1));assertEquals(ConsumerReceiptStatus.FAILED,report.receipts().getFirst().status());
    }
}
