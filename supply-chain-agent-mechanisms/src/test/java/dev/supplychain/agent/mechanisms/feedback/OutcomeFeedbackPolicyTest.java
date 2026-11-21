package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeReport;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import dev.supplychain.agent.mechanisms.b3.InMemoryB3OutcomeEvidenceRepository;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.MaintenanceProfile;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidate;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidateKind;
import dev.supplychain.agent.mechanisms.b3.WorkspaceScoringPolicy;
import dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutcomeFeedbackPolicyTest {
    private static final Instant NOW=Instant.parse("2026-11-20T13:00:00Z");
    private static final MemoryScopeId SCOPE=new MemoryScopeId("tenant-r2");
    private static String sha(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private List<dev.supplychain.agent.mechanisms.b3.VerifiedDownstreamOutcomeEvidence> verifiedOutcomes() {
        var repository=new InMemoryB3OutcomeEvidenceRepository();
        var service=new B3OutcomeEvidenceService(repository,(scope,receipt,candidate,signal,evidence,at)->
                SCOPE.equals(scope)&&!evidence.isEmpty(),"b3-v1","trusted-test-verifier");
        for(int i=1;i<=4;i++) {
            String cycle="train-"+i,workspace="workspace-"+cycle,unitFingerprint=sha("training-scenario-input-"+i);
            String receiptId=B3OutcomeEvidenceService.receiptId(workspace,"planner");
            repository.appendReceipt(new WorkspaceReceiptEvidence(receiptId,SCOPE,cycle,workspace,"planner",
                    ConsumerReceiptStatus.ACCEPTED,java.util.Map.of("candidate-a","a".repeat(64)),"b".repeat(64),
                    unitFingerprint,"b3-v1",NOW.minusSeconds(10)));
            var signal=i<=3?DownstreamOutcomeSignal.USEFUL:DownstreamOutcomeSignal.HARMFUL;
            service.recordVerifiedOutcome(new DownstreamOutcomeReport("o"+i,receiptId,SCOPE,"candidate-a",signal,
                    List.of(new EvidenceReference("erp","evidence-o"+i), new EvidenceReference(
                            B3OutcomeEvidenceService.EXPERIMENT_UNIT_EVIDENCE_TYPE,unitFingerprint)),NOW));
        }
        return service.verifiedOutcomes(SCOPE,100);
    }
    private OutcomeFeedbackPolicyCandidate candidate() {
        return new OutcomeFeedbackPolicyTrainer().train("feedback-r2",2,SCOPE,"b1-topk/b3-base-v1",
                verifiedOutcomes(),3,1.0,NOW);
    }
    private List<FeedbackEvaluationObservation> positiveHoldout() {
        var list=new ArrayList<FeedbackEvaluationObservation>();
        for(int i=1;i<=12;i++)list.add(new FeedbackEvaluationObservation("holdout-"+i,sha("holdout-scenario-input-"+i),
                sha("baseline-run-"+i),sha("candidate-run-"+i),10.0,10.5,0,0,8.0,8.0));
        return List.copyOf(list);
    }

    /** Test-only immutable run ledger: each run fingerprint is registered against its source unit. */
    private FeedbackPolicyHoldoutEvaluator evaluatorFor(List<FeedbackEvaluationObservation> observations) {
        var runToUnit = new java.util.HashMap<String, String>();
        observations.forEach(o -> {
            runToUnit.put(o.baselineRunFingerprint(), o.experimentUnitFingerprint());
            runToUnit.put(o.candidateRunFingerprint(), o.experimentUnitFingerprint());
        });
        return new FeedbackPolicyHoldoutEvaluator((candidate, observation, at) ->
                !at.isBefore(candidate.trainedAt())
                && candidate.baselinePolicyVersion().equals("b1-topk/b3-base-v1")
                && observation.experimentUnitFingerprint().equals(runToUnit.get(observation.baselineRunFingerprint()))
                && observation.experimentUnitFingerprint().equals(runToUnit.get(observation.candidateRunFingerprint())));
    }

    @Test void trainerCreatesBoundedShadowOnlyPolicyFromVerifiedOutcomeRecords() {
        var candidate=candidate();
        assertEquals(PolicyDisposition.SHADOW,candidate.disposition());
        assertEquals(0.5,candidate.adjustmentFor(SCOPE,"candidate-a"),1e-9);
        assertEquals(0.0,candidate.adjustmentFor(SCOPE,"unknown"),1e-9);
        assertEquals(0.0,candidate.adjustmentFor(new MemoryScopeId("tenant-other"),"candidate-a"),1e-9);
        assertEquals(4,candidate.sourceOutcomeIds().size());
    }

    @Test void outcomesBelowMinimumSampleThresholdDoNotChangeAnyCandidateScore() {
        var candidate=new OutcomeFeedbackPolicyTrainer().train("small",1,SCOPE,"baseline",verifiedOutcomes().subList(0,2),
                3,1.0,NOW);
        assertTrue(candidate.candidateAdjustments().isEmpty());
        assertEquals(PolicyDisposition.SHADOW,candidate.disposition());
    }

    @Test void holdoutMustBeIndependentByCommonScenarioInputFingerprintNotUnrelatedIds() {
        var training=candidate();
        String reusedUnit=training.trainingExperimentUnitFingerprints().iterator().next();
        // Deliberately use a different holdout display ID and cycle namespace; the shared input hash must still reject it.
        var overlap=List.of(new FeedbackEvaluationObservation("holdout-label-not-cycle-id",reusedUnit,
                sha("overlap-baseline-run"),sha("overlap-candidate-run"),1,2,0,0,1,1));
        var evaluator=evaluatorFor(overlap);
        assertThrows(SecurityException.class,()->evaluator.evaluate(candidate(),overlap,
                new FeedbackEvaluationThreshold("r2-prereg-v1",2,0.1,0,0),NOW));
    }

    @Test void holdoutRejectsRunFingerprintsAbsentFromTheAuthoritativeEvaluationLedger() {
        var candidate = candidate();
        var unregistered = List.of(new FeedbackEvaluationObservation("external-1", sha("external-unit-1"),
                sha("unregistered-baseline-run"), sha("unregistered-candidate-run"), 10, 11, 0, 0, 8, 8),
                new FeedbackEvaluationObservation("external-2", sha("external-unit-2"),
                sha("unregistered-baseline-run-2"), sha("unregistered-candidate-run-2"), 10, 11, 0, 0, 8, 8));
        var evaluator = evaluatorFor(List.of());
        assertThrows(SecurityException.class, () -> evaluator.evaluate(candidate, unregistered,
                new FeedbackEvaluationThreshold("r2-prereg-v1", 2, 0.1, 0, 0), NOW));
    }

    @Test void promotionEligibilityRequiresPositiveLowerConfidenceBoundNoSafetyRegressionAndNoCostRegression() {
        var candidate=candidate();
        var report=evaluatorFor(positiveHoldout()).evaluate(candidate,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0.0,0),NOW);
        assertEquals(PolicyDisposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION,report.decision());
        assertTrue(report.lowerConfidenceBound95()>0.1);
        var active=new FeedbackPolicyActivationService((candidateArg,reportArg,approver,reference,at)->
                candidateArg.scopeId().equals(SCOPE)&&reportArg.policyId().equals(candidateArg.policyId())
                        &&"ops-lead".equals(approver)&&"change-42".equals(reference)).activate(candidate,report,
                new FeedbackPolicyActivationApproval(SCOPE,candidate.policyId(),candidate.version(),report.evaluationFingerprint(),
                        "ops-lead","change-42",NOW.plusSeconds(1)),NOW.plusSeconds(2));
        assertEquals(0.5,active.adjustmentFor(SCOPE,"candidate-a"),1e-9);
    }

    @Test void policyCannotActivateWithoutPassingEvidenceOrSeparateHostAuthorization() {
        var candidate=candidate();
        var negativeHoldout = List.of(
                new FeedbackEvaluationObservation("holdout-1",sha("negative-unit-1"),sha("negative-base-1"),sha("negative-candidate-1"),10,9,0,0,8,9),
                new FeedbackEvaluationObservation("holdout-2",sha("negative-unit-2"),sha("negative-base-2"),sha("negative-candidate-2"),10,9,0,0,8,9));
        var badReport=evaluatorFor(negativeHoldout).evaluate(candidate,negativeHoldout,
                new FeedbackEvaluationThreshold("r2-prereg-v1",2,0.1,0,0),NOW);
        assertEquals(PolicyDisposition.REJECTED,badReport.decision());
        var eligible=evaluatorFor(positiveHoldout()).evaluate(candidate,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        var service=new FeedbackPolicyActivationService((candidateArg,reportArg,approver,reference,at)->false);
        var badApproval=new FeedbackPolicyActivationApproval(SCOPE,candidate.policyId(),candidate.version(),badReport.evaluationFingerprint(),
                "ops-lead","change-42",NOW.plusSeconds(1));
        assertThrows(SecurityException.class,()->service.activate(candidate,badReport,badApproval,NOW.plusSeconds(2)));
        var unauthorized=new FeedbackPolicyActivationApproval(SCOPE,candidate.policyId(),candidate.version(),eligible.evaluationFingerprint(),
                "ops-lead","change-42",NOW.plusSeconds(1));
        assertThrows(SecurityException.class,()->service.activate(candidate,eligible,unauthorized,NOW.plusSeconds(2)));
    }
    @Test void emptyOrInsufficientHoldoutProducesFiniteShadowReportAndCannotActivate() {
        var report=evaluatorFor(List.of()).evaluate(candidate(),List.of(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        assertEquals(PolicyDisposition.SHADOW,report.decision());
        assertTrue(Double.isFinite(report.lowerConfidenceBound95()));
    }

    @Test void evaluationFingerprintBindsExactCandidateScopeSourceAndAdjustmentPayload() {
        var first=candidate();
        var firstReport=evaluatorFor(positiveHoldout()).evaluate(first,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        var changed=new OutcomeFeedbackPolicyCandidate(first.policyId(),first.version(),first.scopeId(),
                first.baselinePolicyVersion(),java.util.Map.of("candidate-a",0.25),first.sourceOutcomeIds(),
                first.trainingCycleIds(),first.trainingExperimentUnitFingerprints(),first.minimumObservationsPerCandidate(),first.maximumAbsoluteAdjustment(),
                first.sourceFingerprint(),first.trainedAt(),PolicyDisposition.SHADOW);
        var changedReport=evaluatorFor(positiveHoldout()).evaluate(changed,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        assertFalse(firstReport.evaluationFingerprint().equals(changedReport.evaluationFingerprint()));
        var mismatchApproval=new FeedbackPolicyActivationApproval(SCOPE,changed.policyId(),changed.version(),
                firstReport.evaluationFingerprint(),"ops-lead","change-43",NOW.plusSeconds(1));
        assertThrows(SecurityException.class,()->new FeedbackPolicyActivationService((c,r,a,ref,at)->true)
                .activate(changed,changedReport,mismatchApproval,NOW.plusSeconds(2)));
    }

    @Test void authorizedPolicyOverlayChangesB1AndB3RankingButCannotChangeOtherScopes() {
        var candidate=candidate();
        var report=evaluatorFor(positiveHoldout()).evaluate(candidate,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        var active=new FeedbackPolicyActivationService((candidateArg,reportArg,approver,reference,at)->
                candidateArg.scopeId().equals(SCOPE)&&reportArg.evaluationFingerprint().equals(report.evaluationFingerprint()))
                .activate(candidate,report,new FeedbackPolicyActivationApproval(SCOPE,candidate.policyId(),candidate.version(),
                        report.evaluationFingerprint(),"ops-lead","change-integration-1",NOW.plusSeconds(1)),NOW.plusSeconds(2));

        var b1Candidates=List.of(
                new B1MemoryCandidate("candidate-a",SCOPE,B1MemoryCandidateKind.EPISODIC,"memory-a",List.of(),Map.of(),
                        10.0,1,Duration.ZERO,new MaintenanceProfile(0,0,0,Duration.ofDays(1),NOW)),
                new B1MemoryCandidate("candidate-b",SCOPE,B1MemoryCandidateKind.EPISODIC,"memory-b",List.of(),Map.of(),
                        10.8,1,Duration.ZERO,new MaintenanceProfile(0,0,0,Duration.ofDays(1),NOW)));
        var b1Request=new B1SelectionRequest(SCOPE,Set.of(),NOW,2,1,Duration.ZERO,
                new B1ScoringPolicy(0,0,0,0));
        var neutralB1=new B1RetrievalSelector(input->new dev.supplychain.agent.mechanisms.b1.ActivationEstimate(0,0,0),
                estimate->Duration.ZERO).select(b1Request,b1Candidates);
        var adjustedB1=new B1RetrievalSelector(input->new dev.supplychain.agent.mechanisms.b1.ActivationEstimate(0,0,0),
                estimate->Duration.ZERO,active).select(b1Request,b1Candidates);
        assertEquals("candidate-b",neutralB1.selectedCandidates().getFirst().candidateId());
        assertEquals("candidate-a",adjustedB1.selectedCandidates().getFirst().candidateId());

        var b3Candidates=List.of(
                new WorkspaceCandidate("candidate-a",SCOPE,WorkspaceCandidateKind.OBSERVATION,5.0,1.0,0.0,1,
                        Set.of(),Set.of(),false,List.of()),
                new WorkspaceCandidate("candidate-b",SCOPE,WorkspaceCandidateKind.OBSERVATION,5.4,1.0,0.0,1,
                        Set.of(),Set.of(),false,List.of()));
        var b3Request=new WorkspaceAdmissionRequest("cycle-feedback",SCOPE,1,1,Set.of(),Map.of(),
                new WorkspaceScoringPolicy(0,0),b3Candidates,NOW);
        var neutralB3=new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(b3Request);
        var adjustedB3=new WorkspaceAdmissionService(new InMemoryWorkspaceRepository(),active).admit(b3Request);
        assertEquals("candidate-b",neutralB3.snapshot().entries().getFirst().candidate().id());
        assertEquals("candidate-a",adjustedB3.snapshot().entries().getFirst().candidate().id());

        var foreignB1=new B1SelectionRequest(new MemoryScopeId("tenant-other"),Set.of(),NOW,2,1,Duration.ZERO,
                new B1ScoringPolicy(0,0,0,0));
        assertEquals("candidate-b",new B1RetrievalSelector(input->new dev.supplychain.agent.mechanisms.b1.ActivationEstimate(0,0,0),
                estimate->Duration.ZERO,active).select(foreignB1,List.of(
                        new B1MemoryCandidate("candidate-a",new MemoryScopeId("tenant-other"),B1MemoryCandidateKind.EPISODIC,
                                "other-a",List.of(),Map.of(),10.0,1,Duration.ZERO,new MaintenanceProfile(0,0,0,Duration.ofDays(1),NOW)),
                        new B1MemoryCandidate("candidate-b",new MemoryScopeId("tenant-other"),B1MemoryCandidateKind.EPISODIC,
                                "other-b",List.of(),Map.of(),10.8,1,Duration.ZERO,new MaintenanceProfile(0,0,0,Duration.ofDays(1),NOW))))
                .selectedCandidates().getFirst().candidateId());
    }

    @Test void activePolicyCannotTransferFeedbackAcrossTenantScopes() {
        var candidate=candidate();
        var report=evaluatorFor(positiveHoldout()).evaluate(candidate,positiveHoldout(),
                new FeedbackEvaluationThreshold("r2-prereg-v1",10,0.1,0,0),NOW);
        var active=new FeedbackPolicyActivationService((candidateArg,reportArg,approver,reference,at)->true).activate(candidate,report,
                new FeedbackPolicyActivationApproval(SCOPE,candidate.policyId(),candidate.version(),report.evaluationFingerprint(),
                        "ops-lead","change-43",NOW.plusSeconds(1)),NOW.plusSeconds(2));
        assertEquals(0.5,active.adjustmentFor(SCOPE,"candidate-a"),1e-9);
        assertEquals(0.0,active.adjustmentFor(new MemoryScopeId("tenant-other"),"candidate-a"),1e-9);
    }

}
