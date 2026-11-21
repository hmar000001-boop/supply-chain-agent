package dev.supplychain.agent.evaluation.host;

import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftService;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import org.bsc.langgraph4j.CompiledGraph;

/** The compiled agent graph plus the exact R2 service instances wired into that graph. */
public record FullGraphRuntime(CompiledGraph<CognitiveCycleState> graph,
        RecoveryProcedureDraftService recoveryProcedureDrafts,
        B3OutcomeEvidenceService outcomeEvidence,
        B1RetrievalSelector b1RetrievalSelector,
        WorkspaceAdmissionService b3WorkspaceAdmission,
        WorkspaceBroadcastService b3WorkspaceBroadcast) {
}
