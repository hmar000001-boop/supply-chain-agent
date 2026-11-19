package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionExecutionLedger;
import dev.supplychain.agent.mechanisms.b2.ImpasseCaseRepository;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionRepository;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatchStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlanStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ObservationStore;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Objects;

/**
 * Scope-bound composition of durable M5/M6/B2/B4 artifacts and the M3 operation ledger.
 * Authentication/approval policy remains a host responsibility; this object exposes the
 * durable audit store and does not manufacture trusted principal identities.
 */
public final class PostgresRuntimePersistence {
    private final PlanningArtifactStore planningArtifacts;
    private final AuthorizedActionPlanStore authorizedActionPlans;
    private final ActionExecutionBatchStore actionExecutionBatches;
    private final ImpasseChildArtifactStore impasseChildArtifacts;
    private final ChildInvestigationStepStore childInvestigationSteps;
    private final ObservationStore observations;
    private final RecoveryIntentionRepository recoveryIntentions;
    private final ImpasseCaseRepository impasseCases;
    private final PostgresApprovalStore approvals;
    private final PostgresActionExecutionLedger actionLedger;
    private final PostgresMessagingStore messaging;
    private final PostgresRuntimeMetrics metrics;

    public PostgresRuntimePersistence(DataSource dataSource, String scopeId, PostgresRuntimeCodecs codecs) {
        Objects.requireNonNull(dataSource, "dataSource");
        String scope = required(scopeId);
        Objects.requireNonNull(codecs, "codecs");
        this.planningArtifacts = new PostgresPlanningArtifactStore(dataSource, scope,
                codecs.planningArtifacts(), new dev.supplychain.agent.infrastructure.postgres.planning.PostgresPlannerSessionStore(dataSource));
        this.authorizedActionPlans = new PostgresAuthorizedActionPlanStore(dataSource, scope, codecs.authorizedActionPlans());
        this.actionExecutionBatches = new PostgresActionExecutionBatchStore(dataSource, scope);
        this.impasseChildArtifacts = new PostgresImpasseChildArtifactStore(dataSource, scope, codecs.childArtifacts());
        this.childInvestigationSteps = new PostgresB2ChildStepReceiptStore(dataSource, scope,
                codecs.childStepFindings(), codecs.childStepLease());
        this.observations = new PostgresObservationStore(dataSource, scope, codecs.observations(), codecs.observationProvenance());
        this.recoveryIntentions = new PostgresRecoveryIntentionRepository(dataSource, scope, codecs.recoveryIntentions());
        this.impasseCases = new PostgresImpasseCaseRepository(dataSource, scope, codecs.impasseCases());
        this.approvals = new PostgresApprovalStore(dataSource);
        this.actionLedger = new PostgresActionExecutionLedger(dataSource, codecs.actionReceipts(),
                codecs.actionFailures(), codecs.operationLease());
        this.messaging = new PostgresMessagingStore(dataSource);
        this.metrics = new PostgresRuntimeMetrics(dataSource, scope);
    }

    public PlanningArtifactStore planningArtifacts() { return planningArtifacts; }
    public AuthorizedActionPlanStore authorizedActionPlans() { return authorizedActionPlans; }
    public ActionExecutionBatchStore actionExecutionBatches() { return actionExecutionBatches; }
    public ImpasseChildArtifactStore impasseChildArtifacts() { return impasseChildArtifacts; }
    public ChildInvestigationStepStore childInvestigationSteps() { return childInvestigationSteps; }
    public ObservationStore observations() { return observations; }
    public RecoveryIntentionRepository recoveryIntentions() { return recoveryIntentions; }
    public ImpasseCaseRepository impasseCases() { return impasseCases; }
    public PostgresApprovalStore approvals() { return approvals; }
    public ActionExecutionLedger actionLedger() { return actionLedger; }
    public PostgresActionExecutionLedger fencedActionLedger() { return actionLedger; }
    public PostgresMessagingStore messaging() { return messaging; }
    public PostgresRuntimeMetrics.Snapshot metrics() { return metrics.read(); }

    /** Exact approval revalidation expected immediately before a state-changing dispatch. */
    public void requireCurrentApproval(String scopeId, String approvalId,
                                       dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort.ApprovalBinding binding,
                                       Instant now) {
        if (!approvals.isCurrentlyValid(scopeId, approvalId, binding, now)) {
            throw new SecurityException("no currently valid approval matches the exact candidate/intention/action-plan binding");
        }
    }

    private static String required(String value) {
        String result = Objects.requireNonNull(value, "scopeId").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("scopeId cannot be blank");
        return result;
    }
}
