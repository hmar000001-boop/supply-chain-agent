package dev.supplychain.agent.evaluation.host;

import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureLearningService;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.InMemoryB1MemoryFeatureRepository;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftService;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedurePromotionService;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationService;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import org.bsc.langgraph4j.CompiledGraph;

/** The compiled agent graph plus the exact mechanism services and repositories wired into it. */
public record FullGraphRuntime(CompiledGraph<CognitiveCycleState> graph,
        RecoveryProcedureDraftService recoveryProcedureDrafts,
        RecoveryProcedurePromotionService recoveryProcedurePromotion,
        B3OutcomeEvidenceService outcomeEvidence,
        B1RetrievalSelector b1RetrievalSelector,
        B1MemoryFeatureLearningService b1MemoryFeatureLearning,
        InMemoryB1MemoryFeatureRepository b1MemoryFeatureRepository,
        UncertaintyCalibrationService uncertaintyCalibration,
        R2WorkflowConfiguration r2Workflow,
        WorkspaceAdmissionService b3WorkspaceAdmission,
        WorkspaceBroadcastService b3WorkspaceBroadcast) {
}
