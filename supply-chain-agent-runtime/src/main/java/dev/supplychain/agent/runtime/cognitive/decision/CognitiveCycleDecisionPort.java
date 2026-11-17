package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

import java.time.Instant;
import java.util.Set;

/**
 * Domain translation and durable artifact boundary for the decision-side integration.
 *
 * <p>This port deliberately does not perform retrieval, B1 selection, B3 admission/broadcast,
 * planner routing or deterministic candidate evaluation. Those are invoked by the real services
 * in {@link CognitiveCycleDecisionServices}. Implementations only translate supply-chain domain
 * state into their typed inputs and persist domain evidence/receipt records.</p>
 */
public interface CognitiveCycleDecisionPort {

    MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context);

    MemoryRetrievalQuery retrievalQuery(CognitiveCycleOperations.CycleContext context, Instant at);

    B1SelectionRequest b1SelectionRequest(CognitiveCycleOperations.CycleContext context, Instant at);

    B1CandidateFeatureProvider b1FeatureProvider(CognitiveCycleOperations.CycleContext context);

    /** Persist the M2 result and B1 selection as a provenance-bearing evidence-set record. */
    CognitiveCycleOperations.EvidenceRetrieved persistEvidenceSelection(
            CognitiveCycleOperations.CycleContext context,
            MemoryRetrievalResult retrieved,
            B1SelectionResult selected,
            Instant at);

    /** Build B3 candidates from the evidence set referenced by the graph context. */
    WorkspaceAdmissionRequest workspaceAdmissionRequest(
            CognitiveCycleOperations.CycleContext context, Instant at);

    /** Return the explicitly configured downstream workspace consumers. */
    Set<String> workspaceConsumers(CognitiveCycleOperations.CycleContext context);

    /** Persist broadcast receipts for audit/diagnostics; this does not alter the workspace snapshot. */
    void recordWorkspaceBroadcast(
            CognitiveCycleOperations.CycleContext context,
            WorkspaceBroadcastReport report,
            Instant at);

    /** Build a complete, trusted M5 request from authoritative domain state and admitted context. */
    PlanningRequest planningRequest(CognitiveCycleOperations.CycleContext context, Instant at);
}
