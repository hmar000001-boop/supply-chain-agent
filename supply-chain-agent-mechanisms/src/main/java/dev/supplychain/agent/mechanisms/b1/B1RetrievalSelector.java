package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** B1 engineering realization: activation/accessibility, distinct latency measures, downstream value and maintenance cost. */
public final class B1RetrievalSelector {
    private final ActivationEstimator activationEstimator;
    private final CognitiveRetrievalLatencyModel latencyModel;
    private final dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment outcomeAdjustment;
    public B1RetrievalSelector(ActivationEstimator activationEstimator, CognitiveRetrievalLatencyModel latencyModel) {
        this(activationEstimator, latencyModel, dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment.neutral());
    }
    /** Outcome feedback is opt-in and should be supplied only by the trusted activation boundary. */
    public B1RetrievalSelector(ActivationEstimator activationEstimator, CognitiveRetrievalLatencyModel latencyModel,
            dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment outcomeAdjustment) {
        this.activationEstimator = Objects.requireNonNull(activationEstimator, "activationEstimator");
        this.latencyModel = Objects.requireNonNull(latencyModel, "latencyModel");
        this.outcomeAdjustment = Objects.requireNonNull(outcomeAdjustment, "outcomeAdjustment");
    }
    public B1SelectionResult select(B1SelectionRequest request, List<B1MemoryCandidate> candidates) {
        Objects.requireNonNull(request, "request"); Objects.requireNonNull(candidates, "candidates");
        var ids = new HashSet<String>();
        var raw = new ArrayList<B1CandidateScore>();
        var byId = new java.util.HashMap<String, B1MemoryCandidate>();
        for (var candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (!request.scopeId().equals(candidate.scopeId())) throw new SecurityException("B1 candidate scope does not match request scope");
            if (!ids.add(candidate.candidateId())) throw new IllegalArgumentException("duplicate B1 candidate ID: " + candidate.candidateId());
            var activation = activationEstimator.estimate(new ActivationInput(candidate.accessHistory(),
                    candidate.cueAssociationStrengths(), request.queryCues(), request.evaluatedAt()));
            double probability = 1.0 / (1.0 + Math.exp(-Math.max(-40.0, Math.min(40.0, activation.totalActivation()))));
            Duration cognitiveLatency = latencyModel.predict(activation);
            if (cognitiveLatency == null || cognitiveLatency.isNegative()) throw new IllegalStateException("latency model returned invalid duration");
            double maintenance = candidate.maintenanceProfile().estimateCost(request.evaluatedAt(), request.maintenanceHorizon());
            double cognitivePenalty = durationMillis(cognitiveLatency) * request.policy().cognitiveLatencyPenaltyPerMillisecond();
            double infrastructurePenalty = durationMillis(candidate.measuredInfrastructureLatency())
                    * request.policy().infrastructureLatencyPenaltyPerMillisecond();
            double adjustment = outcomeAdjustment.adjustmentFor(candidate.scopeId(), candidate.candidateId());
            if (!Double.isFinite(adjustment) || Math.abs(adjustment) > 5.0) {
                throw new SecurityException("B1 outcome feedback adjustment is non-finite or exceeds its safety bound");
            }
            double net = probability * candidate.expectedDownstreamUtility() - cognitivePenalty - infrastructurePenalty - maintenance + adjustment;
            if (!Double.isFinite(net)) throw new IllegalStateException("B1 score is not finite");
            raw.add(new B1CandidateScore(candidate.candidateId(), activation.totalActivation(), probability, cognitiveLatency,
                    candidate.measuredInfrastructureLatency(), candidate.expectedDownstreamUtility(), maintenance, net,
                    candidate.contextUnits(), false));
            byId.put(candidate.candidateId(), candidate);
        }
        raw.sort(Comparator.comparingDouble(B1CandidateScore::netUtility).reversed().thenComparing(B1CandidateScore::candidateId));
        int used = 0;
        var selected = new ArrayList<B1MemoryCandidate>();
        var selectedIds = new HashSet<String>();
        int requiredKinds = request.uncertaintyDirective().minimumDistinctMemoryKinds();
        Set<B1MemoryCandidateKind> selectedKinds = new HashSet<>();
        for (var score : raw) {
            if (selected.size() >= request.maximumCandidates()) break;
            if (score.accessibilityProbability() < request.policy().minimumAccessibilityProbability()) continue;
            if (score.netUtility() <= request.policy().minimumNetUtility()) continue;
            if (score.contextUnits() > request.contextBudgetUnits() - used) continue;
            B1MemoryCandidate candidate = byId.get(score.candidateId());
            // Under a validated high evidence-uncertainty profile, prefer distinct memory types
            // before filling remaining slots. This is a bounded diversity floor, not a probability claim.
            if (selectedKinds.size() < requiredKinds && selectedKinds.contains(candidate.kind())) continue;
            selected.add(candidate);
            selectedIds.add(score.candidateId());
            selectedKinds.add(candidate.kind());
            used += score.contextUnits();
        }
        if (selectedKinds.size() < requiredKinds) {
            for (var score : raw) {
                if (selected.size() >= request.maximumCandidates()) break;
                if (selectedIds.contains(score.candidateId())) continue;
                B1MemoryCandidate candidate = byId.get(score.candidateId());
                if (selectedKinds.contains(candidate.kind())) continue;
                if (score.accessibilityProbability() < request.policy().minimumAccessibilityProbability()
                        || score.netUtility() <= request.policy().minimumNetUtility()
                        || score.contextUnits() > request.contextBudgetUnits() - used) continue;
                selected.add(candidate); selectedIds.add(score.candidateId());
                selectedKinds.add(candidate.kind()); used += score.contextUnits();
            }
        }
        var finalScores = raw.stream().map(s -> new B1CandidateScore(s.candidateId(), s.activation(), s.accessibilityProbability(),
                s.predictedCognitiveLatency(), s.measuredInfrastructureLatency(), s.expectedDownstreamUtility(),
                s.estimatedMaintenanceCost(), s.netUtility(), s.contextUnits(), selectedIds.contains(s.candidateId()))).toList();
        return new B1SelectionResult(selected, finalScores, used, request.contextBudgetUnits(),
                requiredKinds, selectedKinds.size());
    }

    private static double durationMillis(Duration duration) {
        return duration.getSeconds() * 1_000.0 + duration.getNano() / 1_000_000.0;
    }
}
