package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryProcedureDraftServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-20T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-r2");
    private static final ParentContextSnapshot PARENT=new ParentContextSnapshot(SCOPE,"request-r2",8,"b".repeat(64));

    private ImpasseResolutionService resolvedCase() {
        var service=new ImpasseResolutionService(new DeterministicImpasseDetector(),new InMemoryImpasseCaseRepository());
        var signal=new ImpasseSignal(ImpasseType.CONFLICTING_EVIDENCE,ImpasseSeverity.HIGH,"inventory:sku-1","Inventory state conflicts",
                List.of(new EvidenceReference("erp","state-1")),NOW);
        var c=service.open("case-r2",SCOPE,"execution-r2",PARENT,signal,
                Set.of(Capability.MEMORY_RETRIEVE,Capability.REASONING_INVOKE,Capability.INVENTORY_READ,Capability.INVENTORY_RESERVE),
                Set.of(Capability.MEMORY_RETRIEVE,Capability.INVENTORY_READ),NOW,NOW.plusSeconds(600),3);
        c=service.start(c.id(),c.version(),NOW.plusSeconds(1));
        var result=new ChildResolutionResult(c.id(),PARENT.requestId(),PARENT.workingMemoryVersion(),PARENT.objectiveFingerprint(),
                ResolutionDisposition.EVIDENCE_CONFIRMED,List.of(new ResolutionFinding("Inventory snapshot reconciled",
                List.of(new EvidenceReference("wms","reconciliation-7"),new EvidenceReference("erp","state-1")))),List.of(),NOW.plusSeconds(10));
        service.resolve(c.id(),c.version(),result,PARENT,NOW.plusSeconds(11));
        return service;
    }

    private ActionRegistry actions() {
        ActionDescriptor descriptor=new ActionDescriptor(new ActionId("inventory.reserve"),"Reserve inventory","Registered inventory reservation",
                ActionCategory.GROUNDING,ActionEffect.DOMAIN_WRITE,Capability.INVENTORY_RESERVE,true);
        return new ActionRegistry().register(new ActionHandler<String,String>() {
            public ActionDescriptor descriptor(){return descriptor;}
            public Class<String> inputType(){return String.class;}
            public Class<String> outputType(){return String.class;}
            public String execute(ActionContext context,String input){throw new AssertionError("draft creation must never execute actions");}
        });
    }

    private RecoveryProcedureDraftService service(InMemoryRecoveryProcedureDraftRepository repository,boolean authorized) {
        return new RecoveryProcedureDraftService(resolvedCase(),actions(),repository,
                (scope,evidence,at)->SCOPE.equals(scope)&&evidence.stream().allMatch(e->!e.evidenceId().isBlank()),
                (draft,approver,reference,at)->authorized&&SCOPE.equals(draft.scopeId())
                        &&draft.actions().stream().allMatch(a->a.actionId().equals("inventory.reserve"))
                        &&"ops-lead".equals(approver)&&"approval-1".equals(reference));
    }
    private RecoveryProcedureDraftRequest request() {
        return new RecoveryProcedureDraftRequest("draft-r2","case-r2",2,SCOPE,PARENT,"inventory.shortage",
                List.of(new ActionId("inventory.reserve")),List.of("inventory.available"),List.of("reservation.confirmed"),
                List.of("reservation.rejected","inventory.version.changed"),NOW.plusSeconds(300));
    }

    @Test void createsAnEvidenceBoundNonExecutableDraftFromResolvedCaseAndRegisteredActions() {
        var repository=new InMemoryRecoveryProcedureDraftRepository();var service=service(repository,true);
        var draft=service.createDraft(request(),NOW.plusSeconds(20));
        assertEquals(RecoveryProcedureStatus.DRAFT,draft.status());
        assertEquals("inventory.reserve",draft.actions().getFirst().actionId());
        assertEquals(2,draft.evidence().size());
        assertEquals("inventory.shortage",draft.conditionKey());
        assertNull(draft.approvedAt());
    }

    @Test void createIsIdempotentForTheExactSourceAndRejectsConflictingReuse() {
        var service=service(new InMemoryRecoveryProcedureDraftRepository(),true);
        var first=service.createDraft(request(),NOW.plusSeconds(20));
        assertEquals(first,service.createDraft(request(),NOW.plusSeconds(21)));
        var conflicting=new RecoveryProcedureDraftRequest("draft-r2","case-r2",2,SCOPE,PARENT,"inventory.surplus",
                List.of(new ActionId("inventory.reserve")),List.of("inventory.available"),List.of("reservation.confirmed"),
                List.of("reservation.rejected"),NOW.plusSeconds(300));
        assertThrows(SecurityException.class,()->service.createDraft(conflicting,NOW.plusSeconds(20)));
    }

    @Test void unknownOrUnregisteredActionsCannotBecomeProcedureSteps() {
        var service=service(new InMemoryRecoveryProcedureDraftRepository(),true);
        var bad=new RecoveryProcedureDraftRequest("draft-bad","case-r2",2,SCOPE,PARENT,"inventory.shortage",
                List.of(new ActionId("llm.generated.instruction")),List.of("inventory.available"),List.of("reservation.confirmed"),
                List.of("reservation.rejected"),NOW.plusSeconds(300));
        assertThrows(IllegalArgumentException.class,()->service.createDraft(bad,NOW.plusSeconds(20)));
    }

    @Test void freeFormNarrativeIsNotAcceptedAsAnExecutableConditionKey() {
        assertThrows(IllegalArgumentException.class,()->new RecoveryProcedureDraftRequest("d","case-r2",2,SCOPE,PARENT,
                "then call the ERP and reserve everything",List.of(new ActionId("inventory.reserve")),List.of("p"),List.of("q"),List.of("f"),NOW.plusSeconds(30)));
    }

    @Test void approvalRequiresCurrentParentEvidenceAndTrustedOperatorAuthority() {
        var service=service(new InMemoryRecoveryProcedureDraftRepository(),true);
        var draft=service.createDraft(request(),NOW.plusSeconds(20));
        var approved=service.approve(draft.id(),draft.version(),PARENT,
                new RecoveryProcedureApproval("ops-lead","approval-1",NOW.plusSeconds(30)),NOW.plusSeconds(31));
        assertEquals(RecoveryProcedureStatus.APPROVED,approved.status());
        assertEquals(1,approved.version());
        assertEquals("ops-lead",approved.approvedBy());
        assertThrows(IllegalStateException.class,()->service.approve(draft.id(),draft.version(),PARENT,
                new RecoveryProcedureApproval("ops-lead","approval-1",NOW.plusSeconds(30)),NOW.plusSeconds(32)));
    }

    @Test void unauthorizedApprovalFailsClosed() {
        var service=service(new InMemoryRecoveryProcedureDraftRepository(),false);
        var draft=service.createDraft(request(),NOW.plusSeconds(20));
        assertThrows(SecurityException.class,()->service.approve(draft.id(),draft.version(),PARENT,
                new RecoveryProcedureApproval("untrusted","approval-1",NOW.plusSeconds(30)),NOW.plusSeconds(31)));
    }

    @Test void approvalCannotCrossParentVersionOrObjectiveChange() {
        var service=service(new InMemoryRecoveryProcedureDraftRepository(),true);
        var draft=service.createDraft(request(),NOW.plusSeconds(20));
        var changed=new ParentContextSnapshot(SCOPE,PARENT.requestId(),9,PARENT.objectiveFingerprint());
        assertThrows(SecurityException.class,()->service.approve(draft.id(),draft.version(),changed,
                new RecoveryProcedureApproval("ops-lead","approval-1",NOW.plusSeconds(30)),NOW.plusSeconds(31)));
    }
}
