package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Explicit, allow-listed codecs required by the production runtime repositories.
 * The host owns JSON mapping and must provide deterministic output; Java native
 * serialization and arbitrary polymorphic deserialization are deliberately unsupported.
 */
public record PostgresRuntimeCodecs(
        JsonCodec<PlanningArtifactStore.PlanningArtifacts> planningArtifacts,
        JsonCodec<AuthorizedActionPlan> authorizedActionPlans,
        JsonCodec<ImpasseChildArtifactStore.ImpasseChildArtifact> childArtifacts,
        JsonCodec<CognitiveCycleOperations.ExternalObservation> observations,
        ObservationProvenanceResolver observationProvenance,
        JsonCodec<RecoveryIntention> recoveryIntentions,
        JsonCodec<ImpasseResolutionCase> impasseCases,
        JsonCodec<List<ResolutionFinding>> childStepFindings,
        JsonCodec<ActionReceipt> actionReceipts,
        JsonCodec<ActionFailure> actionFailures,
        Duration operationLease,
        Duration childStepLease) {
    public PostgresRuntimeCodecs {
        Objects.requireNonNull(planningArtifacts, "planningArtifacts");
        Objects.requireNonNull(authorizedActionPlans, "authorizedActionPlans");
        Objects.requireNonNull(childArtifacts, "childArtifacts");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(observationProvenance, "observationProvenance");
        Objects.requireNonNull(recoveryIntentions, "recoveryIntentions");
        Objects.requireNonNull(impasseCases, "impasseCases");
        Objects.requireNonNull(childStepFindings, "childStepFindings");
        Objects.requireNonNull(actionReceipts, "actionReceipts");
        Objects.requireNonNull(actionFailures, "actionFailures");
        positive(operationLease, "operationLease");
        positive(childStepLease, "childStepLease");
    }

    public static PostgresRuntimeCodecs withDefaults(
            JsonCodec<PlanningArtifactStore.PlanningArtifacts> planningArtifacts,
            JsonCodec<AuthorizedActionPlan> authorizedActionPlans,
            JsonCodec<ImpasseChildArtifactStore.ImpasseChildArtifact> childArtifacts,
            JsonCodec<CognitiveCycleOperations.ExternalObservation> observations,
            ObservationProvenanceResolver observationProvenance,
            JsonCodec<RecoveryIntention> recoveryIntentions,
            JsonCodec<ImpasseResolutionCase> impasseCases,
            JsonCodec<List<ResolutionFinding>> childStepFindings,
            JsonCodec<ActionReceipt> actionReceipts,
            JsonCodec<ActionFailure> actionFailures) {
        return new PostgresRuntimeCodecs(planningArtifacts, authorizedActionPlans, childArtifacts, observations,
                observationProvenance, recoveryIntentions, impasseCases, childStepFindings, actionReceipts,
                actionFailures, Duration.ofSeconds(90), Duration.ofSeconds(60));
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
