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
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective;
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
        PlannerSelectionOverride plannerSelectionOverride,
        List<ReusableRecoveryProcedure> reusableRecoveryProcedures,
        UncertaintyGovernanceDirective uncertaintyDirective,
        IndependentAlternativeHypothesisReview alternativeHypothesisReview) {
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
        reusableRecoveryProcedures = List.copyOf(Objects.requireNonNull(reusableRecoveryProcedures, "reusableRecoveryProcedures"));
        uncertaintyDirective = uncertaintyDirective == null
                ? neutralDirective(executionId, workingMemory.scopeId().value(), uncertaintyAssessmentReference)
                : uncertaintyDirective;
        if (uncertaintyAssessmentReference != null
                && !uncertaintyDirective.assessmentReference().equals(uncertaintyAssessmentReference)) {
            throw new SecurityException("planning uncertainty directive differs from the exact assessment reference");
        }
        if (!uncertaintyDirective.assessmentReference().executionId().equals(executionId)
                || !uncertaintyDirective.assessmentReference().memoryScopeId().equals(workingMemory.scopeId().value())) {
            throw new SecurityException("planning uncertainty directive belongs to another execution or scope");
        }
        Map<ActionId, ActionDescriptor> unique = availableActionDefinitions.stream()
                .collect(Collectors.toMap(ActionDescriptor::id, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("duplicate action descriptor: " + a.id());
                }));
        String objectiveFingerprint = ObjectiveFingerprint.from(objective).value();
        for (ReusableRecoveryProcedure reusable : reusableRecoveryProcedures) {
            if (!workingMemory.scopeId().equals(reusable.artifact().scopeId())
                    || reusable.procedure().status() != dev.supplychain.agent.memory.procedural.ProcedureStatus.ACTIVE
                    || !reusable.artifact().objectiveFingerprint().equals(objectiveFingerprint)) {
                throw new SecurityException("reusable B2 recovery procedure is inactive or does not match the planning scope/objective");
            }
            for (var actionReference : reusable.artifact().actions()) {
                ActionDescriptor current = unique.get(new ActionId(actionReference.actionId()));
                if (current == null || !dev.supplychain.agent.mechanisms.b2.RecoveryProcedureActionReferences.current(current)
                        .equals(actionReference)) {
                    throw new SecurityException("reusable B2 procedure references an action absent from or changed in this trusted planning request: "
                            + actionReference.actionId());
                }
            }
        }
        if (alternativeHypothesisReview != null) {
            if (!alternativeHypothesisReview.executionId().equals(executionId)
                    || !alternativeHypothesisReview.assessmentReference().equals(uncertaintyDirective.assessmentReference())
                    || !alternativeHypothesisReview.objectiveFingerprint().equals(objectiveFingerprint)
                    || !evidence.contains(alternativeHypothesisReview.evidenceReference())) {
                throw new SecurityException("independent alternative-hypothesis review is not bound to this exact planning context");
            }
        }
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
                requestedAt, resumeSessionId, null, null, List.of(), null, null);
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
                requestedAt, resumeSessionId, uncertaintyAssessmentReference, null, List.of(), null, null);
    }

    /** Source-compatible constructor retaining B4 method authority before B2 reuse was wired into M5. */
    public PlanningRequest(String executionId, RecoveryObjective objective, SupplyChainSnapshot initialWorld,
                           WorkingMemorySnapshot workingMemory, List<EvidenceReference> evidence,
                           List<ActionDescriptor> availableActionDefinitions, Set<RecoveryConstraint> hardConstraints,
                           PlanningScoringProfile scoringProfile, PlanningRiskPolicy riskPolicy,
                           PlanningAuthorityPolicy authorityPolicy, PlanningComplexity complexity,
                           PlannerBudget budget, Instant requestedAt, String resumeSessionId,
                           UncertaintyAssessment.Reference uncertaintyAssessmentReference,
                           PlannerSelectionOverride plannerSelectionOverride) {
        this(executionId, objective, initialWorld, workingMemory, evidence, availableActionDefinitions,
                hardConstraints, scoringProfile, riskPolicy, authorityPolicy, complexity, budget,
                requestedAt, resumeSessionId, uncertaintyAssessmentReference, plannerSelectionOverride, List.of(), null, null);
    }

    /** Source-compatible constructor retaining B4 and B2 references before B6 directive propagation. */
    public PlanningRequest(String executionId, RecoveryObjective objective, SupplyChainSnapshot initialWorld,
            WorkingMemorySnapshot workingMemory, List<EvidenceReference> evidence,
            List<ActionDescriptor> availableActionDefinitions, Set<RecoveryConstraint> hardConstraints,
            PlanningScoringProfile scoringProfile, PlanningRiskPolicy riskPolicy,
            PlanningAuthorityPolicy authorityPolicy, PlanningComplexity complexity, PlannerBudget budget,
            Instant requestedAt, String resumeSessionId, UncertaintyAssessment.Reference uncertaintyAssessmentReference,
            PlannerSelectionOverride plannerSelectionOverride, List<ReusableRecoveryProcedure> reusableRecoveryProcedures) {
        this(executionId, objective, initialWorld, workingMemory, evidence, availableActionDefinitions, hardConstraints,
                scoringProfile, riskPolicy, authorityPolicy, complexity, budget, requestedAt, resumeSessionId,
                uncertaintyAssessmentReference, plannerSelectionOverride, reusableRecoveryProcedures, null, null);
    }

    /** Source-compatible constructor before independent B6 hypothesis-review evidence was added. */
    public PlanningRequest(String executionId, RecoveryObjective objective, SupplyChainSnapshot initialWorld,
            WorkingMemorySnapshot workingMemory, List<EvidenceReference> evidence,
            List<ActionDescriptor> availableActionDefinitions, Set<RecoveryConstraint> hardConstraints,
            PlanningScoringProfile scoringProfile, PlanningRiskPolicy riskPolicy,
            PlanningAuthorityPolicy authorityPolicy, PlanningComplexity complexity, PlannerBudget budget,
            Instant requestedAt, String resumeSessionId, UncertaintyAssessment.Reference uncertaintyAssessmentReference,
            PlannerSelectionOverride plannerSelectionOverride, List<ReusableRecoveryProcedure> reusableRecoveryProcedures,
            UncertaintyGovernanceDirective uncertaintyDirective) {
        this(executionId, objective, initialWorld, workingMemory, evidence, availableActionDefinitions, hardConstraints,
                scoringProfile, riskPolicy, authorityPolicy, complexity, budget, requestedAt, resumeSessionId,
                uncertaintyAssessmentReference, plannerSelectionOverride, reusableRecoveryProcedures,
                uncertaintyDirective, null);
    }

    /** Returns a copy with governed, condition-specific B2 recovery knowledge attached. */
    public PlanningRequest withReusableRecoveryProcedures(List<ReusableRecoveryProcedure> procedures) {
        var updatedEvidence = new java.util.LinkedHashSet<EvidenceReference>(evidence);
        for (ReusableRecoveryProcedure reusable : procedures) {
            updatedEvidence.add(new EvidenceReference("coala-procedural-recovery",
                    reusable.procedure().id().value() + "@" + reusable.procedure().versionNumber()));
            updatedEvidence.addAll(reusable.artifact().evidence());
        }
        return new PlanningRequest(executionId, objective, initialWorld, workingMemory, List.copyOf(updatedEvidence),
                availableActionDefinitions, hardConstraints, scoringProfile, riskPolicy, authorityPolicy,
                complexity, budget, requestedAt, resumeSessionId, uncertaintyAssessmentReference,
                plannerSelectionOverride, procedures, uncertaintyDirective, alternativeHypothesisReview);
    }

    /** Returns a copy with the B4 planner binding and B6's per-request compute envelope applied. */
    public PlanningRequest withPlannerControl(PlannerBudget effectiveBudget,
                                              PlannerSelectionOverride selectionOverride) {
        return new PlanningRequest(executionId, objective, initialWorld, workingMemory, evidence,
                availableActionDefinitions, hardConstraints, scoringProfile, riskPolicy, authorityPolicy,
                complexity, Objects.requireNonNull(effectiveBudget, "effectiveBudget"), requestedAt,
                resumeSessionId, uncertaintyAssessmentReference, selectionOverride, reusableRecoveryProcedures,
                uncertaintyDirective, alternativeHypothesisReview);
    }

    public PlanningRequest withUncertaintyDirective(UncertaintyGovernanceDirective directive) {
        return new PlanningRequest(executionId, objective, initialWorld, workingMemory, evidence,
                availableActionDefinitions, hardConstraints, scoringProfile, riskPolicy, authorityPolicy,
                complexity, budget, requestedAt, resumeSessionId, uncertaintyAssessmentReference,
                plannerSelectionOverride, reusableRecoveryProcedures, Objects.requireNonNull(directive, "directive"),
                alternativeHypothesisReview);
    }

    private static UncertaintyGovernanceDirective neutralDirective(String executionId, String scopeId,
            UncertaintyAssessment.Reference existing) {
        var reference = existing == null
                ? new UncertaintyAssessment.Reference("planning-standalone-neutral", 1, executionId, scopeId)
                : existing;
        return new UncertaintyGovernanceDirective(reference,
                UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED, "", "", 0,
                0, 0, 0, 1, 1, false, false);
    }

    public Map<ActionId, ActionDescriptor> actionDefinitionsById() {
        return availableActionDefinitions.stream().collect(Collectors.toUnmodifiableMap(ActionDescriptor::id, Function.identity()));
    }

    /** Adds a host-issued alternative-hypothesis review after the B2/B4/B6 planning inputs are final. */
    public PlanningRequest withAlternativeHypothesisReview(IndependentAlternativeHypothesisReview review) {
        Objects.requireNonNull(review, "review");
        PlanningRequest updated = new PlanningRequest(executionId, objective, initialWorld, workingMemory,
                addEvidence(evidence, review.evidenceReference()), availableActionDefinitions, hardConstraints,
                scoringProfile, riskPolicy, authorityPolicy, complexity, budget, requestedAt, resumeSessionId,
                uncertaintyAssessmentReference, plannerSelectionOverride, reusableRecoveryProcedures,
                uncertaintyDirective, review);
        updated.assertGovernanceComplete();
        return updated;
    }

    /** Stable pre-review identity exposed to the trusted host reviewer; excludes only review evidence itself. */
    public String reviewBasisFingerprint() {
        return PlanningRequestFingerprint.basisFingerprint(this);
    }

    public void assertGovernanceComplete() {
        if (uncertaintyDirective.requireAlternativeHypothesisReview() && alternativeHypothesisReview == null) {
            throw new SecurityException("B6 calibrated hypothesis uncertainty requires an independent alternative-hypothesis review before M5 planning");
        }
        if (alternativeHypothesisReview != null) {
            String expectedObjective = ObjectiveFingerprint.from(objective).value();
            if (!alternativeHypothesisReview.executionId().equals(executionId)
                    || !alternativeHypothesisReview.assessmentReference().equals(uncertaintyDirective.assessmentReference())
                    || !alternativeHypothesisReview.objectiveFingerprint().equals(expectedObjective)
                    || alternativeHypothesisReview.reviewedAt().isBefore(requestedAt)
                    || !evidence.contains(alternativeHypothesisReview.evidenceReference())
                    || !alternativeHypothesisReview.basisPlanningFingerprint().equals(PlanningRequestFingerprint.basisFingerprint(this))) {
                throw new SecurityException("independent alternative-hypothesis review is stale or belongs to another planning context");
            }
        }
    }

    private static List<EvidenceReference> addEvidence(List<EvidenceReference> existing, EvidenceReference addition) {
        var values = new java.util.LinkedHashSet<EvidenceReference>(existing);
        values.add(addition);
        return List.copyOf(values);
    }

    /** Stable soft-priority set derived only from exact active, descriptor-revalidated B2 procedures. */
    public Set<ActionId> reusableRecoveryActionIds() {
        return reusableRecoveryProcedures.stream().flatMap(procedure -> procedure.artifact().actions().stream())
                .map(reference -> new ActionId(reference.actionId())).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isReusableRecoveryAction(PlanningAction action) {
        return reusableRecoveryActionIds().contains(Objects.requireNonNull(action, "action").descriptorId());
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
