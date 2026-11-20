package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetaControlGovernorTest {
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private UncertaintyAssessment uncertainty(double evidence,double hypothesis,double plan){
        var reference = new UncertaintyAssessment.Reference("assessment:" + evidence + ":" + hypothesis + ":" + plan,
                1, "e1", "tenant:test");
        return new UncertaintyAssessment(reference, NOW.minusSeconds(1), NOW.plusSeconds(100),
                "test-provenance:meta-control", UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                UncertaintyDimension.EVIDENCE,new UncertaintySignal(UncertaintyDimension.EVIDENCE,evidence,"evidence quality",3),
                UncertaintyDimension.HYPOTHESIS,new UncertaintySignal(UncertaintyDimension.HYPOTHESIS,hypothesis,"hypothesis confidence",2),
                UncertaintyDimension.PLAN,new UncertaintySignal(UncertaintyDimension.PLAN,plan,"plan viability",4)));}

    private ResourceBudget budget(Map<ResourceUnit,Long> limits,Instant deadline){return new ResourceBudget(limits,deadline);}
    private InMemoryResourceBudgetLedger ledger(String execution,ResourceBudget budget){var l=new InMemoryResourceBudgetLedger();l.register(execution,budget);return l;}
    private MetaControlGovernor governor(){return new MetaControlGovernor(new MetaControlPolicy(0.5,0.8,0.2));}
    private ResourceUsage noNextResources(){return new ResourceUsage(Map.of());}

    @Test void resourceReservationTracksReservedAndConsumedAmounts(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));
        l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.MODEL_CALLS,3),NOW);
        assertEquals(7,l.snapshot("e1",NOW).remaining(ResourceUnit.MODEL_CALLS));
        var snapshot=l.commit("e1","r1",ResourceUsage.of(ResourceUnit.MODEL_CALLS,2),NOW.plusSeconds(1));
        assertEquals(2,snapshot.consumed().get(ResourceUnit.MODEL_CALLS).longValue());assertEquals(8,snapshot.remaining(ResourceUnit.MODEL_CALLS));
        var repeatedCommit=l.commit("e1","r1",ResourceUsage.of(ResourceUnit.MODEL_CALLS,2),NOW.plusSeconds(2));
        assertEquals(snapshot.consumed(),repeatedCommit.consumed());
        assertThrows(IllegalStateException.class,()->l.commit("e1","r1",ResourceUsage.of(ResourceUnit.MODEL_CALLS,1),NOW.plusSeconds(3)));
    }
    @Test void concurrentReservationsCannotOversubscribeBudget() throws Exception {
        var l=ledger("e1",budget(Map.of(ResourceUnit.MCTS_ROLLOUTS,10L),NOW.plusSeconds(100)));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{start.await();try{l.reserve("e1","a",ResourceUsage.of(ResourceUnit.MCTS_ROLLOUTS,7),NOW);return true;}catch(ResourceBudgetExceededException ex){return false;}});
            var b=pool.submit(()->{start.await();try{l.reserve("e1","b",ResourceUsage.of(ResourceUnit.MCTS_ROLLOUTS,7),NOW);return true;}catch(ResourceBudgetExceededException ex){return false;}});
            start.countDown();assertTrue(a.get(5,TimeUnit.SECONDS)^b.get(5,TimeUnit.SECONDS));
            assertEquals(7,l.snapshot("e1",NOW).reserved().get(ResourceUnit.MCTS_ROLLOUTS).longValue());
        }finally{pool.shutdownNow();}
    }
    @Test void duplicateReservationIdDoesNotReserveTwice(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));var usage=ResourceUsage.of(ResourceUnit.MODEL_CALLS,3);
        var first=l.reserve("e1","same",usage,NOW);var second=l.reserve("e1","same",usage,NOW.plusSeconds(1));
        assertEquals(first,second);assertEquals(3,l.snapshot("e1",NOW).reserved().get(ResourceUnit.MODEL_CALLS).longValue());
    }
    @Test void reservationIdCannotBeReusedWithDifferentAmount(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));l.reserve("e1","same",ResourceUsage.of(ResourceUnit.MODEL_CALLS,3),NOW);
        assertThrows(IllegalStateException.class,()->l.reserve("e1","same",ResourceUsage.of(ResourceUnit.MODEL_CALLS,4),NOW));
    }
    @Test void releasedReservationReturnsCapacity(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.INPUT_TOKENS,100L),NOW.plusSeconds(100)));l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.INPUT_TOKENS,70),NOW);
        var snapshot=l.release("e1","r1",NOW.plusSeconds(1));assertEquals(100,snapshot.remaining(ResourceUnit.INPUT_TOKENS));assertEquals(0L,snapshot.reserved().getOrDefault(ResourceUnit.INPUT_TOKENS,0L).longValue());
    }
    @Test void actualUsageOverrunIsRecordedAndStopsFurtherWork(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.INPUT_TOKENS,10L),NOW.plusSeconds(100)));l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.INPUT_TOKENS,5),NOW);
        var snapshot=l.commit("e1","r1",ResourceUsage.of(ResourceUnit.INPUT_TOKENS,12),NOW.plusSeconds(1));
        assertTrue(snapshot.overBudget());assertEquals(12,snapshot.consumed().get(ResourceUnit.INPUT_TOKENS).longValue());
        var decision=governor().evaluate(snapshot,uncertainty(0.1,0.2,0.3),noNextResources(),NOW.plusSeconds(2));assertEquals(MetaControlAction.STOP,decision.action());
    }
    @Test void reservationCannotStartAtOrAfterDeadline(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(10)));
        assertThrows(ResourceBudgetExceededException.class,()->l.reserve("e1","late",ResourceUsage.of(ResourceUnit.MODEL_CALLS,1),NOW.plusSeconds(10)));
    }
    @Test void highEvidenceUncertaintyRequestsNewEvidence(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));
        var d=governor().evaluate(l.snapshot("e1",NOW),uncertainty(0.6,0.2,0.3),noNextResources(),NOW);
        assertEquals(MetaControlAction.REQUEST_EVIDENCE,d.action());assertEquals(ResumeCondition.NEW_EVIDENCE,d.resumeCondition());
    }
    @Test void criticalPlanUncertaintyInterruptsForHumanDecision(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));
        var d=governor().evaluate(l.snapshot("e1",NOW),uncertainty(0.2,0.3,0.9),noNextResources(),NOW);
        assertEquals(MetaControlAction.INTERRUPT_FOR_HUMAN,d.action());assertEquals(ResumeCondition.HUMAN_DECISION,d.resumeCondition());
    }
    @Test void deadlineStopsExecution(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MODEL_CALLS,10L),NOW));var d=governor().evaluate(l.snapshot("e1",NOW),uncertainty(0.1,0.1,0.1),noNextResources(),NOW);
        assertEquals(MetaControlAction.STOP,d.action());assertEquals(ResumeCondition.NEVER,d.resumeCondition());
    }
    @Test void exhaustedConfiguredResourceSuspendsUntilAuthorizedBudgetReplenishment(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.SEARCH_EXPANSIONS,1L),NOW.plusSeconds(100)));
        l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS,1),NOW);l.commit("e1","r1",ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS,1),NOW.plusSeconds(1));
        var d=governor().evaluate(l.snapshot("e1",NOW.plusSeconds(2)),uncertainty(0.1,0.1,0.1),ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS,1),NOW.plusSeconds(2));assertEquals(MetaControlAction.INTERRUPT_FOR_HUMAN,d.action());assertEquals(ResumeCondition.BUDGET_REPLENISHED,d.resumeCondition());
    }
    @Test void exhaustedUnusedPlannerBudgetDoesNotStopDifferentNextStep(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.MCTS_ROLLOUTS,0L,ResourceUnit.MODEL_CALLS,10L),NOW.plusSeconds(100)));
        var d=governor().evaluate(l.snapshot("e1",NOW),uncertainty(0.1,0.1,0.1),ResourceUsage.of(ResourceUnit.MODEL_CALLS,1),NOW);
        assertEquals(MetaControlAction.CONTINUE,d.action());
    }
    @Test void lowResourceMarginSelectsReducedComputation(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.INPUT_TOKENS,100L),NOW.plusSeconds(100)));
        l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.INPUT_TOKENS,85),NOW);l.commit("e1","r1",ResourceUsage.of(ResourceUnit.INPUT_TOKENS,85),NOW.plusSeconds(1));
        var d=governor().evaluate(l.snapshot("e1",NOW.plusSeconds(2)),uncertainty(0.1,0.1,0.1),ResourceUsage.of(ResourceUnit.INPUT_TOKENS,1),NOW.plusSeconds(2));assertEquals(MetaControlAction.REDUCE_COMPUTATION,d.action());
    }
    @Test void missingUncertaintyDimensionIsNotSilentlyTreatedAsZero(){
        var incomplete=Map.of(UncertaintyDimension.EVIDENCE,new UncertaintySignal(UncertaintyDimension.EVIDENCE,0.1,"known",1));
        var reference = new UncertaintyAssessment.Reference("assessment:incomplete", 1, "e1", "tenant:test");
        assertThrows(IllegalArgumentException.class, () -> new UncertaintyAssessment(reference,
                NOW.minusSeconds(1), NOW.plusSeconds(10), "test-provenance",
                UncertaintyAssessment.Calibration.notCalibrated(), incomplete));
    }
    @Test void uncertaintyAssessmentMustMatchExecutionScopeAndValidityWindow() {
        var assessment = uncertainty(0.1, 0.2, 0.3);
        assertTrue(assessment.isCurrentFor("e1", "tenant:test", NOW));
        assertFalse(assessment.isCurrentFor("other-execution", "tenant:test", NOW));
        assertFalse(assessment.isCurrentFor("e1", "other-tenant", NOW));
        assertFalse(assessment.isCurrentFor("e1", "tenant:test", NOW.plusSeconds(101)));
    }

    @Test void calibratedUncertaintyRequiresAnAuditableValidationProfile() {
        assertThrows(IllegalArgumentException.class, () -> new UncertaintyAssessment.Calibration(
                UncertaintyAssessment.CalibrationStatus.CALIBRATED, "", "", 0));
        var calibration = new UncertaintyAssessment.Calibration(
                UncertaintyAssessment.CalibrationStatus.CALIBRATED,
                "calibration-profile-v1", "validation-report:heldout-01", 250);
        assertEquals(UncertaintyAssessment.CalibrationStatus.CALIBRATED, calibration.status());
    }

    @Test void cumulativeActualUsageSaturatesInsteadOfOverflowingAndHidingAnOverrun(){
        var l=ledger("e1",budget(Map.of(ResourceUnit.OUTPUT_TOKENS,Long.MAX_VALUE-1),NOW.plusSeconds(100)));
        l.reserve("e1","r1",ResourceUsage.of(ResourceUnit.OUTPUT_TOKENS,0),NOW);
        l.reserve("e1","r2",ResourceUsage.of(ResourceUnit.OUTPUT_TOKENS,0),NOW);
        l.commit("e1","r1",ResourceUsage.of(ResourceUnit.OUTPUT_TOKENS,Long.MAX_VALUE),NOW.plusSeconds(1));
        var snapshot=l.commit("e1","r2",ResourceUsage.of(ResourceUnit.OUTPUT_TOKENS,1),NOW.plusSeconds(2));
        assertEquals(Long.MAX_VALUE,snapshot.consumed().get(ResourceUnit.OUTPUT_TOKENS).longValue());
        assertTrue(snapshot.overBudget());
    }

    @Test void computationalBudgetDoesNotModelBusinessInventory(){
        assertFalse(java.util.Arrays.stream(ResourceUnit.values()).anyMatch(u->u.name().contains("INVENTORY")||u.name().contains("MONEY")||u.name().contains("CAPACITY")));
    }
}
