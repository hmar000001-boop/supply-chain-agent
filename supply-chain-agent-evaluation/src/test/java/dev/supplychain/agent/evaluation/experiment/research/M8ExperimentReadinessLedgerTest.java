package dev.supplychain.agent.evaluation.experiment.research;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class M8ExperimentReadinessLedgerTest {
    @Test
    void firstGateIsReadyAndDownstreamGatesStayBlockedWithoutReceipts() {
        var snapshot = new M8ExperimentReadinessLedger(Map.of()).evaluate();
        assertEquals(M8ExperimentReadinessLedger.Status.READY,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.HOSTED_CI_GREEN));
        assertEquals(M8ExperimentReadinessLedger.Status.BLOCKED,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.TRAIN_TELEMETRY_EXECUTED));
        assertEquals(M8ExperimentReadinessLedger.Status.OPTIONAL_NOT_STARTED,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.REAL_LLM_PROFILE_QUALIFIED));
    }

    @Test
    void aVerifiedCiReceiptUnlocksOnlyHostQualification() {
        var receipts = new EnumMap<M8ExperimentReadinessLedger.Gate, M8ExperimentReadinessLedger.Receipt>(
                M8ExperimentReadinessLedger.Gate.class);
        receipts.put(M8ExperimentReadinessLedger.Gate.HOSTED_CI_GREEN,
                new M8ExperimentReadinessLedger.Receipt("c".repeat(64), "github-actions", Instant.parse("2026-11-22T17:00:00Z")));
        var snapshot = new M8ExperimentReadinessLedger(receipts).evaluate();
        assertEquals(M8ExperimentReadinessLedger.Status.COMPLETE,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.HOSTED_CI_GREEN));
        assertEquals(M8ExperimentReadinessLedger.Status.READY,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.DETERMINISTIC_TRAIN_HOST_QUALIFIED));
        assertEquals(M8ExperimentReadinessLedger.Status.BLOCKED,
                snapshot.statuses().get(M8ExperimentReadinessLedger.Gate.TRAIN_TELEMETRY_EXECUTED));
    }

    @Test
    void rejectsPrematureDownstreamCompletionReceipt() {
        var receipts = new EnumMap<M8ExperimentReadinessLedger.Gate, M8ExperimentReadinessLedger.Receipt>(
                M8ExperimentReadinessLedger.Gate.class);
        receipts.put(M8ExperimentReadinessLedger.Gate.TRAIN_ARTIFACTS_FROZEN,
                new M8ExperimentReadinessLedger.Receipt("d".repeat(64), "independent-freeze-verifier", Instant.now()));
        assertThrows(SecurityException.class, () -> new M8ExperimentReadinessLedger(receipts).evaluate());
    }
}
