package dev.supplychain.agent.planning.cot;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.planning.PlanningTestFixtures;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SequentialReasoningPlannerTest {
    @Test
    void sequentialPlannerBuildsOneLinearCandidateWithoutBranching() {
        PlanningRequest request = PlanningTestFixtures.request("cot-linear",
                PlanningTestFixtures.budget(3, 10, 0, 0, 3));
        SequentialDecisionProvider provider = (planningRequest, state, available) ->
                SequentialDecision.choose(available.get(0).planningActionId(), planningRequest.evidence(),
                        List.of("Use currently available qualified stock"), "Select the first feasible reservation.");
        var planner = planner(provider, request);

        var result = planner.plan(request);

        assertEquals(PlannerTerminationReason.COMPLETED, result.terminationReason());
        assertEquals(1, result.candidates().size());
        assertEquals(2, result.candidates().get(0).actions().size());
        assertEquals(2, result.trace().size());
        assertEquals(4, result.statistics().evaluations());
    }

    @Test
    void rejectsActionNotPresentInCurrentActionSet() {
        PlanningRequest request = PlanningTestFixtures.request("cot-invalid-action",
                PlanningTestFixtures.budget(2, 10, 0, 0, 2));
        var planner = planner((planningRequest, state, available) -> SequentialDecision.choose(
                "not-a-permitted-action", planningRequest.evidence(), List.of(), "Try an unknown action."), request);

        var result = planner.plan(request);

        assertEquals(PlannerTerminationReason.INVALID_PROPOSAL, result.terminationReason());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void rejectsEvidenceOutsideTheTrustedRequestAndWorkingMemory() {
        PlanningRequest request = PlanningTestFixtures.request("cot-invalid-evidence",
                PlanningTestFixtures.budget(2, 10, 0, 0, 2));
        EvidenceReference unsupported = new EvidenceReference("untrusted", "not-in-request");
        var planner = planner((planningRequest, state, available) -> SequentialDecision.choose(
                available.get(0).planningActionId(), List.of(unsupported), List.of(), "Uses an unsupported reference."), request);

        var result = planner.plan(request);

        assertEquals(PlannerTerminationReason.INVALID_PROPOSAL, result.terminationReason());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void honorsExplicitStopAndDoesNotInventAPlan() {
        PlanningRequest request = PlanningTestFixtures.request("cot-stop",
                PlanningTestFixtures.budget(2, 10, 0, 0, 2));
        var planner = planner((planningRequest, state, available) ->
                SequentialDecision.stop(planningRequest.evidence(), "Insufficient confirmation to propose an action."), request);

        var result = planner.plan(request);

        assertEquals(PlannerTerminationReason.STOPPED_BY_STRATEGY, result.terminationReason());
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.trace().isEmpty());
    }

    @Test
    void chargesModelCallOnlyForExplicitlyModelBackedProvider() {
        PlanningRequest request = PlanningTestFixtures.request("cot-model-budget",
                PlanningTestFixtures.budget(2, 10, 0, 0, 2));
        SequentialDecisionProvider modelProvider = new SequentialDecisionProvider() {
            @Override
            public SequentialDecision chooseNext(PlanningRequest planningRequest,
                                                 dev.supplychain.agent.planning.api.PlanningState state,
                                                 List<PlanningAction> available) {
                return SequentialDecision.choose(available.get(0).planningActionId(),
                        planningRequest.evidence(), List.of(), "Model-backed selection.");
            }
            @Override public boolean modelBacked() { return true; }
        };
        var planner = planner(modelProvider, request);

        var result = planner.plan(request);

        assertEquals(PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED, result.terminationReason());
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, result.statistics().modelCalls());
    }

    @Test
    void sequentialBaselineExplicitlyRejectsResumeRequests() {
        PlanningRequest original = PlanningTestFixtures.request("cot-no-resume",
                PlanningTestFixtures.budget(2, 10, 0, 0, 2));
        PlanningRequest resume = PlanningTestFixtures.withBudgetAndResume(original, original.budget(), "cot-session");
        var planner = planner((planningRequest, state, available) -> SequentialDecision.stop(
                planningRequest.evidence(), "Stop."), original);

        assertThrows(IllegalArgumentException.class, () -> planner.plan(resume));
    }

    private static SequentialReasoningPlanner planner(SequentialDecisionProvider provider, PlanningRequest request) {
        var worldModel = PlanningTestFixtures.worldModel();
        return new SequentialReasoningPlanner(worldModel, PlanningTestFixtures.evaluator(worldModel), provider,
                PlanningTestFixtures.meter(PlanningTestFixtures.ledger(request.executionId(), request.budget())),
                PlanningTestFixtures.clock());
    }
}
