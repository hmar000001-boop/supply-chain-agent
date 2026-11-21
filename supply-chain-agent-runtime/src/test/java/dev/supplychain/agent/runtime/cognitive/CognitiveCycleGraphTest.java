package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.mechanisms.b6.MetaControlAction;
import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.mechanisms.b6.MetaControlReason;
import dev.supplychain.agent.mechanisms.b6.ResumeCondition;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.*;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveCycleGraphTest {

    @Test
    void readOnlyCycleRunsThroughCoalaPlannersAndObservationWithoutApproval() throws Exception {
        var operations = new ScriptedOperations();
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-readonly");

        var finalOutput = graph.invokeFinal(GraphInput.args(input()), config)
                .orElseThrow(() -> new AssertionError("cognitive cycle produced no final output"));

        assertEquals(END, finalOutput.node());
        assertEquals("COMPLETED", finalOutput.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("TOT_BOUNDED_SEARCH", finalOutput.state().text(CognitiveCycleState.PLANNER_ID));
        assertEquals("wm-1", finalOutput.state().text(CognitiveCycleState.WORKING_MEMORY_ID));
        assertEquals("workspace-1", finalOutput.state().text(CognitiveCycleState.WORKSPACE_SNAPSHOT_ID));
        assertEquals(1L, operations.uncertaintyVersionAtRetrieve,
                "the initial B6 uncertainty assessment must exist before B1 retrieval consumes it");
        assertEquals(1, operations.executeCalls);
        assertEquals(1, operations.observationCalls);
        assertEquals(1, operations.episodeCalls);
    }

    @Test
    void retrievalProjectionAdvancesTheWorkingMemoryVersionInGraphState() throws Exception {
        var operations = new ScriptedOperations();
        operations.projectWorkingMemoryOnRetrieval = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var result = graph.invokeFinal(GraphInput.args(input()), config("cognitive-wm-projection"))
                .orElseThrow(() -> new AssertionError("projected cognitive cycle produced no final output"));

        assertEquals(END, result.node());
        assertEquals("wm-1", result.state().text(CognitiveCycleState.WORKING_MEMORY_ID));
        assertEquals(3L, result.state().number(CognitiveCycleState.WORKING_MEMORY_VERSION));
    }

    @Test
    void clarificationIsSuppliedByHostBeforeTheInterpretationCycleResumes() throws Exception {
        var operations = new ScriptedOperations();
        operations.needsClarificationOnce = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-clarification");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause before the clarification node */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        assertEquals(0, operations.clarificationCalls);

        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.CLARIFICATION_CONTEXT, "The disrupted supplier serves customer order SO-100."), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("clarification cycle did not complete"));

        assertEquals(END, resumed.node());
        assertEquals(1, operations.clarificationCalls);
        assertEquals(1, resumed.state().integer(CognitiveCycleState.CLARIFICATION_ROUND));
        assertEquals("COMPLETED", resumed.state().text(CognitiveCycleState.DECISION_STATUS));
    }

    @Test
    void stateChangingCandidateInterruptsAndResumesOnlyAfterBoundApproval() throws Exception {
        var operations = new ScriptedOperations();
        operations.requireApproval = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-approval");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) {
            // Consume graph updates up to the static approval breakpoint.
        }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var paused = graph.getState(config).state();
        assertEquals("approval-1", paused.text(CognitiveCycleState.APPROVAL_ID));
        assertEquals(0, operations.executeCalls, "no state-changing action may run before the approval response");

        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.APPROVAL_RESPONSE_ID, "approval-response-1",
                CognitiveCycleState.APPROVER_ID, "human-approver-7"), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("approved cycle did not reach a terminal output"));

        assertEquals(END, resumed.node());
        assertEquals("COMPLETED", resumed.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("APPROVED", resumed.state().text(CognitiveCycleState.APPROVAL_DECISION));
        assertEquals(1, operations.approvalCalls);
        assertEquals(1, operations.executeCalls);
    }

    @Test
    void deniedApprovalNeverExecutesCandidate() throws Exception {
        var operations = new ScriptedOperations();
        operations.requireApproval = true;
        operations.denyApproval = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-denied");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause at approval */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.APPROVAL_RESPONSE_ID, "approval-response-denied",
                CognitiveCycleState.APPROVER_ID, "human-approver-8"), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("denied cycle did not terminate"));

        assertEquals(END, resumed.node());
        assertEquals("ESCALATED", resumed.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, operations.executeCalls);
        assertEquals(1, operations.escalationCalls);
    }

    @Test
    void externalEvidenceIsARealInterruptAndIsAcceptedBeforeRetrievalRepeats() throws Exception {
        var operations = new ScriptedOperations();
        operations.requireExternalEvidenceOnce = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-evidence");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause before awaiting evidence */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        assertEquals("NEEDS_EXTERNAL_EVIDENCE", graph.getState(config).state().text(CognitiveCycleState.DECISION_STATUS));

        var updateConfig = graph.updateState(config,
                Map.of(CognitiveCycleState.EVIDENCE_RESPONSE_ID, "evidence-response-1"), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("evidence-resumed cycle did not complete"));

        assertEquals(END, resumed.node());
        assertEquals(1, operations.acceptEvidenceCalls);
        assertTrue(operations.retrieveCalls >= 2);
        assertEquals(1, operations.executeCalls);
    }

    @Test
    void b2ImpasseIsResolvedInItsOwnBoundedSubgraphBeforeParentReconsiders() throws Exception {
        var operations = new ScriptedOperations();
        operations.impasseOnFirstRetrieval = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var result = graph.invokeFinal(GraphInput.args(input()), config("cognitive-b2"))
                .orElseThrow(() -> new AssertionError("B2 resolution cycle did not complete"));

        assertEquals(END, result.node());
        assertEquals(1, operations.openImpasseCalls);
        assertEquals(1, operations.investigateImpasseCalls);
        assertEquals(1, operations.validateImpasseCalls);
        assertEquals(1L, operations.uncertaintyVersionAtRetrieve,
                "B1 retrieval must consume the initial B6 assessment");
        assertEquals(2L, operations.uncertaintyVersionAtOpen,
                "the child must begin under the fresh parent assessment for the detected impasse");
        assertEquals(2L, operations.uncertaintyVersionAtInvestigation,
                "investigation must use the assessment under which the child was opened");
        assertEquals(3L, operations.uncertaintyVersionAtValidation,
                "parent return validation must use a fresh assessment after child investigation");
        assertEquals(3L, operations.uncertaintyVersionAtParentReload,
                "the refreshed child assessment reference must be propagated back to the parent graph");
        assertTrue(result.state().events().stream().anyMatch(event -> event.equals("b2-resolution:RESOLVED")));
        assertEquals(1, operations.executeCalls);
    }

    @Test
    void b6HumanControlInterruptRequiresAHostIssuedResponse() throws Exception {
        var operations = new ScriptedOperations();
        operations.metaControlAction = MetaControlAction.INTERRUPT_FOR_HUMAN;
        operations.metaControlActionAfterHumanControl = MetaControlAction.CONTINUE;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-human-control");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause before human-control node */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID, "control-response-1",
                CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID, "operator-1"), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("human-control cycle did not complete"));

        assertEquals(END, resumed.node());
        assertEquals(1, operations.humanControlCalls);
        assertEquals(2, operations.metaControlCalls,
                "B6 must reassess the cycle after the host decision using the refreshed uncertainty assessment");
        assertEquals(4L, operations.uncertaintyVersionAtSecondMetaControl,
                "the resumed B6 decision must consume the post-human-control assessment revision after the initial pre-retrieval assessment");
        assertEquals(1, operations.executeCalls);
    }

    @Test
    void modelOrPlannerCannotMarkAStateChangingCandidateAsApprovalFree() {
        assertThrows(SecurityException.class, () -> new AuthorizationPrepared(
                AuthorizationDisposition.NO_APPROVAL_REQUIRED, true, "", "model says it is safe"));
    }

    @Test
    void b4SuspensionUsesTheHumanControlInterruptionAndThenResumes() throws Exception {
        var operations = new ScriptedOperations();
        operations.intentionAction = IntentionAction.SUSPEND;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-b4-suspend");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause at the B4 suspension boundary */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        assertEquals(0, operations.executeCalls);

        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID, "b4-control-response",
                CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID, "operator-b4"), null);
        var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("B4-suspended cycle did not resume"));

        assertEquals(END, resumed.node());
        assertEquals(1, operations.humanControlCalls);
        assertEquals(1, operations.executeCalls);
        assertEquals("b4-control-response", resumed.state().text(CognitiveCycleState.HUMAN_CONTROL_AUDIT_REFERENCE));
        assertEquals("operator-b4", resumed.state().text(CognitiveCycleState.HUMAN_CONTROL_ACTOR_AUDIT_ID));
    }

    @Test
    void approvalMustMatchExactCandidateSetAndIntentionVersion() throws Exception {
        var operations = new ScriptedOperations();
        operations.requireApproval = true;
        operations.mismatchApprovalBinding = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-approval-binding");

        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause at approval */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.APPROVAL_RESPONSE_ID, "approval-response-1",
                CognitiveCycleState.APPROVER_ID, "human-approver-7"), null);

        assertFailureContains(SecurityException.class,
                () -> graph.invokeFinal(GraphInput.resume(), updateConfig));
        assertEquals(0, operations.executeCalls);
    }

    @Test
    void spoofedHumanControlActorIsRejected() throws Exception {
        var operations = new ScriptedOperations();
        operations.metaControlAction = MetaControlAction.INTERRUPT_FOR_HUMAN;
        operations.mismatchHumanControlActor = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var config = config("cognitive-human-identity");
        var firstRun = graph.stream(GraphInput.args(input()), config);
        for (var ignored : firstRun) { /* pause at B6 human-control boundary */ }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var updateConfig = graph.updateState(config, Map.of(
                CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID, "control-response-1",
                CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID, "operator-1"), null);

        assertFailureContains(SecurityException.class,
                () -> graph.invokeFinal(GraphInput.resume(), updateConfig));
        assertEquals(0, operations.executeCalls);
    }

    @Test
    void unknownExternalOutcomeIsReconciledAndNeverBlindlyRetried() throws Exception {
        var operations = new ScriptedOperations();
        operations.unknownOutcome = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var result = graph.invokeFinal(GraphInput.args(input()), config("cognitive-unknown-outcome"))
                .orElseThrow(() -> new AssertionError("reconciled cycle did not complete"));

        assertEquals(END, result.node());
        assertEquals(1, operations.executeCalls);
        assertEquals(1, operations.observationCalls);
        assertEquals(1, operations.workingMemoryUpdateCalls);
        assertEquals(1, operations.episodeCalls);
    }

    @Test
    void workingMemoryVersionConflictForcesReloadAndReconsideration() throws Exception {
        var operations = new ScriptedOperations();
        operations.workingMemoryConflictOnce = true;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var result = graph.invokeFinal(GraphInput.args(input()), config("cognitive-wm-conflict"))
                .orElseThrow(() -> new AssertionError("reconsidered cycle did not complete"));

        assertEquals(END, result.node());
        assertTrue(operations.contextLoadCalls >= 2);
        assertEquals(1, operations.executeCalls, "a memory conflict after observation must not repeat the external action");
        assertEquals(2, operations.workingMemoryUpdateCalls);
    }

    @Test
    void metaControlStopNeverReachesAuthorizationOrExecution() throws Exception {
        var operations = new ScriptedOperations();
        operations.metaControlAction = MetaControlAction.STOP;
        var graph = CognitiveCycleGraph.compile(operations, new MemorySaver());
        var result = graph.invokeFinal(GraphInput.args(input()), config("cognitive-stop"))
                .orElseThrow(() -> new AssertionError("stopped cycle did not terminate"));

        assertEquals(END, result.node());
        assertEquals("ESCALATED", result.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, operations.executeCalls);
        assertEquals(1, operations.escalationCalls);
    }

    /**
     * LangGraph4j exposes node failures through its asynchronous graph runner. Assert the
     * security failure in the cause chain instead of depending on one wrapper exception type.
     */
    private static void assertFailureContains(Class<? extends Throwable> expectedCause,
                                              org.junit.jupiter.api.function.Executable executable) {
        var thrown = assertThrows(RuntimeException.class, executable);
        Throwable current = thrown;
        while (current != null) {
            if (expectedCause.isInstance(current)) {
                return;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        throw new AssertionError("Expected failure cause " + expectedCause.getName()
                + " but found " + thrown, thrown);
    }

    private static RunnableConfig config(String prefix) {
        return RunnableConfig.builder().threadId(prefix + "-" + UUID.randomUUID()).build();
    }

    private static Map<String, Object> input() {
        return Map.of(
                CognitiveCycleState.REQUEST_ID, "request-1",
                CognitiveCycleState.EXECUTION_ID, "execution-1",
                CognitiveCycleState.MEMORY_SCOPE_ID, "tenant-acme",
                CognitiveCycleState.REQUEST, "Assess the supplier shutdown affecting customer order SO-100.");
    }

    private static final class ScriptedOperations implements CognitiveCycleOperations {
        private boolean needsClarificationOnce;
        private boolean projectWorkingMemoryOnRetrieval;
        private boolean clarificationIssued;
        private int clarificationCalls;
        private boolean requireApproval;
        private boolean denyApproval;
        private boolean mismatchApprovalBinding;
        private boolean mismatchHumanControlActor;
        private boolean unknownOutcome;
        private boolean workingMemoryConflictOnce;
        private boolean workingMemoryConflictIssued;
        private IntentionAction intentionAction = IntentionAction.CONTINUE;
        private int contextLoadCalls;
        private int workingMemoryUpdateCalls;
        private boolean requireExternalEvidenceOnce;
        private boolean externalEvidenceIssued;
        private boolean impasseOnFirstRetrieval;
        private boolean impasseIssued;
        private MetaControlAction metaControlAction = MetaControlAction.CONTINUE;
        private MetaControlAction metaControlActionAfterHumanControl;
        private int metaControlCalls;
        private long uncertaintyVersionAtSecondMetaControl;
        private int retrieveCalls;
        private int acceptEvidenceCalls;
        private int openImpasseCalls;
        private int investigateImpasseCalls;
        private int validateImpasseCalls;
        private long uncertaintyVersionAtOpen;
        private long uncertaintyVersionAtInvestigation;
        private long uncertaintyVersionAtValidation;
        private long uncertaintyVersionAtParentReload;
        private long uncertaintyVersionAtRetrieve;
        private int approvalCalls;
        private int humanControlCalls;
        private int executeCalls;
        private int observationCalls;
        private int episodeCalls;
        private int escalationCalls;

        @Override public InterpretationResult interpret(CycleContext context) {
            if (needsClarificationOnce && !clarificationIssued) {
                clarificationIssued = true;
                return new InterpretationResult(InterpretationOutcome.NEEDS_CLARIFICATION,
                        "NEEDS_CLARIFICATION", "The affected customer order must be identified.");
            }
            return new InterpretationResult(InterpretationOutcome.READY, "READY", "Request is sufficiently specified.");
        }
        @Override public void recordClarification(CycleContext context) {
            clarificationCalls++;
            assertTrue(!context.clarificationContext().isBlank());
        }
        @Override public ContextLoaded loadContext(CycleContext context) {
            contextLoadCalls++;
            if (!context.impasseInvestigationReference().isBlank()) {
                uncertaintyVersionAtParentReload = context.requireUncertaintyAssessmentReference().version();
            }
            return new ContextLoaded("wm-1", contextLoadCalls, "intention-1", 2);
        }
        @Override public EvidenceRetrieved retrieveEvidence(CycleContext context) {
            retrieveCalls++;
            if (retrieveCalls == 1) {
                uncertaintyVersionAtRetrieve = context.requireUncertaintyAssessmentReference().version();
            }
            if (impasseOnFirstRetrieval && !impasseIssued) {
                impasseIssued = true;
                return new EvidenceRetrieved(EvidenceDisposition.IMPASSE, "evidence-1", 0.5,
                        "A source conflict requires bounded resolution.");
            }
            if (requireExternalEvidenceOnce && !externalEvidenceIssued) {
                externalEvidenceIssued = true;
                return new EvidenceRetrieved(EvidenceDisposition.NEEDS_EXTERNAL_EVIDENCE, "", 0.7,
                        "Supplier confirmation is missing.");
            }
            if (projectWorkingMemoryOnRetrieval) {
                return new EvidenceRetrieved(EvidenceDisposition.READY, "evidence-1", 0.1,
                        "Evidence is sufficient and selected references were projected.",
                        context.workingMemoryId(), context.workingMemoryVersion() + 1L);
            }
            return new EvidenceRetrieved(EvidenceDisposition.READY, "evidence-1", 0.1, "Evidence is sufficient.");
        }
        @Override public EvidenceAccepted acceptExternalEvidence(CycleContext context) {
            acceptEvidenceCalls++;
            return new EvidenceAccepted(EvidenceAcceptance.ACCEPTED, "evidence-2", "Supplier confirmation accepted.");
        }
        @Override public WorkspacePrepared prepareWorkspace(CycleContext context) {
            return new WorkspacePrepared(WorkspaceDisposition.ADMITTED, "workspace-1", "Required context admitted.");
        }
        @Override public PlanningPrepared proposeCandidates(CycleContext context) {
            return new PlanningPrepared(PlannerId.TOT_BOUNDED_SEARCH, "planner-session-1", "candidate-set-1", 2,
                    PlannerTerminationReason.COMPLETED);
        }
        @Override public CandidateEvaluation evaluateCandidates(CycleContext context) {
            return new CandidateEvaluation(EvaluationDisposition.SELECTED, "candidate-1", "Candidate passed deterministic feasibility checks.");
        }
        @Override public ImpasseOpened openImpasse(CycleContext context) {
            assertTrue(context.uncertaintyAssessmentReference() != null,
                    "B2 investigation must receive the current uncertainty reference, including when retrieval raises an impasse");
            openImpasseCalls++;
            uncertaintyVersionAtOpen = context.requireUncertaintyAssessmentReference().version();
            assertEquals("A source conflict requires bounded resolution.", context.lastReason(),
                    "B2 must receive the original impasse cause as the child's last reason");
            return new ImpasseOpened("impasse-1", context.lastReason());
        }
        @Override public ImpasseInvestigated investigateImpasse(CycleContext context) {
            investigateImpasseCalls++;
            uncertaintyVersionAtInvestigation = context.requireUncertaintyAssessmentReference().version();
            return new ImpasseInvestigated("impasse-findings-1", "Conflicting source records isolated.");
        }
        @Override public ImpasseResolved validateImpasseReturn(CycleContext context) {
            validateImpasseCalls++;
            uncertaintyVersionAtValidation = context.requireUncertaintyAssessmentReference().version();
            return new ImpasseResolved(ImpasseDisposition.RESOLVED, "impasse-findings-1", "Parent version and objective fingerprint remain valid.");
        }
        @Override public IntentionGovernance governIntention(CycleContext context) {
            return new IntentionGovernance(intentionAction, "intention-1", 3, "Objective remains unchanged.");
        }
        private final java.util.Map<String, dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.Reference> uncertaintyByTrigger = new java.util.HashMap<>();
        private long nextUncertaintyVersion;

        @Override public dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.Reference assessUncertainty(
                CycleContext context, String triggerId) {
            return uncertaintyByTrigger.computeIfAbsent(triggerId, ignored -> {
                long version = ++nextUncertaintyVersion;
                return new dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.Reference(
                        "assessment:" + context.executionId(), version, context.executionId(), context.memoryScopeId());
            });
        }

        @Override public MetaControlDecision governMetaControl(CycleContext context) {
            metaControlCalls++;
            MetaControlAction action = humanControlCalls > 0 && metaControlActionAfterHumanControl != null
                    ? metaControlActionAfterHumanControl : metaControlAction;
            if (metaControlCalls == 2) {
                uncertaintyVersionAtSecondMetaControl = context.requireUncertaintyAssessmentReference().version();
            }
            ResumeCondition condition = switch (action) {
                case INTERRUPT_FOR_HUMAN -> ResumeCondition.HUMAN_DECISION;
                case STOP -> ResumeCondition.NEVER;
                default -> null;
            };
            MetaControlReason reason = action == MetaControlAction.INTERRUPT_FOR_HUMAN
                    ? MetaControlReason.UNCERTAINTY_REQUIRES_HUMAN_REVIEW
                    : MetaControlReason.WITHIN_BUDGET_AND_UNCERTAINTY;
            return new MetaControlDecision(action, reason, condition, "Scripted B6 policy result.", 0.2);
        }
        @Override public HumanControlResolution resolveHumanControl(CycleContext context) {
            humanControlCalls++;
            assertTrue(!context.humanControlResponseId().isBlank());
            assertTrue(!context.humanControlActorId().isBlank());
            return new HumanControlResolution(HumanControlAction.CONTINUE,
                    mismatchHumanControlActor ? "impostor" : context.humanControlActorId(),
                    context.humanControlResponseId(), "Operator allows the bounded process to continue.");
        }
        @Override public AuthorizationPrepared prepareAuthorization(CycleContext context) {
            return requireApproval
                    ? new AuthorizationPrepared(AuthorizationDisposition.REQUIRES_APPROVAL, true, "approval-1", "Human approval required.")
                    : new AuthorizationPrepared(AuthorizationDisposition.NO_APPROVAL_REQUIRED, false, "", "Candidate contains no state-changing action.");
        }
        @Override public ApprovalResolved resolveApproval(CycleContext context) {
            approvalCalls++;
            String candidateId = mismatchApprovalBinding ? "different-candidate" : context.selectedCandidateId();
            return denyApproval
                    ? new ApprovalResolved(ApprovalDisposition.DENIED, "approval-1", candidateId,
                            context.candidateSetId(), context.intentionId(), context.intentionVersion(),
                            context.approverId(), "", "Approver denied the candidate.")
                    : new ApprovalResolved(ApprovalDisposition.APPROVED, "approval-1", candidateId,
                            context.candidateSetId(), context.intentionId(), context.intentionVersion(),
                            context.approverId(), "approval-evidence-1", "Exact candidate version approved.");
        }
        @Override public PlanRevalidation revalidatePlan(CycleContext context) {
            return new PlanRevalidation(RevalidationDisposition.VALID, "Plan and approval remain current.");
        }
        @Override public AuthorizedExecution executeAuthorizedCandidate(CycleContext context) {
            executeCalls++;
            return new AuthorizedExecution(unknownOutcome ? ExecutionDisposition.UNKNOWN_OUTCOME : ExecutionDisposition.SUCCEEDED,
                    "operation-1", requireApproval ? "reservation-1" : "supplier-status-read-1",
                    unknownOutcome ? "External result is uncertain." : "Operation completed.");
        }
        @Override public ExternalObservation observeAndReconcile(CycleContext context) {
            observationCalls++;
            return new ExternalObservation(ObservationDisposition.OBJECTIVE_SATISFIED, "observation-1", "evidence-3", "Post-action state verified.");
        }
        @Override public WorkingMemoryUpdated updateWorkingMemoryFromObservation(CycleContext context, ExternalObservation observation) {
            workingMemoryUpdateCalls++;
            if (workingMemoryConflictOnce && !workingMemoryConflictIssued) {
                workingMemoryConflictIssued = true;
                return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.CONFLICT,
                        "", 0, "Working memory was updated by another execution.");
            }
            return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.UPDATED,
                    "wm-1", context.workingMemoryVersion() + 1, "Observation persisted to working memory.");
        }
        @Override public WorkingMemoryUpdated updateWorkingMemoryFromStoredObservation(CycleContext context) {
            workingMemoryUpdateCalls++;
            return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.UPDATED,
                    "wm-1", context.workingMemoryVersion() + 1, "Persisted observation replayed to latest working-memory version.");
        }
        @Override public EpisodeRecorded recordOutcomeEpisode(CycleContext context) {
            episodeCalls++;
            return new EpisodeRecorded("episode-1", EpisodeVerificationStatus.RECORDED_UNVERIFIED);
        }
        @Override public void recordEscalation(CycleContext context, String reason) { escalationCalls++; }
    }
}
