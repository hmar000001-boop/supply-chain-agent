package dev.supplychain.agent.planning.api;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.planning.PlanningTestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DeterministicCandidateEvaluatorTest {
    @Test
    void acceptsSimulatorReplayedPlanAndMarksApprovalAsGovernanceRequirement() {
        PlanningRequest request = PlanningTestFixtures.request("eval-valid",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        PlanningWorldModel worldModel = PlanningTestFixtures.worldModel();
        PlanningState state = applyFirst(request, worldModel);

        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(worldModel).evaluate(request, state);

        assertTrue(evaluation.feasible());
        assertTrue(evaluation.approvalRequired());
        assertEquals(0.5, evaluation.expectedFulfillmentProgress(), 1.0e-9);
        assertTrue(evaluation.violations().isEmpty());
    }

    @Test
    void rejectsAPlanWhoseClaimedStateDoesNotMatchSimulatorReplay() {
        PlanningRequest request = PlanningTestFixtures.request("eval-forged",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        PlanningWorldModel worldModel = PlanningTestFixtures.worldModel();
        PlanningState valid = applyFirst(request, worldModel);
        InventoryLot lot = valid.world().inventoryLot(PlanningTestFixtures.LOT_B);
        var alteredLot = new InventoryLot(lot.id(), lot.productId(), lot.locationId(),
                PlanningTestFixtures.q("9"), lot.qualityStatus(), lot.expiresAt(), lot.qualityHeldAt(),
                lot.qualityHoldReason(), lot.version());
        var forgedWorld = valid.world().toBuilder().replaceInventoryLot(alteredLot).build();
        PlanningState forged = new PlanningState(valid.objectiveId(), valid.rootWorldVersion(),
                forgedWorld, valid.actionPath(), valid.evidence());

        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(worldModel).evaluate(request, forged);

        assertFalse(evaluation.feasible());
        assertTrue(evaluation.violations().stream().anyMatch(v -> v.contains("deterministic simulator replay")));
        assertEquals(0.0, evaluation.score(), 1.0e-9);
    }

    @Test
    void rejectsPlanExceedingHardRiskThreshold() {
        PlanningRequest original = PlanningTestFixtures.request("eval-risk",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        PlanningRequest strictRisk = copyWithRisk(original, new PlanningRiskPolicy(0.04, true));
        PlanningWorldModel worldModel = PlanningTestFixtures.worldModel();
        PlanningState state = applyFirst(strictRisk, worldModel);

        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(worldModel).evaluate(strictRisk, state);

        assertFalse(evaluation.feasible());
        assertTrue(evaluation.violations().stream().anyMatch(v -> v.contains("risk")));
    }

    @Test
    void rejectsStateForDifferentObjectiveOrRootVersion() {
        PlanningRequest request = PlanningTestFixtures.request("eval-state-owner",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        PlanningWorldModel worldModel = PlanningTestFixtures.worldModel();
        PlanningState valid = applyFirst(request, worldModel);
        var versionShiftedWorld = valid.world().toBuilder().version(valid.world().version() + 1).build();
        PlanningState wrongRoot = new PlanningState(valid.objectiveId(), valid.rootWorldVersion() + 1,
                versionShiftedWorld, valid.actionPath(), valid.evidence());

        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(worldModel).evaluate(request, wrongRoot);

        assertFalse(evaluation.feasible());
        assertTrue(evaluation.violations().stream().anyMatch(v -> v.contains("root-world version")));
    }


    @Test
    void failsClosedWhenProtectedCommitmentConstraintCannotBeVerifiedByCurrentDomainModel() {
        PlanningRequest original = PlanningTestFixtures.request("eval-protected-commitment",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        var constraints = new java.util.HashSet<>(original.hardConstraints());
        constraints.add(dev.supplychain.agent.domain.recovery.RecoveryConstraint.PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS);
        PlanningRequest protectedRequest = new PlanningRequest(original.executionId(), original.objective(),
                original.initialWorld(), original.workingMemory(), original.evidence(), original.availableActionDefinitions(),
                constraints, original.scoringProfile(), original.riskPolicy(), original.authorityPolicy(),
                original.complexity(), original.budget(), original.requestedAt(), null,
                original.uncertaintyAssessmentReference());
        PlanningWorldModel model = PlanningTestFixtures.worldModel();
        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(model).evaluate(protectedRequest, applyFirst(protectedRequest, model));

        assertFalse(evaluation.feasible());
        assertTrue(evaluation.violations().stream().anyMatch(v -> v.contains("PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS")));
    }

    @Test
    void hardApprovalConstraintCannotBeWeakenedByAuthorityPolicyConfiguration() {
        PlanningRequest original = PlanningTestFixtures.request("eval-hard-approval",
                PlanningTestFixtures.budget(10, 20, 0, 5, 3));
        var noPolicyApproval = new PlanningAuthorityPolicy(original.authorityPolicy().policyVersion(),
                original.authorityPolicy().proposalScope(), java.util.Set.of());
        PlanningRequest request = new PlanningRequest(original.executionId(), original.objective(),
                original.initialWorld(), original.workingMemory(), original.evidence(), original.availableActionDefinitions(),
                original.hardConstraints(), original.scoringProfile(), original.riskPolicy(), noPolicyApproval,
                original.complexity(), original.budget(), original.requestedAt(), null,
                original.uncertaintyAssessmentReference());
        PlanningWorldModel model = PlanningTestFixtures.worldModel();
        PlanEvaluation evaluation = PlanningTestFixtures.evaluator(model).evaluate(request, applyFirst(request, model));

        assertTrue(evaluation.feasible());
        assertTrue(evaluation.approvalRequired());
    }

    private static PlanningState applyFirst(PlanningRequest request, PlanningWorldModel model) {
        PlanningState initial = model.initialState(request);
        PlanningAction action = model.availableActions(request, initial).get(0);
        WorldTransition transition = model.apply(request, initial, action);
        assertTrue(transition instanceof WorldTransition.Applied);
        return ((WorldTransition.Applied) transition).state();
    }

    private static PlanningRequest copyWithRisk(PlanningRequest source, PlanningRiskPolicy risk) {
        return new PlanningRequest(source.executionId(), source.objective(), source.initialWorld(),
                source.workingMemory(), source.evidence(), source.availableActionDefinitions(),
                source.hardConstraints(), source.scoringProfile(), risk, source.authorityPolicy(),
                source.complexity(), source.budget(), source.requestedAt(), null,
                source.uncertaintyAssessmentReference());
    }
}
