package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImpasseResolutionServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-a");
    private static final ParentContextSnapshot PARENT=new ParentContextSnapshot(SCOPE,"request-1",4,"a".repeat(64));
    private static final Set<Capability> PARENT_CAPS=Set.of(Capability.MEMORY_RETRIEVE,Capability.REASONING_INVOKE,Capability.INVENTORY_READ,Capability.INVENTORY_RESERVE);
    private static final Set<Capability> CHILD_CAPS=Set.of(Capability.MEMORY_RETRIEVE,Capability.REASONING_INVOKE,Capability.INVENTORY_READ);
    private static ImpasseSignal signal(ImpasseSeverity severity){return new ImpasseSignal(ImpasseType.CONFLICTING_EVIDENCE,severity,"lot:1","Inventory observations conflict",List.of(new EvidenceReference("erp","e1")),NOW);}
    private ImpasseResolutionService service(){return new ImpasseResolutionService(new DeterministicImpasseDetector(),new InMemoryImpasseCaseRepository());}
    private ImpasseResolutionCase open(ImpasseResolutionService s){return s.open("case-1",SCOPE,"execution-1",PARENT,signal(ImpasseSeverity.HIGH),PARENT_CAPS,CHILD_CAPS,NOW,NOW.plusSeconds(600),3);}
    private ChildResolutionResult result(){return new ChildResolutionResult("case-1","request-1",4,"a".repeat(64),ResolutionDisposition.EVIDENCE_CONFIRMED,
            List.of(new ResolutionFinding("WMS count is current",List.of(new EvidenceReference("wms","report-7")))),List.of(),NOW.plusSeconds(30));}

    @Test void detectorConsolidatesDuplicateSignalsAndKeepsHighestSeverity(){
        var detector=new DeterministicImpasseDetector();var low=signal(ImpasseSeverity.LOW);var high=new ImpasseSignal(low.type(),ImpasseSeverity.CRITICAL,low.subjectReference(),"Critical conflict",List.of(new EvidenceReference("carrier","e2")),NOW.plusSeconds(1));
        var result=detector.consolidate(List.of(low,high));assertEquals(1,result.size());assertEquals(ImpasseSeverity.CRITICAL,result.getFirst().severity());assertEquals(2,result.getFirst().evidence().size());
    }
    @Test void childCapabilitiesMustBeSubsetOfParent(){
        var s=service();assertThrows(SecurityException.class,()->s.open("c",SCOPE,"x",PARENT,signal(ImpasseSeverity.HIGH),PARENT_CAPS,
                Set.of(Capability.INVENTORY_RESERVE),NOW,NOW.plusSeconds(10),1));
    }
    @Test void childCapabilitiesCannotContainAnyWriteCapability(){
        assertThrows(SecurityException.class,()->ImpasseResolutionCase.open("c",SCOPE,"x",PARENT,signal(ImpasseSeverity.HIGH),PARENT_CAPS,
                Set.of(Capability.INVENTORY_RESERVE),NOW,NOW.plusSeconds(10),1));
    }
    @Test void childScopeMustMatchParentScope(){
        assertThrows(SecurityException.class,()->ImpasseResolutionCase.open("c",new MemoryScopeId("tenant-b"),"x",PARENT,signal(ImpasseSeverity.HIGH),PARENT_CAPS,CHILD_CAPS,NOW,NOW.plusSeconds(10),1));
    }
    @Test void childActionBudgetAndCapabilityAreEnforced(){
        var s=service();var c=open(s);c=s.start(c.id(),c.version(),NOW.plusSeconds(1));
        var used=s.recordChildAction(c.id(),c.version(),Capability.INVENTORY_READ,NOW.plusSeconds(2));
        assertEquals(1,used.childActionsUsed());
        var current=used;
        assertThrows(SecurityException.class,()->s.recordChildAction(current.id(),current.version(),Capability.INVENTORY_RESERVE,NOW.plusSeconds(3)));
    }
    @Test void childCannotExceedActionBudget(){
        var s=service();var c=s.open("case-1",SCOPE,"execution-1",PARENT,signal(ImpasseSeverity.HIGH),PARENT_CAPS,CHILD_CAPS,NOW,NOW.plusSeconds(60),1);
        c=s.start(c.id(),c.version(),NOW.plusSeconds(1));c=s.recordChildAction(c.id(),c.version(),Capability.INVENTORY_READ,NOW.plusSeconds(2));var current=c;
        assertThrows(IllegalStateException.class,()->s.recordChildAction(current.id(),current.version(),Capability.MEMORY_RETRIEVE,NOW.plusSeconds(3)));
    }
    @Test void evidenceBackedChildResultCompletesOnlyTheSubstate(){
        var s=service();var c=open(s);c=s.start(c.id(),c.version(),NOW.plusSeconds(1));c=s.resolve(c.id(),c.version(),result(),PARENT,NOW.plusSeconds(31));
        assertEquals(ImpasseCaseStatus.RESOLVED,c.status());assertEquals(ResolutionDisposition.EVIDENCE_CONFIRMED,c.result().disposition());assertEquals(4,c.parentWorkingMemoryVersion());
    }
    @Test void staleParentVersionRejectsChildResult(){
        var s=service();var c=open(s);c=s.start(c.id(),c.version(),NOW.plusSeconds(1));
        var changed=new ParentContextSnapshot(SCOPE,"request-1",5,"a".repeat(64));var current=c;
        assertThrows(IllegalStateException.class,()->s.resolve(current.id(),current.version(),result(),changed,NOW.plusSeconds(31)));
    }
    @Test void unresolvedChildEscalatesInsteadOfClaimingResolution(){
        var s=service();var c=open(s);c=s.start(c.id(),c.version(),NOW.plusSeconds(1));
        var unresolved=new ChildResolutionResult("case-1","request-1",4,"a".repeat(64),ResolutionDisposition.UNRESOLVED,List.of(),List.of("supplier has not replied"),NOW.plusSeconds(2));
        c=s.resolve(c.id(),c.version(),unresolved,PARENT,NOW.plusSeconds(3));assertEquals(ImpasseCaseStatus.ESCALATED,c.status());assertEquals(ResolutionDisposition.UNRESOLVED,c.result().disposition());
    }
    @Test void caseExpiresAtDeadlineAndCannotContinue(){
        var s=service();var c=open(s);c=s.start(c.id(),c.version(),NOW.plusSeconds(1));c=s.expire(c.id(),c.version(),NOW.plusSeconds(600));assertEquals(ImpasseCaseStatus.EXPIRED,c.status());
        var expired=c;assertThrows(IllegalStateException.class,()->s.recordChildAction(expired.id(),expired.version(),Capability.INVENTORY_READ,NOW.plusSeconds(601)));
    }
    @Test void optimisticVersionConflictIsRejected(){
        var s=service();var c=open(s);s.start(c.id(),c.version(),NOW.plusSeconds(1));assertThrows(IllegalStateException.class,()->s.start(c.id(),c.version(),NOW.plusSeconds(2)));
    }
}
