package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ImpasseSignal;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit adapter boundary for supply-chain/domain facts and external systems. Implementations
 * must use authenticated host identity, authoritative repositories and narrowly scoped read/write
 * adapters. Nothing here is an LLM-controlled capability or a permissive default.
 */
public interface CognitiveCycleLifecyclePort {
    MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context);

    /** Stable, execution-scoped identifier; repeated interpretation/restart must return the same value. */
    WorkingMemoryId workingMemoryId(CognitiveCycleOperations.CycleContext context);

    dev.supplychain.agent.core.interpretation.InterpretationRequest interpretationRequest(
            CognitiveCycleOperations.CycleContext context, Instant at);

    /** Persist the actual clarification received from the authenticated host before re-interpretation. */
    void persistClarification(CognitiveCycleOperations.CycleContext context, Instant at);

    /** Required only when the graph has no intention reference; must return the authoritative active objective. */
    Optional<IntentionSeed> intentionSeed(CognitiveCycleOperations.CycleContext context, Instant at);

    /** Facts supplied on context load are provenance-bearing and are applied idempotently to working memory. */
    default List<ContextFact> contextFacts(CognitiveCycleOperations.CycleContext context,
                                          WorkingMemorySnapshot current, Instant at) { return List.of(); }

    ExternalEvidenceDecision acceptExternalEvidence(CognitiveCycleOperations.CycleContext context, Instant at);

    String objectiveFingerprint(CognitiveCycleOperations.CycleContext context);

    ImpasseOpenRequest impasseOpenRequest(CognitiveCycleOperations.CycleContext context, Instant at);

    /** Returns a bounded plan of read-only child steps. It must not execute those steps. */
    ChildInvestigationPlan planChildInvestigation(CognitiveCycleOperations.CycleContext context,
                                                  ImpasseResolutionCase resolutionCase, Instant at);

    /** Executes exactly one previously declared child read step under the supplied B2 case scope. */
    List<ResolutionFinding> executeChildInvestigationStep(CognitiveCycleOperations.CycleContext context,
                                                          ImpasseResolutionCase resolutionCase,
                                                          ChildInvestigationStep step, Instant at);

    ParentContextSnapshot currentParentContext(CognitiveCycleOperations.CycleContext context,
                                               WorkingMemorySnapshot latestWorkingMemory);

    IntentionDirective intentionDirective(CognitiveCycleOperations.CycleContext context,
                                          RecoveryIntention current, Instant at);

    /**
     * Creates and durably stores the next immutable assessment revision. The first call must return
     * version 1; subsequent calls for the same execution must preserve assessmentId and increment
     * the version by exactly one. The lifecycle service supplies an opaque, deterministic
     * idempotency key derived from the logical graph trigger, execution/scope and exact predecessor
     * assessment reference. Implementations must deduplicate by this complete key, not by a logical
     * trigger label alone: the same logical event can recur after the assessment has advanced, while
     * a checkpoint retry from the same predecessor must return the same immutable revision.
     */
    UncertaintyAssessment assessUncertainty(CognitiveCycleOperations.CycleContext context, String requestIdempotencyKey, Instant at);

    /**
     * Resolves the exact reference carried by CycleContext; implementations must not return a newer
     * or older revision. This exactness applies at B4, B6, authorization, revalidation and execution boundaries.
     */
    UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at);

    MetaControlInputs metaControlInputs(CognitiveCycleOperations.CycleContext context, Instant at);

    /** Records the selected B6 decision and any budget reduction through the host's budget ledger. */
    void recordMetaControlDecision(CognitiveCycleOperations.CycleContext context,
                                   MetaControlInputs inputs,
                                   dev.supplychain.agent.mechanisms.b6.MetaControlDecision decision,
                                   Instant at);

    /**
     * Fresh world and evidence snapshot for final pre-execution deterministic replay. The returned
     * PlanningRequest must retain context.requireUncertaintyAssessmentReference() exactly, so the
     * final deterministic check is bound to the same uncertainty revision as the graph checkpoint.
     */
    PlanningRequest revalidationRequest(CognitiveCycleOperations.CycleContext context,
                                        CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                                        WorkingMemorySnapshot latestWorkingMemory, Instant at);

    /** Converts the exact selected typed plan into registered M3 action requests, in plan order. */
    List<ActionDispatchCommand<?, ?>> actionCommands(CognitiveCycleOperations.CycleContext context,
                                                      CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                                                      Instant at);

    /** Reconciles every recorded action receipt against authoritative external/domain state. Must never blindly redispatch. */
    CognitiveCycleOperations.ExternalObservation observeAndReconcile(
            CognitiveCycleOperations.CycleContext context, ActionExecutionBatch batch, Instant at);

    /** Returns an auditable episode draft; the lifecycle service always records it as unverified. */
    EpisodeDraft outcomeEpisode(CognitiveCycleOperations.CycleContext context,
                                CognitiveCycleOperations.ExternalObservation observation, Instant at);

    /** Verified domain proof; mandatory before B4 can mark the protected recovery objective complete. */
    dev.supplychain.agent.mechanisms.b4.ObjectiveSatisfactionProof objectiveSatisfactionProof(
            CognitiveCycleOperations.CycleContext context,
            CognitiveCycleOperations.ExternalObservation observation, Instant at);

    void recordEscalation(CognitiveCycleOperations.CycleContext context, String reason, Instant at);

    record IntentionSeed(RecoveryIntentionId intentionId, RecoveryObjective objective,
                         String owner, RecoveryMethodReference initialMethod) {
        public IntentionSeed {
            Objects.requireNonNull(intentionId, "intentionId");
            Objects.requireNonNull(objective, "objective");
            owner = required(owner, "owner");
            Objects.requireNonNull(initialMethod, "initialMethod");
        }
    }

    record ContextFact(String key, MemoryValue value, List<EvidenceReference> evidence) {
        public ContextFact {
            key = required(key, "key");
            Objects.requireNonNull(value, "value");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        }
    }

    record ExternalEvidenceDecision(CognitiveCycleOperations.EvidenceAcceptance acceptance,
                                    String evidenceSetId, List<ContextFact> facts, String reason) {
        public ExternalEvidenceDecision {
            Objects.requireNonNull(acceptance, "acceptance");
            evidenceSetId = optional(evidenceSetId);
            facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
            reason = required(reason, "reason");
            if (acceptance == CognitiveCycleOperations.EvidenceAcceptance.ACCEPTED && evidenceSetId.isEmpty()) {
                throw new IllegalArgumentException("accepted external evidence must reference a persisted evidence set");
            }
        }
    }

    record ImpasseOpenRequest(String caseId, ImpasseSignal signal, Set<Capability> parentCapabilities,
                              Set<Capability> childCapabilities, Instant deadline, int maximumChildActions) {
        public ImpasseOpenRequest {
            caseId = required(caseId, "caseId");
            Objects.requireNonNull(signal, "signal");
            parentCapabilities = Set.copyOf(Objects.requireNonNull(parentCapabilities, "parentCapabilities"));
            childCapabilities = Set.copyOf(Objects.requireNonNull(childCapabilities, "childCapabilities"));
            Objects.requireNonNull(deadline, "deadline");
            if (maximumChildActions < 1) throw new IllegalArgumentException("maximumChildActions must be positive");
        }
    }

    record ChildInvestigationStep(String stepId, Capability capability, String description) {
        public ChildInvestigationStep {
            stepId = required(stepId, "stepId");
            Objects.requireNonNull(capability, "capability");
            description = required(description, "description");
        }
    }

    record ChildInvestigationPlan(List<ChildInvestigationStep> steps, ResolutionDisposition disposition,
                                  List<String> assumptions, DelegationReturn intentionReturn) {
        public ChildInvestigationPlan {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            disposition = Objects.requireNonNull(disposition, "disposition");
            assumptions = List.copyOf(Objects.requireNonNull(assumptions, "assumptions"));
            if (assumptions.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("child assumptions cannot be blank");
            }
            intentionReturn = Objects.requireNonNull(intentionReturn, "intentionReturn");
            if (disposition != ResolutionDisposition.UNRESOLVED && steps.isEmpty()) {
                throw new IllegalArgumentException("resolved investigation requires at least one evidence step");
            }
        }
    }

    record IntentionDirective(CognitiveCycleOperations.IntentionAction action, String reason,
                              RecoveryMethodReference replacementMethod, String assertedObjectiveFingerprint,
                              String actorId) {
        public IntentionDirective {
            Objects.requireNonNull(action, "action");
            reason = required(reason, "reason");
            assertedObjectiveFingerprint = optional(assertedObjectiveFingerprint);
            actorId = optional(actorId);
            if (action == CognitiveCycleOperations.IntentionAction.CHANGE_METHOD && replacementMethod == null) {
                throw new IllegalArgumentException("method change requires a replacement method");
            }
            if (action == CognitiveCycleOperations.IntentionAction.TERMINATE && actorId.isEmpty()) {
                throw new IllegalArgumentException("termination requires an authenticated actor reference");
            }
        }
    }

    record MetaControlInputs(ResourceBudgetSnapshot budget, UncertaintyAssessment uncertainty,
                             ResourceUsage requiredForNextStep) {
        public MetaControlInputs {
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(uncertainty, "uncertainty");
            Objects.requireNonNull(requiredForNextStep, "requiredForNextStep");
        }
    }

    record EpisodeDraft(String title, String summary, EpisodeOutcome outcome,
                        Instant startedAt, Instant completedAt,
                        List<EntityReference> entities, List<EpisodeEvent> events,
                        List<EvidenceReference> evidence, Provenance provenance) {
        public EpisodeDraft {
            title = required(title, "title");
            summary = required(summary, "summary");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(completedAt, "completedAt");
            if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("episode completion precedes start");
            entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            Objects.requireNonNull(provenance, "provenance");
            if (provenance.isModelDerived()) throw new SecurityException("model-derived text cannot authoritatively create a recovery outcome episode");
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String optional(String value) { return value == null ? "" : value.trim(); }
}
