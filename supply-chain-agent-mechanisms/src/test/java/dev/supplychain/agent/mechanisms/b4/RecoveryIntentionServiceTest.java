package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryIntentionServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-a");
    private RecoveryObjective objective(){return new RecoveryObjective(new RecoveryObjectiveId("OBJ-1"),"Fulfill affected orders",Set.of(new CustomerOrderId("SO-1")),NOW.plusSeconds(86400),Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,RecoveryConstraint.NO_UNQUALIFIED_SUBSTITUTION),RecoveryObjectiveStatus.ACTIVE,0);}
    private RecoveryMethodReference method(String id){return new RecoveryMethodReference(id,"plan:"+id);}
    private RecoveryIntentionService service(){return new RecoveryIntentionService(new InMemoryRecoveryIntentionRepository());}
    private ObjectiveSatisfactionProof proof(String fingerprint,Instant at){return new ObjectiveSatisfactionProof(fingerprint,List.of(new EvidenceReference("erp","fulfillment-verified")),"operator-1","fulfillment-verifier-v1",at);}

    @Test void intentionCapturesImmutableObjectiveFingerprintAndConstraints(){
        var s=service();var i=s.create(objective(),SCOPE,"planner-owner",method("cot"),NOW);
        assertEquals(ObjectiveFingerprint.from(objective()).value(),i.objectiveFingerprint());assertEquals(objective().hardConstraints(),i.protectedConstraints());assertEquals(IntentionStatus.ACTIVE,i.status());
    }
    @Test void methodCannotChangeUntilReconsiderationIsRequested(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);var current=i;
        assertThrows(IllegalStateException.class,()->s.changeMethod(current.id(),current.version(),method("tot"),current.objectiveFingerprint(),NOW.plusSeconds(1)));
    }
    @Test void methodCanChangeAfterReconsiderationWithoutChangingObjective(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.requestReconsideration(i.id(),i.version(),"supplier capacity changed",NOW.plusSeconds(1));
        i=s.changeMethod(i.id(),i.version(),method("mcts"),i.objectiveFingerprint(),NOW.plusSeconds(2));assertEquals(IntentionStatus.ACTIVE,i.status());assertEquals("mcts",i.currentMethod().methodId());assertEquals(ObjectiveFingerprint.from(objective()).value(),i.objectiveFingerprint());
    }
    @Test void methodChangeCannotRewriteProtectedObjective(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.requestReconsideration(i.id(),i.version(),"new evidence",NOW.plusSeconds(1));var current=i;
        assertThrows(SecurityException.class,()->s.changeMethod(current.id(),current.version(),method("tot"),"b".repeat(64),NOW.plusSeconds(2)));
    }
    @Test void suspensionAndResumeRestorePriorLifecycleState(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.suspend(i.id(),i.version(),"awaiting supplier response",NOW.plusSeconds(1));assertEquals(IntentionStatus.SUSPENDED,i.status());
        i=s.resume(i.id(),i.version(),NOW.plusSeconds(2));assertEquals(IntentionStatus.ACTIVE,i.status());
        i=s.requestReconsideration(i.id(),i.version(),"plan threatened",NOW.plusSeconds(3));i=s.suspend(i.id(),i.version(),"awaiting evidence",NOW.plusSeconds(4));i=s.resume(i.id(),i.version(),NOW.plusSeconds(5));assertEquals(IntentionStatus.RECONSIDERATION_PENDING,i.status());
    }
    @Test void delegatedInvestigationReturnsToActiveOrReconsideration(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.delegate(i.id(),i.version(),"impasse-case-1",NOW.plusSeconds(1));
        i=s.returnFromDelegation(i.id(),i.version(),"impasse-case-1",DelegationReturn.METHOD_RECONSIDERATION_REQUIRED,NOW.plusSeconds(2));assertEquals(IntentionStatus.RECONSIDERATION_PENDING,i.status());
        i=s.changeMethod(i.id(),i.version(),method("tot"),i.objectiveFingerprint(),NOW.plusSeconds(3));i=s.delegate(i.id(),i.version(),"impasse-case-2",NOW.plusSeconds(4));
        i=s.returnFromDelegation(i.id(),i.version(),"impasse-case-2",DelegationReturn.NO_METHOD_CHANGE,NOW.plusSeconds(5));assertEquals(IntentionStatus.ACTIVE,i.status());
    }
    @Test void delegationReturnMustMatchActiveChild(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.delegate(i.id(),i.version(),"child-1",NOW.plusSeconds(1));var current=i;
        assertThrows(IllegalStateException.class,()->s.returnFromDelegation(current.id(),current.version(),"child-2",DelegationReturn.NO_METHOD_CHANGE,NOW.plusSeconds(2)));
    }
    @Test void completionRequiresMatchingEvidenceBackedObjectiveProof(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);var bad=proof("c".repeat(64),NOW.plusSeconds(1));var current=i;
        assertThrows(SecurityException.class,()->s.complete(current.id(),current.version(),bad,NOW.plusSeconds(2)));
        var good=proof(i.objectiveFingerprint(),NOW.plusSeconds(1));i=s.complete(i.id(),i.version(),good,NOW.plusSeconds(2));assertEquals(IntentionStatus.COMPLETED,i.status());
    }
    @Test void completionProofCannotBeFutureDated(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);var future=proof(i.objectiveFingerprint(),NOW.plusSeconds(10));var current=i;
        assertThrows(IllegalArgumentException.class,()->s.complete(current.id(),current.version(),future,NOW.plusSeconds(2)));
    }
    @Test void staleVersionCannotOverwriteConcurrentIntentionUpdate(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);var oldVersion=i.version();s.requestReconsideration(i.id(),oldVersion,"trigger",NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class,()->s.suspend(i.id(),oldVersion,"stale caller",NOW.plusSeconds(2)));
    }
    @Test void objectiveFingerprintIsStableAcrossUnorderedSetsAndSensitiveToDelimitedText(){
        var first=objective();
        var reordered=new RecoveryObjective(first.id(),first.description(),Set.of(new CustomerOrderId("SO-1")),first.targetFulfillmentAt(),
                Set.of(RecoveryConstraint.NO_UNQUALIFIED_SUBSTITUTION,RecoveryConstraint.NO_OVER_ALLOCATION),first.status(),first.version());
        assertEquals(ObjectiveFingerprint.from(first),ObjectiveFingerprint.from(reordered));
        var changed=new RecoveryObjective(first.id(),first.description()+"|O:SO-2|C:APPROVAL",first.affectedOrders(),first.targetFulfillmentAt(),
                first.hardConstraints(),first.status(),first.version());
        assertTrue(!ObjectiveFingerprint.from(first).equals(ObjectiveFingerprint.from(changed)));
    }

    @Test void terminationCannotMoveIntentionTimeBackwards(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);
        i=s.requestReconsideration(i.id(),i.version(),"supplier response pending",NOW.plusSeconds(10));
        var current=i;
        assertThrows(IllegalArgumentException.class,()->s.terminate(current.id(),current.version(),"operator","cancelled",NOW.plusSeconds(5)));
    }


    @Test void intentionPersistsRationalePlanContinuationTriggersAndAuthorityContract(){
        var policy = new IntentionAuthorityPolicy("owner", "runtime-governor",
                Set.of("owner", "runtime-governor"), Set.of("owner", "operator"), true);
        var s = service();
        var i = s.create(new RecoveryIntentionId("i-b4-contract"), objective(), SCOPE, "owner", method("cot"),
                "Preserve affected customer orders while restoring supplier capacity",
                ContinuationCondition.untilDeadline(NOW.plusSeconds(3600)),
                Set.of(ReconsiderationTrigger.NEW_VERIFIED_EVIDENCE, ReconsiderationTrigger.METHOD_FAILURE),
                policy, NOW);
        assertEquals("Preserve affected customer orders while restoring supplier capacity", i.rationale());
        assertEquals("plan:cot", i.activePlanReference());
        assertEquals(ContinuationCondition.Kind.UNTIL_DEADLINE, i.continuationCondition().kind());
        assertTrue(i.reconsiderationTriggers().contains(ReconsiderationTrigger.METHOD_FAILURE));
        assertEquals(policy, i.authorityPolicy());
        assertFalse(i.continuationExpired(NOW.plusSeconds(5), false, false));
        assertTrue(i.continuationExpired(NOW.plusSeconds(3600), false, false));
    }

    @Test void typedReconsiderationRequiresAnAllowedTriggerAndAuthorizedActor(){
        var policy = new IntentionAuthorityPolicy("owner", "runtime-governor",
                Set.of("owner", "runtime-governor"), Set.of("owner", "operator"), true);
        var s = service();
        var i = s.create(new RecoveryIntentionId("i-b4-trigger"), objective(), SCOPE, "owner", method("cot"),
                "Protect objective", ContinuationCondition.untilObjectiveVerified(),
                Set.of(ReconsiderationTrigger.METHOD_FAILURE), policy, NOW);
        var current = i;
        assertThrows(SecurityException.class, () -> s.requestReconsideration(current.id(), current.version(),
                ReconsiderationTrigger.OPERATOR_REQUEST, "runtime-governor", "manual", NOW.plusSeconds(1)));
        assertThrows(SecurityException.class, () -> s.requestReconsideration(current.id(), current.version(),
                ReconsiderationTrigger.METHOD_FAILURE, "untrusted-model", "method failed", NOW.plusSeconds(1)));
        i = s.requestReconsideration(i.id(), i.version(), ReconsiderationTrigger.METHOD_FAILURE,
                "runtime-governor", "planner failed", NOW.plusSeconds(1));
        assertEquals(IntentionStatus.RECONSIDERATION_PENDING, i.status());
    }

    @Test void methodChangeUpdatesActivePlanReferenceButNeverObjectiveFingerprint(){
        var s = service();
        var i = s.create(objective(), SCOPE, "owner", method("cot"), NOW);
        String objectiveFingerprint = i.objectiveFingerprint();
        i = s.requestReconsideration(i.id(), i.version(), "new evidence", NOW.plusSeconds(1));
        i = s.changeMethod(i.id(), i.version(), method("tot"), objectiveFingerprint, NOW.plusSeconds(2));
        assertEquals("plan:tot", i.activePlanReference());
        assertEquals(objectiveFingerprint, i.objectiveFingerprint());
    }

    @Test void terminationRecordsTypedReasonAndAuthenticatedActor(){
        var s = service();
        var i = s.create(objective(), SCOPE, "owner", method("cot"), NOW);
        i = s.terminate(i.id(), i.version(), "operator", "safety review cancelled the recovery",
                IntentionTerminationReason.SAFETY_STOP, NOW.plusSeconds(1));
        assertEquals(IntentionStatus.TERMINATED, i.status());
        assertEquals(IntentionTerminationReason.SAFETY_STOP, i.terminationReason());
        assertEquals("operator", i.terminatedBy());
    }

    @Test void terminalIntentionCannotBeResumedOrChanged(){
        var s=service();var i=s.create(objective(),SCOPE,"owner",method("cot"),NOW);i=s.terminate(i.id(),i.version(),"operator","cancelled",NOW.plusSeconds(1));var terminal=i;
        assertEquals(IntentionStatus.TERMINATED,terminal.status());assertThrows(IllegalStateException.class,()->s.requestReconsideration(terminal.id(),terminal.version(),"later",NOW.plusSeconds(2)));
    }
}
