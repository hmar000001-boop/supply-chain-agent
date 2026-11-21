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

    @Test void broadcastDoesNotReportSuccessWhenDurableReceiptRecordingFails() {
        var snapshot=snapshot();
        WorkspaceReceiptRecorder failing=(s,r)->{throw new IllegalStateException("database unavailable");};
        WorkspaceConsumer consumer=message->new WorkspaceConsumerReceipt("planner",message.snapshot().id(),ConsumerReceiptStatus.ACCEPTED,
                List.of("candidate-1"),message.broadcastAt(),"consumed");
        var broadcaster=new WorkspaceBroadcastService(Map.of("planner",consumer),failing);
        assertThrows(IllegalStateException.class,()->broadcaster.broadcast(snapshot,SCOPE,Set.of("planner"),NOW.plusSeconds(1)));
    }
}
