package dev.supplychain.agent.evaluation.ablation;

import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b1.*;
import dev.supplychain.agent.mechanisms.b2.*;
import dev.supplychain.agent.mechanisms.b3.*;
import dev.supplychain.agent.mechanisms.b4.*;
import dev.supplychain.agent.mechanisms.b6.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic, side-effect-free M8-B3 probes over the accepted production mechanism APIs.
 * The runner does not touch planning, external actions, real business state or PostgreSQL.
 */
public final class MechanismAblationRunner {
    public static final String VERSION = "M8-B3-v1";
    private static final Instant T0 = Instant.parse("2026-01-15T12:00:00Z");
    private static final MemoryScopeId SCOPE = new MemoryScopeId("m8-b3-evaluation-scope");
    private static final Set<String> REPLAY_SCENARIOS = Set.of(
            "B1-01", "B1-02", "B1-03", "B2-01", "B3-01", "B4-01", "B6-01", "B2B4-01", "B1B3B6-01");

    public record Scenario(String studyId, String scenarioId, int index) { }

    public record Observation(String studyId, String scenarioId, String conditionId,
                              String conditionRole, String observationKind, long seed,
                              Map<String, Double> metrics, Map<String, String> labels,
                              String logicalFingerprint) {
        public Observation {
            studyId = require(studyId, "studyId");
            scenarioId = require(scenarioId, "scenarioId");
            conditionId = require(conditionId, "conditionId");
            conditionRole = require(conditionRole, "conditionRole");
            observationKind = require(observationKind, "observationKind");
            metrics = Map.copyOf(metrics);
            labels = Map.copyOf(labels);
            logicalFingerprint = require(logicalFingerprint, "logicalFingerprint");
        }
        public String toJsonLine() {
            return "{\"recordType\":\"ablationObservation\",\"studyId\":" + q(studyId)
                    + ",\"scenarioId\":" + q(scenarioId) + ",\"conditionId\":" + q(conditionId)
                    + ",\"conditionRole\":" + q(conditionRole) + ",\"observationKind\":" + q(observationKind)
                    + ",\"seed\":" + seed + ",\"metrics\":" + jsonNumbers(metrics)
                    + ",\"labels\":" + jsonStrings(labels) + ",\"logicalFingerprint\":" + q(logicalFingerprint) + "}";
        }
    }

    public record PairComparison(String studyId, String scenarioId, String controlCondition,
                                 String treatmentCondition, Map<String, Double> deltas,
                                 String interpretation) {
        public PairComparison {
            deltas = Map.copyOf(deltas);
            interpretation = require(interpretation, "interpretation");
        }
        public String toJsonLine() {
            return "{\"recordType\":\"pairedContrast\",\"studyId\":" + q(studyId)
                    + ",\"scenarioId\":" + q(scenarioId) + ",\"controlCondition\":" + q(controlCondition)
                    + ",\"treatmentCondition\":" + q(treatmentCondition) + ",\"deltas\":" + jsonNumbers(deltas)
                    + ",\"interpretation\":" + q(interpretation) + "}";
        }
    }

    public record Batch(List<Observation> observations, List<PairComparison> comparisons,
                        int primaryObservationCount, int deterministicReplayCount,
                        int scenarioCount, int determinismReplayMatchCount) {
        public Batch {
            observations = List.copyOf(observations);
            comparisons = List.copyOf(comparisons);
        }
        public String summaryJsonLine() {
            return "{\"recordType\":\"ablationManifest\",\"version\":" + q(VERSION)
                    + ",\"scenarioCount\":" + scenarioCount + ",\"studyCount\":" + observations.stream().map(Observation::studyId).distinct().count()
                    + ",\"primaryObservationCount\":" + primaryObservationCount
                    + ",\"deterministicReplayCount\":" + deterministicReplayCount
                    + ",\"rawObservationCount\":" + observations.size()
                    + ",\"pairedContrastCount\":" + comparisons.size()
                    + ",\"determinismReplayMatchCount\":" + determinismReplayMatchCount
                    + ",\"experimentalUnitCount\":" + scenarioCount
                    + ",\"externalEffects\":0,\"postgresTouched\":false}";
        }
        public List<String> rawJsonLines() {
            var lines = new ArrayList<String>();
            lines.add(summaryJsonLine());
            observations.forEach(row -> lines.add(row.toJsonLine()));
            return List.copyOf(lines);
        }
        public List<String> analysisJsonLines() {
            var lines = new ArrayList<String>();
            lines.add("{\"recordType\":\"analysisManifest\",\"scenarioCount\":" + scenarioCount
                    + ",\"experimentalUnitCount\":" + scenarioCount
                    + ",\"primaryObservationCount\":" + primaryObservationCount
                    + ",\"deterministicReplayRowsExcludedFromInference\":" + deterministicReplayCount
                    + ",\"pairedContrastCount\":" + comparisons.size()
                    + ",\"inference\":\"DESCRIPTIVE_ONLY\"}");
            comparisons.forEach(pair -> lines.add(pair.toJsonLine()));
            lines.add(studySummaryJsonLine(observations, comparisons));
            conditionSummaryJsonLines(observations).forEach(lines::add);
            pairedSummaryJsonLines(comparisons).forEach(lines::add);
            return List.copyOf(lines);
        }
    }

    public Batch run() {
        List<Scenario> scenarios = scenarios();
        var primary = new ArrayList<Observation>();
        Map<String, List<Observation>> byScenario = new LinkedHashMap<>();
        for (Scenario scenario : scenarios) {
            List<Observation> rows = execute(scenario, "PRIMARY");
            byScenario.put(scenario.scenarioId(), rows);
            primary.addAll(rows);
        }

        var comparisons = new ArrayList<PairComparison>();
        for (Scenario scenario : scenarios) {
            List<Observation> rows = byScenario.get(scenario.scenarioId());
            Observation control = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                Observation treatment = rows.get(i);
                comparisons.add(compare(control, treatment));
            }
        }

        var all = new ArrayList<>(primary);
        int replayMatches = 0;
        for (Scenario scenario : scenarios) {
            if (!REPLAY_SCENARIOS.contains(scenario.scenarioId())) continue;
            List<Observation> original = byScenario.get(scenario.scenarioId());
            List<Observation> replay = execute(scenario, "DETERMINISM_REPLAY");
            Observation controlOriginal = original.get(0);
            Observation treatmentOriginal = original.get(original.size() - 1);
            Observation controlReplay = replay.get(0);
            Observation treatmentReplay = replay.get(replay.size() - 1);
            if (controlOriginal.logicalFingerprint().equals(controlReplay.logicalFingerprint())) replayMatches++;
            if (treatmentOriginal.logicalFingerprint().equals(treatmentReplay.logicalFingerprint())) replayMatches++;
            all.add(withReplayKind(controlReplay));
            all.add(withReplayKind(treatmentReplay));
        }
        return new Batch(all, comparisons, primary.size(), all.size() - primary.size(), scenarios.size(), replayMatches);
    }

    private List<Observation> execute(Scenario scenario, String kind) {
        return switch (scenario.studyId()) {
            case "B1" -> probeB1(scenario, kind);
            case "B2" -> probeB2(scenario, kind);
            case "B3" -> probeB3(scenario, kind);
            case "B4" -> probeB4(scenario, kind);
            case "B6" -> probeB6(scenario, kind);
            case "B2+B4" -> probeB2B4(scenario, kind);
            case "B1+B3+B6" -> probeB1B3B6(scenario, kind);
            case "B4+B6" -> probeB4B6(scenario, kind);
            default -> throw new IllegalArgumentException("unknown study: " + scenario.studyId());
        };
    }

    private List<Observation> probeB1(Scenario s, String kind) {
        List<B1MemoryCandidate> candidates = b1Candidates(s.index());
        var selector = new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.65, 0.9, -2.1),
                new ExponentialActivationLatencyModel(320, 0.35, 5, 2_000));
        var request = new B1SelectionRequest(SCOPE, Set.of("supplier", "inventory", "delay"), T0,
                8 + s.index() % 3, 4, Duration.ofDays(3), new B1ScoringPolicy(0.08, 0.0008, 0.0004, 0.05));
        B1SelectionResult selected = selector.select(request, candidates);
        List<B1MemoryCandidate> topK = candidates.stream()
                .sorted(Comparator.comparingDouble(B1MemoryCandidate::expectedDownstreamUtility).reversed()
                        .thenComparing(B1MemoryCandidate::candidateId)).limit(4).toList();
        double maintenanceCostAll = selected.scores().stream().mapToDouble(B1CandidateScore::estimatedMaintenanceCost).sum();
        Set<String> selectedIds = selected.selectedCandidates().stream().map(B1MemoryCandidate::candidateId).collect(Collectors.toSet());
        double selectedMaintenanceCost = selected.scores().stream().filter(score -> selectedIds.contains(score.candidateId()))
                .mapToDouble(B1CandidateScore::estimatedMaintenanceCost).sum();
        double actualMaintenanceAll = 0.0;
        var ledger = new InMemoryMemoryMaintenanceLedger();
        var accounting = new MemoryMaintenanceAccountingService(ledger);
        for (int i = 0; i < candidates.size(); i++) {
            var event = new MemoryMaintenanceEvent("maint:" + s.scenarioId() + ":" + i,
                    candidates.get(i).candidateId(), MaintenanceEventType.VALIDATED, 0.1 + (i * 0.025),
                    T0.minusSeconds(60L * (i + 1)), "fixture:" + s.scenarioId());
            accounting.record(event);
            actualMaintenanceAll += accounting.actualCost(candidates.get(i).candidateId(), T0.minus(Duration.ofDays(1)), T0.plusSeconds(1));
        }
        var rows = new ArrayList<Observation>();
        rows.add(row(s, "TOP_K_NO_STATE", "CONTROL", kind, Map.of(
                "selectedCount", (double) topK.size(), "downstreamUsedCount", (double) topK.size(),
                "relevantRecall", recall(topK, candidates), "relevantPrecision", precision(topK),
                "estimatedMaintenanceCostAllCandidates", 0.0, "selectedEstimatedMaintenanceCost", 0.0,
                "actualMaintenanceCostAllCandidates", actualMaintenanceAll),
                Map.of("selector", "utility-only top-k", "stateObject", "absent")));
        rows.add(row(s, "B1_LOG_ONLY", "ABLATION", kind, Map.of(
                "selectedCount", (double) selected.selectedCandidates().size(), "downstreamUsedCount", 0.0,
                "relevantRecall", recall(selected.selectedCandidates(), candidates), "relevantPrecision", precision(selected.selectedCandidates()),
                "estimatedMaintenanceCostAllCandidates", maintenanceCostAll, "selectedEstimatedMaintenanceCost", selectedMaintenanceCost,
                "actualMaintenanceCostAllCandidates", actualMaintenanceAll),
                Map.of("selector", "B1 production selector", "authoritativeControlEdge", "removed; log retained")));
        rows.add(row(s, "B1_FULL", "TREATMENT", kind, Map.of(
                "selectedCount", (double) selected.selectedCandidates().size(), "downstreamUsedCount", (double) selected.selectedCandidates().size(),
                "relevantRecall", recall(selected.selectedCandidates(), candidates), "relevantPrecision", precision(selected.selectedCandidates()),
                "estimatedMaintenanceCostAllCandidates", maintenanceCostAll, "selectedEstimatedMaintenanceCost", selectedMaintenanceCost,
                "actualMaintenanceCostAllCandidates", actualMaintenanceAll),
                Map.of("selector", "B1 production selector", "authoritativeControlEdge", "enabled",
                        "learningUpdate", "not present in accepted implementation; not evaluated")));
        return rows;
    }

    private List<Observation> probeB2(Scenario s, String kind) {
        List<ImpasseSignal> signals = duplicateSignals(s.index());
        List<ImpasseSignal> typed = new DeterministicImpasseDetector().consolidate(signals);
        return List.of(
                row(s, "GENERIC_RETRY", "CONTROL", kind, Map.of("signalCount", (double) signals.size(),
                        "resolutionUnits", (double) signals.size(), "duplicateSignals", (double) (signals.size() - typed.size()),
                        "typedSubstates", 0.0), Map.of("control", "generic retry per signal")),
                row(s, "B2_TYPED_IMPASSE", "TREATMENT", kind, Map.of("signalCount", (double) signals.size(),
                        "resolutionUnits", (double) typed.size(), "duplicateSignals", (double) (signals.size() - typed.size()),
                        "typedSubstates", (double) typed.size()), Map.of("detector", "DeterministicImpasseDetector",
                        "parentContextMutation", "none")));
    }

    private List<Observation> probeB3(Scenario s, String kind) {
        List<WorkspaceCandidate> candidates = workspaceCandidates(s.index());
        List<WorkspaceCandidate> topK = candidates.stream().sorted(Comparator.comparingDouble(WorkspaceCandidate::utilityPoints)
                .reversed().thenComparing(WorkspaceCandidate::id)).limit(3).toList();
        int baselineCoverage = relevantCoverage(topK.stream().flatMap(c -> c.coverageTags().stream()).collect(Collectors.toSet()));
        var repo = new InMemoryWorkspaceRepository();
        var service = new WorkspaceAdmissionService(repo);
        var request = new WorkspaceAdmissionRequest("cycle:" + s.scenarioId(), SCOPE, 7 + s.index() % 2, 4,
                Set.of("supplier", "inventory", "route"), Map.of("supplier", 2.0, "inventory", 3.0, "route", 2.0),
                new WorkspaceScoringPolicy(0.8, 1.5), candidates, T0);
        WorkspaceAdmissionOutcome outcome = service.admit(request);
        int admitted = outcome.snapshotOptional().map(w -> w.entries().size()).orElse(0);
        int used = outcome.snapshotOptional().map(WorkspaceSnapshot::usedUnits).orElse(0);
        int coverage = outcome.snapshotOptional().map(w -> relevantCoverage(w.coveredTags())).orElse(0);
        boolean broadcastAccepted = false;
        if (outcome.snapshot() != null) {
            var broadcast = new WorkspaceBroadcastService(Map.of("planner", message ->
                    new WorkspaceConsumerReceipt("planner", message.snapshot().id(), ConsumerReceiptStatus.ACCEPTED,
                            message.snapshot().entries().stream().map(e -> e.candidate().id()).toList(), T0.plusSeconds(1), "consumed admitted workspace")));
            broadcastAccepted = broadcast.broadcast(outcome.snapshot(), SCOPE, Set.of("planner"), T0.plusSeconds(1)).allAccepted();
        }
        return List.of(
                row(s, "TOP_K_SHARED_CONTEXT", "CONTROL", kind, Map.of("admittedCount", (double) topK.size(),
                        "coverageTags", (double) baselineCoverage, "capacityUnitsUsed", (double) topK.stream().mapToInt(WorkspaceCandidate::contextUnits).sum(),
                        "broadcastAccepted", 0.0, "missingCoverage", 3.0 - Math.min(3, baselineCoverage)),
                        Map.of("selection", "top-k utility only", "boundedWorkspace", "absent")),
                row(s, "B3_BOUNDED_BROADCAST", "TREATMENT", kind, Map.of("admittedCount", (double) admitted,
                        "coverageTags", (double) coverage, "capacityUnitsUsed", (double) used,
                        "broadcastAccepted", broadcastAccepted ? 1.0 : 0.0,
                        "missingCoverage", (double) (outcome.missingCoverage().size())),
                        Map.of("admissionStatus", outcome.status().name(), "broadcast", "production service",
                                "learning", "consumer receipt is not outcome-attributed policy learning")));
    }

    private List<Observation> probeB4(Scenario s, String kind) {
        RecoveryObjective objective = objective(s.scenarioId());
        var methodA = new RecoveryMethodReference("method-A", "candidate-plan-A");
        var repository = new InMemoryRecoveryIntentionRepository();
        var service = new RecoveryIntentionService(repository);
        RecoveryIntention intention = service.create(new RecoveryIntentionId("int:" + s.scenarioId()), objective,
                SCOPE, "m8-evaluation", methodA, T0);
        RecoveryIntention reconsidered = service.requestReconsideration(intention.id(), intention.version(), "new evidence arrived", T0.plusSeconds(1));
        RecoveryIntention revised = service.changeMethod(intention.id(), reconsidered.version(),
                new RecoveryMethodReference("method-B", "candidate-plan-B"), intention.objectiveFingerprint(), T0.plusSeconds(2));
        boolean objectivePreserved = revised.objectiveFingerprint().equals(intention.objectiveFingerprint())
                && revised.protectedConstraints().equals(intention.protectedConstraints());
        return List.of(
                row(s, "PLAN_ONLY", "CONTROL", kind, Map.of("lifecycleTransitions", 0.0,
                        "methodReconsidered", 0.0, "objectivePreserved", 1.0),
                        Map.of("state", "plan reference only")),
                row(s, "B4_INTENTION_LIFECYCLE", "TREATMENT", kind, Map.of("lifecycleTransitions", 2.0,
                        "methodReconsidered", revised.currentMethod().methodId().equals("method-B") ? 1.0 : 0.0,
                        "objectivePreserved", objectivePreserved ? 1.0 : 0.0),
                        Map.of("finalStatus", revised.status().name(), "version", Long.toString(revised.version()),
                                "durability", "in-memory lifecycle probe only; process-restart durability not tested")));
    }

    private List<Observation> probeB6(Scenario s, String kind) {
        boolean highUncertainty = s.index() % 2 == 0;
        int limit = highUncertainty ? 10 : 20;
        int consumed = highUncertainty ? 7 : 2;
        int next = highUncertainty ? 2 : 3;
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.REASONING_EVALUATIONS, (long) limit), T0.plusSeconds(600));
        ResourceBudgetSnapshot snapshot = new ResourceBudgetSnapshot("exec:" + s.scenarioId(), budget,
                Map.of(ResourceUnit.REASONING_EVALUATIONS, (long) consumed), Map.of(), T0);
        UncertaintyAssessment uncertainty = uncertainty(highUncertainty ? 0.82 : 0.18,
                highUncertainty ? 0.76 : 0.14, highUncertainty ? 0.79 : 0.2);
        ResourceUsage required = ResourceUsage.of(ResourceUnit.REASONING_EVALUATIONS, next);
        String fixedAction = snapshot.remaining(ResourceUnit.REASONING_EVALUATIONS) >= next ? "CONTINUE" : "STOP";
        MetaControlDecision governed = new MetaControlGovernor(new MetaControlPolicy(0.45, 0.75, 0.2))
                .evaluate(snapshot, uncertainty, required, T0);
        return List.of(
                row(s, "FIXED_BUDGET_ONLY", "CONTROL", kind, Map.of("remainingEvaluations", (double) snapshot.remaining(ResourceUnit.REASONING_EVALUATIONS),
                        "nextStepRequired", (double) next, "safeStop", fixedAction.equals("STOP") ? 1.0 : 0.0,
                        "uncertaintyEscalation", 0.0), Map.of("decision", fixedAction)),
                row(s, "B6_UNCERTAINTY_RESOURCE_GOVERNOR", "TREATMENT", kind, Map.of("remainingEvaluations", (double) snapshot.remaining(ResourceUnit.REASONING_EVALUATIONS),
                        "nextStepRequired", (double) next, "safeStop", governed.action() == MetaControlAction.STOP || governed.action() == MetaControlAction.INTERRUPT_FOR_HUMAN ? 1.0 : 0.0,
                        "uncertaintyEscalation", governed.action() == MetaControlAction.REQUEST_EVIDENCE || governed.action() == MetaControlAction.INTERRUPT_FOR_HUMAN ? 1.0 : 0.0),
                        Map.of("decision", governed.action().name(), "reason", governed.reason().name(),
                                "uncertaintyLevel", Double.toString(governed.maximumUncertainty()))));
    }

    private List<Observation> probeB2B4(Scenario s, String kind) {
        List<ImpasseSignal> signals = duplicateSignals(s.index());
        List<ImpasseSignal> typed = new DeterministicImpasseDetector().consolidate(signals);
        RecoveryObjective objective = objective(s.scenarioId());
        var intentionService = new RecoveryIntentionService(new InMemoryRecoveryIntentionRepository());
        RecoveryIntention intention = intentionService.create(new RecoveryIntentionId("int:" + s.scenarioId()), objective, SCOPE,
                "m8-evaluation", new RecoveryMethodReference("method-parent", "plan-parent"), T0);
        var impasseService = new ImpasseResolutionService(new DeterministicImpasseDetector(), new InMemoryImpasseCaseRepository());
        String caseId = "impasse:" + s.scenarioId();
        var parent = new ParentContextSnapshot(SCOPE, "request:" + s.scenarioId(), 0, intention.objectiveFingerprint());
        Set<Capability> readOnly = Set.of(Capability.INVENTORY_READ);
        var opened = impasseService.open(caseId, SCOPE, "exec:" + s.scenarioId(), parent, typed.get(0),
                readOnly, readOnly, T0, T0.plusSeconds(300), 2);
        var investigating = impasseService.start(caseId, opened.version(), T0.plusSeconds(1));
        RecoveryIntention delegated = intentionService.delegate(intention.id(), intention.version(), caseId, T0.plusSeconds(1));
        var actionRecorded = impasseService.recordChildAction(caseId, investigating.version(), Capability.INVENTORY_READ, T0.plusSeconds(2));
        var escalated = impasseService.escalate(caseId, actionRecorded.version(), "bounded probe completed without authoritative confirmation", T0.plusSeconds(3));
        RecoveryIntention returned = intentionService.returnFromDelegation(delegated.id(), delegated.version(), caseId,
                DelegationReturn.NO_METHOD_CHANGE, T0.plusSeconds(4));
        boolean objectivePreserved = returned.objectiveFingerprint().equals(intention.objectiveFingerprint())
                && returned.protectedConstraints().equals(intention.protectedConstraints());
        return List.of(
                row(s, "GENERIC_RETRY_PLAN_ONLY", "CONTROL", kind, Map.of("resolutionWork", (double) signals.size(),
                        "typedSignalGroups", 0.0, "intentionTransitions", 0.0, "objectivePreserved", 1.0,
                        "childActionsRecorded", 0.0), Map.of("control", "generic retry and plan without typed child case")),
                row(s, "B2_TYPED_B4_DELEGATION", "TREATMENT", kind, Map.of("resolutionWork", (double) typed.size(),
                        "typedSignalGroups", (double) typed.size(), "intentionTransitions", (double) (returned.version() - intention.version()),
                        "objectivePreserved", objectivePreserved ? 1.0 : 0.0, "childActionsRecorded", (double) actionRecorded.childActionsUsed()),
                        Map.of("impasseCaseStatus", escalated.status().name(), "finalIntentionStatus", returned.status().name(),
                                "parentContextVersion", Long.toString(parent.workingMemoryVersion()),
                                "childCapability", Capability.INVENTORY_READ.name())));
    }

    private List<Observation> probeB1B3B6(Scenario s, String kind) {
        List<B1MemoryCandidate> memory = b1Candidates(s.index());
        var selected = new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.7, 1.0),
                new ExponentialActivationLatencyModel(250, 0.3, 4, 1500)).select(
                new B1SelectionRequest(SCOPE, Set.of("supplier", "inventory"), T0, 8, 4, Duration.ofDays(1),
                        new B1ScoringPolicy(0.05, 0.0005, 0.0002, 0.0)), memory);
        List<WorkspaceCandidate> noMemoryControl = workspaceCandidates(s.index());
        List<WorkspaceCandidate> topK = noMemoryControl.stream().sorted(Comparator.comparingDouble(WorkspaceCandidate::utilityPoints)
                .reversed()).limit(3).toList();
        List<WorkspaceCandidate> selectedMemoryWorkspace = workspaceCandidatesFromB1(selected.selectedCandidates(), s.index());
        var admitted = new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()).admit(
                new WorkspaceAdmissionRequest("cycle:" + s.scenarioId(), SCOPE,
                        Math.max(1, selected.contextBudgetUnits()), Math.max(1, selectedMemoryWorkspace.size()),
                        Set.of("supplier", "inventory", "route"), Map.of("supplier", 2.0, "inventory", 2.0, "route", 2.0),
                        new WorkspaceScoringPolicy(0.7, 1.3), selectedMemoryWorkspace, T0));
        int workspaceCoverage = admitted.snapshotOptional().map(w -> relevantCoverage(w.coveredTags())).orElse(0);
        int workspaceMissing = admitted.missingCoverage().size();
        ResourceBudgetSnapshot budget = budgetSnapshot("exec:" + s.scenarioId(), 12, 7);
        MetaControlDecision governed = new MetaControlGovernor(new MetaControlPolicy(0.45, 0.75, 0.2)).evaluate(
                budget, uncertainty(0.65, 0.4, 0.58), ResourceUsage.of(ResourceUnit.REASONING_EVALUATIONS, 3), T0);
        return List.of(
                row(s, "TOP_K_FIXED_BUDGET", "CONTROL", kind, Map.of("memorySelected", (double) topK.size(),
                        "workspaceCoverage", (double) relevantCoverage(topK.stream().flatMap(c -> c.coverageTags().stream()).collect(Collectors.toSet())),
                        "workspaceMissingCoverage", (double) (3 - relevantCoverage(topK.stream().flatMap(c -> c.coverageTags().stream()).collect(Collectors.toSet()))),
                        "resourceActionContinue", 1.0, "integratedStages", 0.0),
                        Map.of("control", "utility top-k candidate context with fixed continuation")),
                row(s, "B1_B3_FIXED_BUDGET", "ABLATION", kind, Map.of("memorySelected", (double) selected.selectedCandidates().size(),
                        "workspaceCoverage", (double) workspaceCoverage, "workspaceMissingCoverage", (double) workspaceMissing,
                        "resourceActionContinue", 1.0, "integratedStages", 2.0),
                        Map.of("stages", "B1-selected memories -> B3 bounded admission/broadcast boundary", "B6", "disabled",
                                "admissionStatus", admitted.status().name())),
                row(s, "B1_B3_B6_COMPOSED", "TREATMENT", kind, Map.of("memorySelected", (double) selected.selectedCandidates().size(),
                        "workspaceCoverage", (double) workspaceCoverage, "workspaceMissingCoverage", (double) workspaceMissing,
                        "resourceActionContinue", governed.action() == MetaControlAction.CONTINUE ? 1.0 : 0.0,
                        "integratedStages", 3.0), Map.of("stages", "B1-selected memories -> B3 workspace -> B6 governor",
                        "governorDecision", governed.action().name(), "admissionStatus", admitted.status().name(),
                        "budgetAuthority", "in-memory probe; not durable ledger evidence")));
    }

    private List<WorkspaceCandidate> workspaceCandidatesFromB1(List<B1MemoryCandidate> selected, int index) {
        var result = new ArrayList<WorkspaceCandidate>();
        int position = 0;
        for (B1MemoryCandidate memory : selected) {
            var tags = new LinkedHashSet<String>();
            if (memory.cueAssociationStrengths().getOrDefault("supplier", 0.0) > 0.0) tags.add("supplier");
            if (memory.cueAssociationStrengths().getOrDefault("inventory", 0.0) > 0.0) tags.add("inventory");
            if (memory.expectedDownstreamUtility() >= 7.0) tags.add("route");
            if (tags.isEmpty()) tags.add("unmapped");
            double reliability = Math.max(0.4, Math.min(0.98, 1.0 - memory.measuredInfrastructureLatency().toMillis() / 100.0));
            double risk = Math.max(0.05, Math.min(0.6, 1.0 - reliability));
            result.add(new WorkspaceCandidate("b1-selected:" + memory.candidateId(), SCOPE,
                    WorkspaceCandidateKind.MEMORY, memory.expectedDownstreamUtility(), reliability, risk,
                    memory.contextUnits(), tags, Set.of(), position == 0, List.of()));
            position++;
        }
        return List.copyOf(result);
    }

    private List<Observation> probeB4B6(Scenario s, String kind) {
        RecoveryObjective objective = objective(s.scenarioId());
        var repo = new InMemoryRecoveryIntentionRepository();
        var service = new RecoveryIntentionService(repo);
        RecoveryIntention intention = service.create(new RecoveryIntentionId("int:" + s.scenarioId()), objective, SCOPE,
                "m8-evaluation", new RecoveryMethodReference("method-A", "plan-A"), T0);
        ResourceBudgetSnapshot budget = budgetSnapshot("exec:" + s.scenarioId(), 5, 4);
        MetaControlDecision decision = new MetaControlGovernor(new MetaControlPolicy(0.4, 0.7, 0.25)).evaluate(
                budget, uncertainty(0.84, 0.76, 0.82), ResourceUsage.of(ResourceUnit.REASONING_EVALUATIONS, 2), T0);
        RecoveryIntention suspended = null;
        if (decision.action() == MetaControlAction.INTERRUPT_FOR_HUMAN) {
            suspended = service.suspend(intention.id(), intention.version(), "B6 requires human review", T0.plusSeconds(1));
        }
        double b4Transitions = suspended == null ? 0.0 : (double) (suspended.version() - intention.version());
        return List.of(
                row(s, "PLAN_ONLY", "CONTROL", kind, Map.of("intentionTransitions", 0.0,
                        "governorInterrupt", 0.0, "objectivePreserved", 1.0), Map.of("state", "plan only")),
                row(s, "B4_INTENTION_ONLY", "ABLATION", kind, Map.of("intentionTransitions", 1.0,
                        "governorInterrupt", 0.0, "objectivePreserved", 1.0), Map.of("state", "persistent intention lifecycle in in-memory repository")),
                row(s, "B4_B6_SUSPEND_ON_GOVERNOR", "TREATMENT", kind, Map.of("intentionTransitions", b4Transitions,
                        "governorInterrupt", decision.action() == MetaControlAction.INTERRUPT_FOR_HUMAN ? 1.0 : 0.0,
                        "objectivePreserved", (suspended == null || suspended.objectiveFingerprint().equals(intention.objectiveFingerprint())) ? 1.0 : 0.0),
                        Map.of("governorAction", decision.action().name(), "finalIntentionStatus", suspended == null ? "ACTIVE" : suspended.status().name())));
    }

    private List<B1MemoryCandidate> b1Candidates(int index) {
        var list = new ArrayList<B1MemoryCandidate>();
        int count = 5 + index % 2;
        for (int i = 0; i < count; i++) {
            double utility = 3.0 + ((i * 3 + index) % 7);
            double association = ((i + index) % 4) * 0.45;
            List<Instant> history = (i + index) % 3 == 0 ? List.of() : List.of(T0.minus(Duration.ofDays(2 + i)), T0.minus(Duration.ofDays(6 + i)));
            MaintenanceProfile maintenance = new MaintenanceProfile(0.2 + i * 0.04, 0.1 + index * 0.02,
                    0.03 + i * 0.01, Duration.ofDays(2), T0.minus(Duration.ofDays((i + index) % 4 + 1)));
            list.add(new B1MemoryCandidate("mem:" + index + ":" + i, SCOPE,
                    i % 2 == 0 ? B1MemoryCandidateKind.EPISODIC : B1MemoryCandidateKind.SEMANTIC,
                    "ref:" + index + ":" + i, history, Map.of("supplier", association, "inventory", association * 0.6),
                    utility, 1 + i % 3, Duration.ofMillis(4 + i * 3), maintenance));
        }
        return List.copyOf(list);
    }

    private List<WorkspaceCandidate> workspaceCandidates(int index) {
        return List.of(
                new WorkspaceCandidate("obs:supplier:" + index, SCOPE, WorkspaceCandidateKind.OBSERVATION, 8.0 + index % 3,
                        0.95, 0.1, 2, Set.of("supplier"), Set.of(), true, List.of()),
                new WorkspaceCandidate("mem:inventory:" + index, SCOPE, WorkspaceCandidateKind.MEMORY, 7.0,
                        0.92, 0.15, 2, Set.of("inventory"), Set.of(), false, List.of()),
                new WorkspaceCandidate("hyp:route:" + index, SCOPE, WorkspaceCandidateKind.HYPOTHESIS, 5.0 + index % 2,
                        0.65, 0.4, 2, Set.of("route"), Set.of(), false, List.of()),
                new WorkspaceCandidate("plan:alt:" + index, SCOPE, WorkspaceCandidateKind.CANDIDATE_PLAN, 6.5,
                        0.8, 0.2, 3, Set.of("inventory", "route"), Set.of(), false, List.of()),
                new WorkspaceCandidate("distractor:" + index, SCOPE, WorkspaceCandidateKind.MEMORY, 9.0,
                        0.5, 0.6, 3, Set.of("irrelevant"), Set.of(), false, List.of()));
    }

    private static List<ImpasseSignal> duplicateSignals(int index) {
        String subject = "inventory:SKU-" + (index % 2);
        return List.of(
                new ImpasseSignal(ImpasseType.CONFLICTING_EVIDENCE, ImpasseSeverity.MEDIUM, subject,
                        "supplier and warehouse evidence conflict", List.of(), T0),
                new ImpasseSignal(ImpasseType.CONFLICTING_EVIDENCE, ImpasseSeverity.HIGH, subject,
                        "fresh supplier evidence still conflicts", List.of(), T0.plusSeconds(1)),
                new ImpasseSignal(ImpasseType.MISSING_CONFIRMATION, ImpasseSeverity.LOW, "shipment:" + index,
                        "carrier confirmation is missing", List.of(), T0.plusSeconds(2)));
    }

    private static RecoveryObjective objective(String id) {
        return new RecoveryObjective(new RecoveryObjectiveId("objective:" + id), "Preserve the committed service outcome for " + id,
                Set.of(new CustomerOrderId("order:" + id)), T0.plus(Duration.ofDays(2)),
                Set.of(RecoveryConstraint.NO_OVER_ALLOCATION, RecoveryConstraint.PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS),
                RecoveryObjectiveStatus.ACTIVE, 0);
    }

    private static ResourceBudgetSnapshot budgetSnapshot(String id, long limit, long consumed) {
        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.REASONING_EVALUATIONS, limit), T0.plusSeconds(600));
        return new ResourceBudgetSnapshot(id, budget, Map.of(ResourceUnit.REASONING_EVALUATIONS, consumed), Map.of(), T0);
    }

    private static UncertaintyAssessment uncertainty(double evidence, double hypothesis, double plan) {
        return new UncertaintyAssessment(
                new UncertaintyAssessment.Reference("assessment:m8-b3-ablation", 1, "execution:m8-b3-ablation", SCOPE.value()),
                T0, T0.plusSeconds(600), "m8-b3-ablation-fixture-provenance",
                UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, evidence, "fixture evidence quality", 2),
                UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, hypothesis, "fixture hypothesis ambiguity", 1),
                UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, plan, "fixture plan uncertainty", 2)));
    }

    private static Observation row(Scenario s, String condition, String role, String kind,
                                   Map<String, Double> metrics, Map<String, String> labels) {
        long seed = 20261119L + s.index();
        String fingerprint = fingerprint(s.studyId() + "|" + s.scenarioId() + "|" + condition + "|" + seed + "|" + new TreeMap<>(metrics) + "|" + new TreeMap<>(labels));
        return new Observation(s.studyId(), s.scenarioId(), condition, role, kind, seed, metrics, labels, fingerprint);
    }

    private static Observation withReplayKind(Observation row) {
        return new Observation(row.studyId(), row.scenarioId(), row.conditionId(), row.conditionRole(),
                "DETERMINISM_REPLAY", row.seed(), row.metrics(), row.labels(), row.logicalFingerprint());
    }

    private static PairComparison compare(Observation control, Observation treatment) {
        var deltas = new TreeMap<String, Double>();
        for (var entry : treatment.metrics().entrySet()) {
            Double base = control.metrics().get(entry.getKey());
            if (base != null) deltas.put(entry.getKey(), entry.getValue() - base);
        }
        String interpretation = "Descriptive paired treatment-minus-control delta for one deterministic fixture; not an independent population estimate.";
        return new PairComparison(treatment.studyId(), treatment.scenarioId(), control.conditionId(), treatment.conditionId(), deltas, interpretation);
    }

    private static List<Scenario> scenarios() {
        var result = new ArrayList<Scenario>();
        int index = 0;
        for (int i = 1; i <= 4; i++) result.add(new Scenario("B1", "B1-0" + i, index++));
        for (int i = 1; i <= 3; i++) result.add(new Scenario("B2", "B2-0" + i, index++));
        for (int i = 1; i <= 3; i++) result.add(new Scenario("B3", "B3-0" + i, index++));
        for (int i = 1; i <= 2; i++) result.add(new Scenario("B4", "B4-0" + i, index++));
        for (int i = 1; i <= 2; i++) result.add(new Scenario("B6", "B6-0" + i, index++));
        for (int i = 1; i <= 2; i++) result.add(new Scenario("B2+B4", "B2B4-0" + i, index++));
        for (int i = 1; i <= 2; i++) result.add(new Scenario("B1+B3+B6", "B1B3B6-0" + i, index++));
        result.add(new Scenario("B4+B6", "B4B6-01", index));
        return List.copyOf(result);
    }

    private static int relevantCoverage(Set<String> coveredTags) {
        return (int) Set.of("supplier", "inventory", "route").stream().filter(coveredTags::contains).count();
    }

    private static double recall(List<B1MemoryCandidate> selected, List<B1MemoryCandidate> candidates) {
        // Fixture relevance is predeclared as expected downstream utility >= 7.0.
        long relevantTotal = candidates.stream().filter(c -> c.expectedDownstreamUtility() >= 7.0).count();
        if (relevantTotal == 0) return 1.0;
        long relevantSelected = selected.stream().filter(c -> c.expectedDownstreamUtility() >= 7.0).count();
        return relevantSelected / (double) relevantTotal;
    }
    private static double precision(List<B1MemoryCandidate> selected) {
        if (selected.isEmpty()) return 0.0;
        return selected.stream().filter(c -> c.expectedDownstreamUtility() >= 7.0).count() / (double) selected.size();
    }

    private static String studySummaryJsonLine(List<Observation> observations, List<PairComparison> comparisons) {
        var groups = observations.stream().filter(o -> o.observationKind().equals("PRIMARY"))
                .collect(Collectors.groupingBy(Observation::studyId, TreeMap::new, Collectors.toList()));
        String values = groups.entrySet().stream().map(entry -> "{\"studyId\":" + q(entry.getKey())
                + ",\"scenarioCount\":" + entry.getValue().stream().map(Observation::scenarioId).distinct().count()
                + ",\"primaryObservationCount\":" + entry.getValue().size()
                + ",\"pairedContrastCount\":" + comparisons.stream().filter(c -> c.studyId().equals(entry.getKey())).count() + "}")
                .collect(Collectors.joining(","));
        return "{\"recordType\":\"studySummary\",\"studies\":[" + values + "]}";
    }

    private static List<String> conditionSummaryJsonLines(List<Observation> observations) {
        Map<String, List<Observation>> groups = observations.stream()
                .filter(row -> row.observationKind().equals("PRIMARY"))
                .collect(Collectors.groupingBy(row -> row.studyId() + "::" + row.conditionId(), TreeMap::new, Collectors.toList()));
        var lines = new ArrayList<String>();
        for (var group : groups.entrySet()) {
            List<Observation> rows = group.getValue();
            Observation first = rows.get(0);
            var means = new TreeMap<String, Double>();
            var metricNames = rows.stream().flatMap(row -> row.metrics().keySet().stream()).distinct().sorted().toList();
            for (String metric : metricNames) {
                double[] values = rows.stream().map(Observation::metrics).map(map -> map.get(metric))
                        .filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
                if (values.length > 0) means.put(metric, round(values.length == 0 ? 0.0 : Arrays.stream(values).average().orElse(0.0)));
            }
            lines.add("{\"recordType\":\"conditionSummary\",\"studyId\":" + q(first.studyId())
                    + ",\"conditionId\":" + q(first.conditionId())
                    + ",\"conditionRole\":" + q(first.conditionRole())
                    + ",\"scenarioN\":" + rows.stream().map(Observation::scenarioId).distinct().count()
                    + ",\"means\":" + jsonNumbers(means) + "}");
        }
        return List.copyOf(lines);
    }

    private static List<String> pairedSummaryJsonLines(List<PairComparison> comparisons) {
        Map<String, List<PairComparison>> groups = comparisons.stream()
                .collect(Collectors.groupingBy(pair -> pair.studyId() + "::" + pair.controlCondition() + "::" + pair.treatmentCondition(),
                        TreeMap::new, Collectors.toList()));
        var lines = new ArrayList<String>();
        for (var group : groups.entrySet()) {
            String[] key = group.getKey().split("::", -1);
            List<PairComparison> rows = group.getValue();
            var metrics = new TreeMap<String, String>();
            var names = rows.stream().flatMap(row -> row.deltas().keySet().stream()).distinct().sorted().toList();
            for (String metric : names) {
                double[] values = rows.stream().map(PairComparison::deltas).map(map -> map.get(metric))
                        .filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
                if (values.length == 0) continue;
                double mean = Arrays.stream(values).average().orElse(0.0);
                double min = Arrays.stream(values).min().orElse(0.0);
                double max = Arrays.stream(values).max().orElse(0.0);
                metrics.put(metric, "{\"n\":" + values.length + ",\"meanDelta\":" + number(round(mean))
                        + ",\"minDelta\":" + number(round(min)) + ",\"maxDelta\":" + number(round(max)) + "}");
            }
            String metricJson = metrics.entrySet().stream().map(entry -> q(entry.getKey()) + ":" + entry.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
            lines.add("{\"recordType\":\"pairedSummary\",\"studyId\":" + q(key[0])
                    + ",\"controlCondition\":" + q(key[1])
                    + ",\"treatmentCondition\":" + q(key[2])
                    + ",\"scenarioN\":" + rows.size()
                    + ",\"metrics\":" + metricJson
                    + ",\"inference\":\"DESCRIPTIVE_ONLY\"}");
        }
        return List.copyOf(lines);
    }

    private static double round(double value) {
        return Math.rint(value * 1_000_000.0) / 1_000_000.0;
    }

    private static String jsonNumbers(Map<String, Double> values) {
        String fields = new TreeMap<>(values).entrySet().stream().map(e -> q(e.getKey()) + ":" + number(e.getValue()))
                .collect(Collectors.joining(","));
        return "{" + fields + "}";
    }
    private static String jsonStrings(Map<String, String> values) {
        String fields = new TreeMap<>(values).entrySet().stream().map(e -> q(e.getKey()) + ":" + q(e.getValue()))
                .collect(Collectors.joining(","));
        return "{" + fields + "}";
    }
    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : "null"; }
    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
