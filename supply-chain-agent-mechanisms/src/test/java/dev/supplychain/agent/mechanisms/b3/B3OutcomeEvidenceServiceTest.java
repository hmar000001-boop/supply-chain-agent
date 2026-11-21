package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B3OutcomeEvidenceServiceTest {
    private static final Instant NOW=Instant.parse("2026-11-20T12:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-r2");
    private static String sha(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private WorkspaceSnapshot snapshot() {
        var candidate=new WorkspaceCandidate("candidate-1",SCOPE,WorkspaceCandidateKind.MEMORY,3.0,0.9,0.1,2,
                Set.of("inventory"),Set.of(),false,List.of(new EvidenceReference("wms","snapshot-1")));
        var outcome=new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(
                new WorkspaceAdmissionRequest("cycle-r2",SCOPE,4,2,Set.of(),Map.of(),new WorkspaceScoringPolicy(1,1),List.of(candidate),NOW)
                        .withExperimentUnitFingerprint(sha("scenario-input:inventory-shortage-v1")));
        return outcome.snapshot();
    }

    @Test void broadcastPersistsReceiptButDoesNotTreatReceiptAsAUsefulOutcome() {
        var repository=new InMemoryB3OutcomeEvidenceRepository();
        var service=new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->true,"b3-admission-v1","outcome-verifier-v1");
        var snapshot=snapshot();
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                List.of("candidate-1"),message.broadcastAt(),"consumed");
        new WorkspaceBroadcastService(Map.of("planner",consumer),service).broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1));
        String receiptId=B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner");
        assertTrue(repository.findReceipt(SCOPE,receiptId).isPresent());
        assertTrue(repository.findOutcomes(SCOPE,100).isEmpty());
    }

    @Test void verifiedOutcomeMustReferenceAnAcceptedConsumedCandidateAndVerifiedSourceEvidence() {
        var repository=new InMemoryB3OutcomeEvidenceRepository();
        var service=new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->
                SCOPE.equals(scope)&&evidence.stream().anyMatch(ref->"erp".equals(ref.evidenceType())&&"outcome-proof".equals(ref.evidenceId())),"b3-admission-v1","outcome-verifier-v1");
        var snapshot=snapshot();
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                List.of("candidate-1"),message.broadcastAt(),"consumed");
        new WorkspaceBroadcastService(Map.of("planner",consumer),service).broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1));
        String receipt=B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner");
        var report=new DownstreamOutcomeReport("outcome-1",receipt,SCOPE,"candidate-1",DownstreamOutcomeSignal.USEFUL,
                List.of(new EvidenceReference("erp","outcome-proof"), new EvidenceReference(
                        B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE, snapshot.experimentUnitFingerprint())),NOW.plusSeconds(2));
        var outcome=service.recordVerifiedOutcome(report);
        assertEquals(DownstreamOutcomeSignal.USEFUL,outcome.signal());
        assertEquals("b3-admission-v1",outcome.admissionPolicyVersion());
        assertEquals(1,service.outcomes(SCOPE,100).size());
        assertEquals(outcome,service.recordVerifiedOutcome(report));
    }

    @Test void outcomeForUnconsumedCandidateOrUnverifiedEvidenceIsRejected() {
        var repository=new InMemoryB3OutcomeEvidenceRepository();
        var service=new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->false,"b3-admission-v1","outcome-verifier-v1");
        var snapshot=snapshot();
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                List.of("candidate-1"),message.broadcastAt(),"consumed");
        new WorkspaceBroadcastService(Map.of("planner",consumer),service).broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1));
        String receipt=B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner");
        var outside=new DownstreamOutcomeReport("outcome-outside",receipt,SCOPE,"candidate-2",DownstreamOutcomeSignal.USEFUL,
                List.of(new EvidenceReference("erp","proof")),NOW.plusSeconds(2));
        assertThrows(SecurityException.class,()->service.recordVerifiedOutcome(outside));
        var unverified=new DownstreamOutcomeReport("outcome-unverified",receipt,SCOPE,"candidate-1",DownstreamOutcomeSignal.USEFUL,
                List.of(new EvidenceReference("erp","proof")),NOW.plusSeconds(2));
        assertThrows(SecurityException.class,()->service.recordVerifiedOutcome(unverified));
    }

    @Test void persistedOutcomeIsReverifiedBeforeTrainerCapabilityIsMinted() {
        var repository=new InMemoryB3OutcomeEvidenceRepository();
        var service=new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->
                "trusted-proof".equals(evidence.getFirst().evidenceId()),"b3-admission-v1","outcome-verifier-v1");
        var snapshot=snapshot();
        var receipt=new WorkspaceReceiptEvidence(B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner"),SCOPE,
                snapshot.cycleId(),snapshot.id(),"planner",ConsumerReceiptStatus.ACCEPTED,
                Map.of("candidate-1",B3OutcomeEvidenceService.candidateFingerprint(snapshot.entries().getFirst().candidate())),
                "c".repeat(64),"d".repeat(64),"b3-admission-v1",NOW.plusSeconds(1));
        repository.appendReceipt(receipt);
        // Simulate a row inserted or altered outside the trusted B3 verifier boundary.
        repository.appendOutcome(new DownstreamOutcomeEvidence("forged-outcome",receipt.receiptId(),SCOPE,
                receipt.cycleId(),receipt.workspaceId(),"candidate-1",
                receipt.consumedCandidateFingerprints().get("candidate-1"),"planner",DownstreamOutcomeSignal.USEFUL,
                "outcome-verifier-v1",List.of(new EvidenceReference("erp","untrusted-proof")),"d".repeat(64),
                "b3-admission-v1",NOW.plusSeconds(2)));
        assertThrows(SecurityException.class,()->service.verifiedOutcomes(SCOPE,100));
    }


    @Test void verifiedDownstreamOutcomeUpdatesOnlyTheExactAttributedB1MemoryRevision() {
        var b1Repository = new dev.supplychain.agent.mechanisms.b1.InMemoryB1MemoryFeatureRepository();
        var b1Learning = new dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureLearningService(b1Repository);
        var repository = new InMemoryB3OutcomeEvidenceRepository();
        var service = new B3OutcomeEvidenceService(repository,
                (scope,receipt,candidate,signal,evidence,at)->true,"b3-admission-v1","outcome-verifier-v1",b1Learning);
        var reference = new dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference(
                dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind.EPISODIC,SCOPE,"episode-1",2);
        var base = new WorkspaceCandidate("candidate-attributed",SCOPE,WorkspaceCandidateKind.MEMORY,3.0,0.9,0.1,2,
                Set.of("inventory"),Set.of(),false,List.of(new EvidenceReference("wms","snapshot-2")));
        String fingerprint = B3OutcomeEvidenceService.candidateFingerprint(base);
        var attributed = base.withMemoryAttributions(List.of(new dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution(
                reference,Set.of("inventory.shortage"),fingerprint)));
        var snapshot = new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(
                new WorkspaceAdmissionRequest("cycle-attributed",SCOPE,4,2,Set.of(),Map.of(),
                        new WorkspaceScoringPolicy(1,1),List.of(attributed),NOW)
                        .withExperimentUnitFingerprint(sha("scenario-input:attributed-memory-v1"))).snapshot();
        WorkspaceConsumer consumer = message -> new WorkspaceConsumerReceipt("planner",message.snapshot().id(),
                ConsumerReceiptStatus.ACCEPTED,List.of("candidate-attributed"),message.broadcastAt(),"consumed");
        new WorkspaceBroadcastService(Map.of("planner",consumer),service).broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1));
        String receipt = B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner");
        var report = new DownstreamOutcomeReport("outcome-attributed",receipt,SCOPE,"candidate-attributed",DownstreamOutcomeSignal.USEFUL,
                List.of(new EvidenceReference("erp","verified-outcome"),new EvidenceReference(
                        B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE,snapshot.experimentUnitFingerprint())),NOW.plusSeconds(2));
        service.recordVerifiedOutcome(report);
        var state = b1Learning.find(reference).orElseThrow();
        assertEquals(1,state.usefulOutcomes());
        assertEquals(1.0,state.expectedDownstreamUtility());
        assertEquals(0.6,state.cueAssociationStrengths().get("inventory.shortage"));
        service.recordVerifiedOutcome(report);
        assertEquals(1,b1Learning.find(reference).orElseThrow().usefulOutcomes(),"outcome replay must not train twice");
    }

    @Test void selectedCoalitionOutcomesAreReceiptBoundAndProduceOnlyShadowFeedbackUntilHeldOutApproval() {
        var repository = new InMemoryB3OutcomeEvidenceRepository();
        var service = new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->true,
                "b3-admission-v1","outcome-verifier-v1");
        String unitOne=sha("coalition-scenario-one"), unitTwo=sha("coalition-scenario-two");
        var first=snapshotForCoalition("coalition-cycle-one",unitOne,service);
        var second=snapshotForCoalition("coalition-cycle-two",unitTwo,service);
        var verified=service.verifiedCoalitionOutcomes(SCOPE,100);
        assertEquals(2,verified.size());
        var trainer=new B3CoalitionFeedbackPolicyService(5,0.25);
        var candidate=trainer.train("b3-coalition-v1",1,verified,2,2.0,NOW.plusSeconds(5));
        assertEquals(B3CoalitionFeedbackCandidate.Disposition.SHADOW,candidate.disposition());
        String preference=first.evaluatedCoalitionProposals().stream().filter(WorkspaceCoalitionProposal::selected)
                .map(WorkspaceCoalitionProposal::preferenceKey).findFirst().orElseThrow();
        assertEquals(2.0,candidate.preferenceAdjustments().get(preference));

        var holdout=new java.util.ArrayList<B3CoalitionFeedbackObservation>();
        for(int i=0;i<5;i++) holdout.add(new B3CoalitionFeedbackObservation("holdout-"+i,sha("heldout-unit-"+i),
                sha("baseline-run-"+i),sha("candidate-run-"+i),0.4,0.7,0,0,10,10));
        var evaluation=trainer.evaluate(candidate,holdout,(trained,observation,at)->true,NOW.plusSeconds(6));
        assertEquals(0, B3CoalitionFeedbackEvaluationReport.class.getConstructors().length,
                "external callers must not be able to manufacture a holdout-eligibility proof");
        assertEquals(B3CoalitionFeedbackEvaluationReport.Disposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION,evaluation.decision());
        var approval=new B3CoalitionFeedbackActivationApproval(SCOPE,candidate.policyId(),candidate.version(),
                evaluation.evaluationFingerprint(),"independent-approver","approval-reference",NOW.plusSeconds(7));
        var active=trainer.activate(candidate,evaluation,approval,(c,r,actor,ref,at)->!actor.equals("trainer"),NOW.plusSeconds(8));
        assertEquals(2.0,active.adjustmentFor(SCOPE,preference));
        assertEquals(0.0,active.adjustmentFor(new MemoryScopeId("another-tenant"),preference));
        assertEquals(0.0,active.adjustmentFor(SCOPE,sha("unknown-preference")));
    }

    private WorkspaceSnapshot snapshotForCoalition(String cycle,String unit,B3OutcomeEvidenceService service) {
        var a=new WorkspaceCandidate("coalition-member-a",SCOPE,WorkspaceCandidateKind.MEMORY,3,1,0,1,Set.of("inventory"),Set.of(),false,List.of());
        var b=new WorkspaceCandidate("coalition-member-b",SCOPE,WorkspaceCandidateKind.OBSERVATION,3,1,0,1,Set.of("supplier"),Set.of(),false,List.of());
        var snapshot=new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(
                new WorkspaceAdmissionRequest(cycle,SCOPE,2,2,Set.of(),Map.of(),new WorkspaceScoringPolicy(0,0),List.of(a,b),NOW)
                        .withExperimentUnitFingerprint(unit)).snapshot();
        assertEquals(1,snapshot.coalitions().size());
        var consumed=snapshot.entries().stream().map(e->e.candidate().id()).toList();
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                consumed,message.broadcastAt(),"consumed");
        new WorkspaceBroadcastService(Map.of("planner",consumer),service).broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1));
        var report=new WorkspaceCoalitionOutcomeReport("coalition-outcome-"+cycle,B3OutcomeEvidenceService.receiptId(snapshot.id(),"planner"),
                SCOPE,snapshot.coalitions().getFirst().id(),DownstreamOutcomeSignal.USEFUL,
                List.of(new EvidenceReference("erp","coalition-result-"+cycle),
                        new EvidenceReference(B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE,unit)),NOW.plusSeconds(2));
        service.recordVerifiedCoalitionOutcome(report);
        return snapshot;
    }

    @Test void broadcastDoesNotReportSuccessWhenDurableReceiptRecordingFails() {
        var snapshot=snapshot();
        WorkspaceReceiptRecorder failing=(s,r)->{throw new IllegalStateException("database unavailable");};
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                List.of("candidate-1"),message.broadcastAt(),"consumed");
        var broadcaster=new WorkspaceBroadcastService(Map.of("planner",consumer),failing);
        assertThrows(IllegalStateException.class,()->broadcaster.broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1)));
    }
}
