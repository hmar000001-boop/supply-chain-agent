package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Complete, trusted planning input. Planner output is a proposal and never an execution grant. */
public record PlanningRequest(
        String executionId,
        RecoveryObjective objective,
        SupplyChainSnapshot initialWorld,
        WorkingMemorySnapshot workingMemory,
        List<EvidenceReference> evidence,
        List<ActionDescriptor> availableActionDefinitions,
        Set<RecoveryConstraint> hardConstraints,
        PlanningScoringProfile scoringProfile,
        PlanningRiskPolicy riskPolicy,
        PlanningAuthorityPolicy authorityPolicy,
        PlanningComplexity complexity,
        PlannerBudget budget,
        Instant requestedAt,
        String resumeSessionId,
        UncertaintyAssessment.Reference uncertaintyAssessmentReference,
        PlannerSelectionOverride plannerSelectionOverride) {
    public PlanningRequest {
        executionId = require(executionId, "executionId");
        objective = Objects.requireNonNull(objective, "objective");
        initialWorld = Objects.requireNonNull(initialWorld, "initialWorld");
        workingMemory = Objects.requireNonNull(workingMemory, "workingMemory");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        availableActionDefinitions = List.copyOf(Objects.requireNonNull(availableActionDefinitions, "availableActionDefinitions"));
        hardConstraints = Set.copyOf(Objects.requireNonNull(hardConstraints, "hardConstraints"));
        scoringProfile = Objects.requireNonNull(scoringProfile, "scoringProfile");
        riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        authorityPolicy = Objects.requireNonNull(authorityPolicy, "authorityPolicy");
        complexity = Objects.requireNonNull(complexity, "complexity");
        budget = Objects.requireNonNull(budget, "budget");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        resumeSessionId = resumeSessionId == null || resumeSessionId.isBlank() ? null : resumeSessionId.trim();
        if (plannerSelectionOverride != null
                && !plannerSelectionOverride.objectiveFingerprint().equals(ObjectiveFingerprint.from(objective).value())) {
            throw new SecurityException("B4 planner-method override is bound to a different recovery objective");
        }
        if (uncertaintyAssessmentReference != null
                && (!uncertaintyAssessmentReference.executionId().equals(executionId)
                || !uncertaintyAssessmentReference.memoryScopeId().equals(workingMemory.scopeId().value()))) {
            throw new SecurityException("planning uncertainty assessment belongs to a different execution or memory scope");
        }
        if (!requestedAt.isBefore(budget.deadline())) throw new IllegalArgumentException("planning request must precede its hard deadline");
        if (!workingMemory.expiresAt().isAfter(requestedAt)) throw new IllegalArgumentException("working-memory snapshot is expired at request time");
        if (!hardConstraints.containsAll(objective.hardConstraints())) {
            throw new IllegalArgumentException("planning request cannot weaken the objective's hard constraints");
        }
        var storedObjective = initialWorld.recoveryObjectives().get(objective.id());
        if (storedObjective == null || !storedObjective.equals(objective)) {
            throw new IllegalArgumentException("objective must match the authoritative objective in the initial world snapshot");
        }
        Map<ActionId, ActionDescriptor> unique = availableActionDefinitions.stream()
                .collect(Collectors.toMap(ActionDescriptor::id, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("duplicate action descriptor: " + a.id());
                }));
        if (unique.values().stream().anyMatch(d -> d.category() != ActionCategory.GROUNDING || d.effect() != ActionEffect.DOMAIN_WRITE)) {
            throw new IllegalArgumentException("planning action definitions must be grounding/domain-write capabilities");
        }
        var proposalScope = authorityPolicy.proposalScope();
        if (unique.values().stream().anyMatch(d -> !proposalScope.contains(d.requiredCapability()))) {
            throw new IllegalArgumentException("available action definitions exceed the trusted proposal scope");
        }
    }

    /** Compatibility constructor for isolated planner/evaluator fixtures without an agent lifecycle. */
    public PlanningRequest(String executionId, RecoveryObjective objective, SupplyChainSnapshot initialWorld,
                           WorkingMemorySnapshot workingMemory, List<EvidenceReference> evidence,
                           List<ActionDescriptor> availableActionDefinitions, Set<RecoveryConstraint> hardConstraints,
                           PlanningScoringProfile scoringProfile, PlanningRiskPolicy riskPolicy,
                           PlanningAuthorityPolicy authorityPolicy, PlanningComplexity complexity,
                           PlannerBudget budget, Instant requestedAt, String resumeSessionId) {
        this(executionId, objective, initialWorld, workingMemory, evidence, availableActionDefinitions,
                hardConstraints, scoringProfile, riskPolicy, authorityPolicy, complexity, budget,
                requestedAt, resumeSessionId, null, null);
    }

    /** Source-compatible constructor retaining the versioned uncertainty reference. */
    public PlanningRequest(String executionId, RecoveryObjective objective, SupplyChainSnapshot initialWorld,
                           WorkingMemorySnapshot workingMemory, List<EvidenceReference> evidence,
                           List<ActionDescriptor> availableActionDefinitions, Set<RecoveryConstraint> hardConstraints,
                           PlanningScoringProfile scoringProfile, PlanningRiskPolicy riskPolicy,
                           PlanningAuthorityPolicy authorityPolicy, PlanningComplexity complexity,
                           PlannerBudget budget, Instant requestedAt, String resumeSessionId,
                           UncertaintyAssessment.Reference uncertaintyAssessmentReference) {
        this(executionId, objective, initialWorld, workingMemory, evidence, availableActionDefinitions,
                hardConstraints, scoringProfile, riskPolicy, authorityPolicy, complexity, budget,
                requestedAt, resumeSessionId, uncertaintyAssessmentReference, null);
    }

    /** Returns a copy with the B4 planner binding and B6's per-request compute envelope applied. */
    public PlanningRequest withPlannerControl(PlannerBudget effectiveBudget,
                                              PlannerSelectionOverride selectionOverride) {
        return new PlanningRequest(executionId, objective, initialWorld, workingMemory, evidence,
                availableActionDefinitions, hardConstraints, scoringProfile, riskPolicy, authorityPolicy,
                complexity, Objects.requireNonNull(effectiveBudget, "effectiveBudget"), requestedAt,
                resumeSessionId, uncertaintyAssessmentReference, selectionOverride);
    }

    public Map<ActionId, ActionDescriptor> actionDefinitionsById() {
        return availableActionDefinitions.stream().collect(Collectors.toUnmodifiableMap(ActionDescriptor::id, Function.identity()));
    }

    public String fingerprint() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    PlanningRequestFingerprint.canonicalInput(this).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
