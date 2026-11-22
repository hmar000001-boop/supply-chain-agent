package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.mechanism.B6CalibrationRunEvidence;
import dev.supplychain.agent.evaluation.experiment.mechanism.B6HoldoutEvaluationReport;
import dev.supplychain.agent.evaluation.experiment.mechanism.B6SimulatorLabelEvidence;
import dev.supplychain.agent.evaluation.experiment.mechanism.B6TransferEvaluationReport;
import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismSpecificExperimentPair;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Confirmatory paired analysis over independently verified endpoint records and independently
 * verified B6 simulator labels. This class never derives B1/B2/B3/B4 primary endpoints from raw
 * graph telemetry or from an LLM judge.
 */
public final class M8ConfirmatoryAnalysisEngine {
    public static final String ANALYSIS_VERSION = "M8_CONFIRMATORY_ANALYSIS_V1";
    private static final int HOLDOUT_UNITS = 280;
    private static final int TRANSFER_UNITS = 210;
    private static final Map<String, Set<M8VerifiedEndpointEvidence.EndpointId>> REQUIRED_ENDPOINTS = requiredEndpoints();

    private final M8VerifiedEndpointEvidenceVerifier endpointVerifier;
    private final dev.supplychain.agent.evaluation.experiment.mechanism.SimulatorUncertaintyLabelVerifier b6LabelVerifier;
    private final M8ExperimentSafetyEvidenceVerifier safetyEvidenceVerifier;
    private final M8PairedStatisticalEngine statistics = new M8PairedStatisticalEngine();

    public M8ConfirmatoryAnalysisEngine(M8VerifiedEndpointEvidenceVerifier endpointVerifier,
            dev.supplychain.agent.evaluation.experiment.mechanism.SimulatorUncertaintyLabelVerifier b6LabelVerifier,
            M8ExperimentSafetyEvidenceVerifier safetyEvidenceVerifier) {
        this.endpointVerifier = Objects.requireNonNull(endpointVerifier, "endpointVerifier");
        this.b6LabelVerifier = Objects.requireNonNull(b6LabelVerifier, "b6LabelVerifier");
        this.safetyEvidenceVerifier = Objects.requireNonNull(safetyEvidenceVerifier, "safetyEvidenceVerifier");
        if (endpointVerifier.verifierId() == null || endpointVerifier.verifierId().isBlank())
            throw new IllegalArgumentException("independent endpoint verifier must have a stable identity");
        if (safetyEvidenceVerifier.verifierId() == null || safetyEvidenceVerifier.verifierId().isBlank())
            throw new IllegalArgumentException("independent safety evidence verifier must have a stable identity");
    }

    public AnalysisReport analyzeHoldout(List<M8VerifiedEndpointEvidence> endpointEvidence,
            List<MechanismSpecificExperimentPair> b6Pairs, M8ExperimentSafetyEvidence safetyEvidence) {
        return analyze(ExperimentSplit.HOLDOUT, endpointEvidence, b6Pairs, safetyEvidence);
    }

    public AnalysisReport analyzeTransfer(List<M8VerifiedEndpointEvidence> endpointEvidence,
            List<MechanismSpecificExperimentPair> b6Pairs, M8ExperimentSafetyEvidence safetyEvidence) {
        return analyze(ExperimentSplit.TRANSFER, endpointEvidence, b6Pairs, safetyEvidence);
    }

    private AnalysisReport analyze(ExperimentSplit split, List<M8VerifiedEndpointEvidence> suppliedEndpoints,
            List<MechanismSpecificExperimentPair> suppliedB6Pairs, M8ExperimentSafetyEvidence safetyEvidence) {
        if (split != ExperimentSplit.HOLDOUT && split != ExperimentSplit.TRANSFER)
            throw new SecurityException("confirmatory analysis accepts HOLDOUT or TRANSFER only");
        int expectedCount = split == ExperimentSplit.HOLDOUT ? HOLDOUT_UNITS : TRANSFER_UNITS;
        List<ExperimentScenarioUnit> expectedUnits = M8ExperimentScenarioFactory.loadDefault().generate(split);
        if (expectedUnits.size() != expectedCount) throw new IllegalStateException("preregistered split count changed");
        Map<String, ExperimentScenarioUnit> expectedById = expectedUnits.stream().collect(Collectors.toMap(
                ExperimentScenarioUnit::experimentUnitId, unit -> unit));
        Map<String, ExperimentScenarioUnit> expectedByFingerprint = expectedUnits.stream().collect(Collectors.toMap(
                ExperimentScenarioUnit::effectiveInputFingerprint, unit -> unit));

        validateSafetyEvidence(split, expectedCount, safetyEvidence);
        Map<M8VerifiedEndpointEvidence.EndpointId, Map<String, M8VerifiedEndpointEvidence>> evidenceByEndpoint =
                validateEndpoints(split, expectedById, suppliedEndpoints);
        Map<String, MechanismSpecificExperimentPair> b6ByUnit = validateB6Pairs(split, expectedByFingerprint,
                suppliedB6Pairs);

        var results = new LinkedHashMap<String, HypothesisResult>();
        var rawP = new LinkedHashMap<String, Double>();
        for (String hypothesis : List.of("H1", "H2", "H3", "H4")) {
            M8VerifiedEndpointEvidence.EndpointId primary = primaryEndpoint(hypothesis);
            List<M8PairedStatisticalEngine.PairedValue> values = new ArrayList<>(expectedCount);
            Map<String, M8VerifiedEndpointEvidence> endpointMap = evidenceByEndpoint.get(primary);
            for (ExperimentScenarioUnit unit : expectedUnits) {
                M8VerifiedEndpointEvidence evidence = endpointMap.get(unit.experimentUnitId());
                String cluster = split == ExperimentSplit.TRANSFER ? unit.scenarioFamily() : "";
                values.add(new M8PairedStatisticalEngine.PairedValue(unit.experimentUnitId(), cluster,
                        unit.fixtureProfile(), evidence.orientedImprovement()));
            }
            M8PairedStatisticalEngine.Result stats = statistics.analyze(values,
                    M8ExperimentScenarioFactory.PROTOCOL_ID + "|" + split + "|" + hypothesis + "|" + ANALYSIS_VERSION,
                    primary.binary());
            double baselineMean = meanEndpoint(endpointMap.values(), true);
            double treatmentMean = meanEndpoint(endpointMap.values(), false);
            Map<M8VerifiedEndpointEvidence.EndpointId, M8VerifiedEndpointEvidence[]> paired = pairEndpointMeans(
                    hypothesis, evidenceByEndpoint);
            MpeAssessment mpe = assessMpe(hypothesis, baselineMean, treatmentMean, stats, paired);
            results.put(hypothesis, new HypothesisResult(hypothesis, primary.name(), baselineMean,
                    treatmentMean, stats, null, mpe.status(), mpe.explanation()));
            rawP.put(hypothesis, stats.twoSidedPValue());
        }

        B6Summary b6 = summarizeB6(split, expectedUnits, b6ByUnit);
        results.put("H5", new HypothesisResult("H5", "B6_MEAN_PER_EPISODE_BRIER_LOSS_IMPROVEMENT",
                b6.rawMeanBrier(), b6.shadowMeanBrier(), b6.stats(), b6.calibrationMetrics(),
                b6.mpeStatus(), b6.mpeExplanation()));
        rawP.put("H5", b6.stats().twoSidedPValue());

        Map<String, Double> adjusted = split == ExperimentSplit.HOLDOUT ? holm(rawP) : Map.of();
        var orderedResults = new LinkedHashMap<String, HypothesisResult>();
        for (String hypothesis : List.of("H1", "H2", "H3", "H4", "H5")) {
            HypothesisResult result = results.get(hypothesis);
            orderedResults.put(hypothesis, new HypothesisResult(result.hypothesisId(), result.primaryEndpoint(),
                    result.baselineMean(), result.treatmentMean(), result.statistics(), result.b6CalibrationMetrics(),
                    result.mpeStatus(), result.mpeExplanation(), rawP.get(hypothesis), adjusted.get(hypothesis)));
        }
        boolean allMpeSatisfied = orderedResults.values().stream().allMatch(value -> value.mpeStatus() == MpeStatus.SATISFIED);
        String evidenceClass = "SIMULATOR_ONLY_NOT_REAL_WORLD_OUTCOME_EVIDENCE";
        return new AnalysisReport(ANALYSIS_VERSION, M8ExperimentScenarioFactory.PROTOCOL_ID,
                M8ExperimentScenarioFactory.GENERATOR_ID, split, expectedCount, orderedResults,
                split == ExperimentSplit.HOLDOUT, split == ExperimentSplit.HOLDOUT,
                split == ExperimentSplit.HOLDOUT ? "HOLM_STEP_DOWN_ACROSS_H1_H5" : "TRANSFER_DIAGNOSTIC_NO_CONFIRMATORY_HOLM",
                allMpeSatisfied, evidenceClass, safetyEvidence.evidenceFingerprint(),
                safetyEvidence.verifierId(), Instant.now());
    }

    private void validateSafetyEvidence(ExperimentSplit split, int expectedCount,
            M8ExperimentSafetyEvidence supplied) {
        Objects.requireNonNull(supplied, "safetyEvidence");
        if (supplied.split() != split || supplied.episodeCount() != expectedCount
                || !supplied.verifierId().equals(safetyEvidenceVerifier.verifierId()))
            throw new SecurityException("safety evidence split, exact episode count or verifier identity mismatch");
        safetyEvidenceVerifier.verify(supplied);
        supplied.requireSafe();
    }

    private Map<M8VerifiedEndpointEvidence.EndpointId, Map<String, M8VerifiedEndpointEvidence>> validateEndpoints(
            ExperimentSplit split, Map<String, ExperimentScenarioUnit> expected,
            List<M8VerifiedEndpointEvidence> supplied) {
        Objects.requireNonNull(supplied, "endpointEvidence");
        var grouped = new EnumMap<M8VerifiedEndpointEvidence.EndpointId, Map<String, M8VerifiedEndpointEvidence>>(
                M8VerifiedEndpointEvidence.EndpointId.class);
        for (M8VerifiedEndpointEvidence.EndpointId endpoint : M8VerifiedEndpointEvidence.EndpointId.values())
            grouped.put(endpoint, new TreeMap<>());
        Set<String> evidenceFingerprints = new HashSet<>();
        for (M8VerifiedEndpointEvidence evidence : List.copyOf(supplied)) {
            if (evidence.split() != split || !evidence.verifierId().equals(endpointVerifier.verifierId()))
                throw new SecurityException("endpoint record split or independent verifier identity mismatch");
            ExperimentScenarioUnit expectedUnit = expected.get(evidence.experimentUnitId());
            if (expectedUnit == null || !expectedUnit.effectiveInputFingerprint().equals(evidence.experimentUnitFingerprint())
                    || !expectedUnit.fixtureProfile().equals(evidence.fixtureProfile())
                    || !expectedUnit.scenarioFamily().equals(evidence.scenarioFamily()))
                throw new SecurityException("endpoint record is not bound to its exact preregistered split unit");
            if (!evidenceFingerprints.add(evidence.evidenceFingerprint()))
                throw new SecurityException("endpoint evidence fingerprint was reused");
            endpointVerifier.verify(evidence);
            Map<String, M8VerifiedEndpointEvidence> records = grouped.get(evidence.endpointId());
            if (records.putIfAbsent(evidence.experimentUnitId(), evidence) != null)
                throw new SecurityException("duplicate endpoint evidence for one episode and endpoint: " + evidence.endpointId());
        }
        for (M8VerifiedEndpointEvidence.EndpointId endpoint : REQUIRED_ENDPOINTS.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet())) {
            Set<String> ids = grouped.get(endpoint).keySet();
            if (ids.size() != expected.size() || !ids.equals(expected.keySet()))
                throw new SecurityException("endpoint " + endpoint + " must cover the exact " + split + " unit set");
        }
        return Map.copyOf(grouped);
    }

    private Map<String, MechanismSpecificExperimentPair> validateB6Pairs(ExperimentSplit split,
            Map<String, ExperimentScenarioUnit> expectedByFingerprint,
            List<MechanismSpecificExperimentPair> supplied) {
        List<MechanismSpecificExperimentPair> pairs = List.copyOf(Objects.requireNonNull(supplied, "b6Pairs"));
        if (pairs.size() != expectedByFingerprint.size())
            throw new SecurityException("B6 analysis must contain the exact preregistered " + split + " unit count");
        var byUnit = new TreeMap<String, MechanismSpecificExperimentPair>();
        var sampleIds = new HashSet<String>();
        var evidenceIds = new HashSet<String>();
        for (MechanismSpecificExperimentPair pair : pairs) {
            if (pair.graphPair().mechanism() != ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION)
                throw new SecurityException("H5 received a non-B6 mechanism pair");
            String fingerprint = pair.graphPair().effectiveInputFingerprint();
            ExperimentScenarioUnit expected = expectedByFingerprint.get(fingerprint);
            if (expected == null || !expected.experimentUnitId().equals(pair.graphPair().experimentUnitId())
                    || !expected.fixtureProfile().equals(pair.graphPair().fixtureProfile())
                    || !expected.scenarioFamily().equals(pair.graphPair().scenarioFamily()))
                throw new SecurityException("B6 pair is outside the exact preregistered split");
            if (byUnit.putIfAbsent(fingerprint, pair) != null)
                throw new SecurityException("duplicate B6 pair for one experiment episode");
            B6CalibrationRunEvidence baseline = (B6CalibrationRunEvidence) pair.baselineMechanismEvidence();
            B6CalibrationRunEvidence treatment = (B6CalibrationRunEvidence) pair.treatmentMechanismEvidence();
            if (pair.b6ValidationLabels().size() != UncertaintyDimension.values().length)
                throw new SecurityException("B6 pair must have exactly one independently verified label per uncertainty dimension");
            Set<UncertaintyDimension> dimensions = new HashSet<>();
            for (B6SimulatorLabelEvidence label : pair.b6ValidationLabels()) {
                var observation = label.observation();
                String evidenceId = observation.labelEvidence().evidenceType() + ":" + observation.labelEvidence().evidenceId();
                if (label.split() != split || !label.experimentUnitFingerprint().equals(fingerprint)
                        || !dimensions.add(observation.dimension()) || !sampleIds.add(observation.sampleId())
                        || !evidenceIds.add(evidenceId)
                        || Double.compare(baseline.rawValues().get(observation.dimension()), observation.predictedUncertainty()) != 0
                        || !b6LabelVerifier.verify(label))
                    throw new SecurityException("B6 independent label failed split, unit, dimension, uniqueness or verifier checks");
                double shadow = treatment.observedValues().get(observation.dimension());
                if (!Double.isFinite(shadow) || shadow < 0.0 || shadow > 1.0)
                    throw new SecurityException("B6 shadow prediction is outside [0,1]");
            }
            if (!dimensions.equals(Set.of(UncertaintyDimension.values())))
                throw new SecurityException("B6 labels do not cover all uncertainty dimensions");
        }
        if (!byUnit.keySet().equals(expectedByFingerprint.keySet()))
            throw new SecurityException("B6 pairs do not cover the exact preregistered split");
        return Map.copyOf(byUnit);
    }

    private B6Summary summarizeB6(ExperimentSplit split, List<ExperimentScenarioUnit> expectedUnits,
            Map<String, MechanismSpecificExperimentPair> pairsByFingerprint) {
        B6HoldoutEvaluationReport holdoutReport = null;
        B6TransferEvaluationReport transferReport = null;
        if (split == ExperimentSplit.HOLDOUT) {
            holdoutReport = B6HoldoutEvaluationReport.fromPairs(List.copyOf(pairsByFingerprint.values()));
        } else {
            Map<String, String> families = expectedUnits.stream().collect(Collectors.toMap(
                    ExperimentScenarioUnit::effectiveInputFingerprint, ExperimentScenarioUnit::scenarioFamily));
            transferReport = B6TransferEvaluationReport.fromPairs(List.copyOf(pairsByFingerprint.values()), families);
        }
        var samples = new ArrayList<M8PairedStatisticalEngine.PairedValue>(expectedUnits.size());
        double rawSum = 0.0;
        double shadowSum = 0.0;
        for (ExperimentScenarioUnit unit : expectedUnits) {
            MechanismSpecificExperimentPair pair = pairsByFingerprint.get(unit.effectiveInputFingerprint());
            B6CalibrationRunEvidence baseline = (B6CalibrationRunEvidence) pair.baselineMechanismEvidence();
            B6CalibrationRunEvidence treatment = (B6CalibrationRunEvidence) pair.treatmentMechanismEvidence();
            double improvementSum = 0.0;
            double rawBrier = 0.0;
            double shadowBrier = 0.0;
            for (B6SimulatorLabelEvidence label : pair.b6ValidationLabels()) {
                var observation = label.observation();
                double target = observation.realizedError() ? 1.0 : 0.0;
                double raw = baseline.rawValues().get(observation.dimension());
                double shadow = treatment.observedValues().get(observation.dimension());
                double rawLoss = square(raw - target);
                double shadowLoss = square(shadow - target);
                improvementSum += rawLoss - shadowLoss;
                rawBrier += rawLoss;
                shadowBrier += shadowLoss;
            }
            double count = UncertaintyDimension.values().length;
            rawSum += rawBrier / count;
            shadowSum += shadowBrier / count;
            String cluster = split == ExperimentSplit.TRANSFER ? unit.scenarioFamily() : "";
            samples.add(new M8PairedStatisticalEngine.PairedValue(unit.experimentUnitId(), cluster,
                    unit.fixtureProfile(), improvementSum / count));
        }
        M8PairedStatisticalEngine.Result stats = statistics.analyze(samples,
                M8ExperimentScenarioFactory.PROTOCOL_ID + "|" + split + "|H5|" + ANALYSIS_VERSION, false);
        double rawMean = rawSum / expectedUnits.size();
        double shadowMean = shadowSum / expectedUnits.size();
        Map<UncertaintyDimension, CalibrationDimension> metrics = split == ExperimentSplit.HOLDOUT
                ? fromHoldout(holdoutReport) : fromTransfer(transferReport);
        double rawEce = metrics.values().stream().mapToDouble(CalibrationDimension::rawEce).average().orElseThrow();
        double shadowEce = metrics.values().stream().mapToDouble(CalibrationDimension::shadowEce).average().orElseThrow();
        double rawBrier = metrics.values().stream().mapToDouble(CalibrationDimension::rawBrier).average().orElseThrow();
        double shadowBrier = metrics.values().stream().mapToDouble(CalibrationDimension::shadowBrier).average().orElseThrow();
        boolean noDimensionRegression = metrics.values().stream()
                .allMatch(value -> value.shadowEce() - value.rawEce() <= 0.02 + 1e-12);
        double relativeBrierImprovement = rawBrier > 0.0 ? (rawBrier - shadowBrier) / rawBrier : Double.NEGATIVE_INFINITY;
        boolean mpe = stats.confidenceIntervalLower() > 0.0 && shadowEce <= 0.15 + 1e-12
                && (rawEce - shadowEce) >= 0.02 - 1e-12 && relativeBrierImprovement >= 0.05 - 1e-12
                && noDimensionRegression;
        String explanation = "mean Brier reduction=" + relativeBrierImprovement
                + "; mean ECE reduction=" + (rawEce - shadowEce)
                + "; shadow ECE=" + shadowEce + "; no-dimension-regression=" + noDimensionRegression;
        CalibrationMetrics serializable = new CalibrationMetrics(metrics, rawEce, shadowEce,
                rawBrier, shadowBrier, relativeBrierImprovement,
                holdoutReport == null ? transferReport.reportFingerprint() : holdoutReport.reportFingerprint());
        return new B6Summary(rawMean, shadowMean, stats, serializable,
                mpe ? MpeStatus.SATISFIED : MpeStatus.NOT_SATISFIED, explanation);
    }

    private static Map<UncertaintyDimension, CalibrationDimension> fromHoldout(B6HoldoutEvaluationReport report) {
        var result = new EnumMap<UncertaintyDimension, CalibrationDimension>(UncertaintyDimension.class);
        report.byDimension().forEach((dimension, value) -> result.put(dimension,
                new CalibrationDimension(value.sampleCount(), value.rawExpectedCalibrationError(),
                        value.shadowExpectedCalibrationError(), value.rawBrierScore(), value.shadowBrierScore())));
        return Map.copyOf(result);
    }

    private static Map<UncertaintyDimension, CalibrationDimension> fromTransfer(B6TransferEvaluationReport report) {
        var result = new EnumMap<UncertaintyDimension, CalibrationDimension>(UncertaintyDimension.class);
        report.byDimension().forEach((dimension, value) -> result.put(dimension,
                new CalibrationDimension(value.sampleCount(), value.rawExpectedCalibrationError(),
                        value.shadowExpectedCalibrationError(), value.rawBrierScore(), value.shadowBrierScore())));
        return Map.copyOf(result);
    }

    private MpeAssessment assessMpe(String hypothesis, double baselineMean, double treatmentMean,
            M8PairedStatisticalEngine.Result stats,
            Map<M8VerifiedEndpointEvidence.EndpointId, M8VerifiedEndpointEvidence[]> endpoints) {
        double improvement = stats.meanImprovement();
        boolean ciPositive = stats.confidenceIntervalLower() > 0.0;
        switch (hypothesis) {
            case "H1" -> {
                double relative = baselineMean > 0.0 ? improvement / baselineMean : Double.NEGATIVE_INFINITY;
                double costIncrease = relativeChange(endpoints.get(M8VerifiedEndpointEvidence.EndpointId.H1_RETRIEVAL_MAINTENANCE_COST));
                boolean pass = ciPositive && relative >= 0.05 && costIncrease <= 0.10;
                return new MpeAssessment(pass ? MpeStatus.SATISFIED : MpeStatus.NOT_SATISFIED,
                        "relative usefulness improvement=" + relative + "; retrieval/maintenance cost change=" + costIncrease);
            }
            case "H2" -> {
                // The preregistered H2 secondary endpoint is median search cost, not the mean.
                double searchCostReduction = medianRelativeReduction(
                        endpoints.get(M8VerifiedEndpointEvidence.EndpointId.H2_SEARCH_COST_TO_FIRST_FEASIBLE));
                boolean successPath = ciPositive && improvement >= 0.05;
                boolean costPath = searchCostReduction >= 0.10 && stats.confidenceIntervalLower() >= -0.05;
                return new MpeAssessment(successPath || costPath ? MpeStatus.SATISFIED : MpeStatus.NOT_SATISFIED,
                        "success-rate improvement=" + improvement + "; search-cost reduction=" + searchCostReduction
                                + "; success non-inferiority lower CI=" + stats.confidenceIntervalLower());
            }
            case "H3" -> {
                double resourceIncrease = relativeChange(endpoints.get(M8VerifiedEndpointEvidence.EndpointId.H3_WORKSPACE_RESOURCE_UNITS));
                boolean pass = ciPositive && improvement >= 0.05 && resourceIncrease <= 0.10;
                return new MpeAssessment(pass ? MpeStatus.SATISFIED : MpeStatus.NOT_SATISFIED,
                        "useful-consumption-per-resource improvement=" + improvement + "; workspace resource change=" + resourceIncrease);
            }
            case "H4" -> {
                double objectiveScoreRelative = relativeChange(endpoints.get(M8VerifiedEndpointEvidence.EndpointId.H4_OBJECTIVE_SCORE));
                double resourceIncrease = relativeChange(endpoints.get(M8VerifiedEndpointEvidence.EndpointId.H4_TOTAL_RESOURCE_UNITS));
                boolean pass = (ciPositive && improvement >= 0.05 || objectiveScoreRelative >= 0.05)
                        && resourceIncrease <= 0.15;
                return new MpeAssessment(pass ? MpeStatus.SATISFIED : MpeStatus.NOT_SATISFIED,
                        "objective-satisfaction improvement=" + improvement + "; objective-score change="
                                + objectiveScoreRelative + "; total resource change=" + resourceIncrease);
            }
            default -> throw new IllegalArgumentException("unknown primary hypothesis " + hypothesis);
        }
    }

    private static Map<M8VerifiedEndpointEvidence.EndpointId, M8VerifiedEndpointEvidence[]> pairEndpointMeans(
            String hypothesis, Map<M8VerifiedEndpointEvidence.EndpointId, Map<String, M8VerifiedEndpointEvidence>> all) {
        var result = new EnumMap<M8VerifiedEndpointEvidence.EndpointId, M8VerifiedEndpointEvidence[]>(
                M8VerifiedEndpointEvidence.EndpointId.class);
        Set<M8VerifiedEndpointEvidence.EndpointId> required = REQUIRED_ENDPOINTS.get(hypothesis);
        if (required == null) throw new IllegalArgumentException("unknown hypothesis " + hypothesis);
        for (M8VerifiedEndpointEvidence.EndpointId endpoint : required) {
            Map<String, M8VerifiedEndpointEvidence> records = all.get(endpoint);
            if (records == null || records.isEmpty()) throw new SecurityException("missing registered secondary endpoint " + endpoint);
            result.put(endpoint, records.values().toArray(M8VerifiedEndpointEvidence[]::new));
        }
        return Map.copyOf(result);
    }


    private static double medianRelativeReduction(M8VerifiedEndpointEvidence[] records) {
        if (records == null || records.length == 0) return Double.NEGATIVE_INFINITY;
        double[] baseline = java.util.Arrays.stream(records).mapToDouble(M8VerifiedEndpointEvidence::baselineValue).toArray();
        double[] treatment = java.util.Arrays.stream(records).mapToDouble(M8VerifiedEndpointEvidence::treatmentValue).toArray();
        java.util.Arrays.sort(baseline);
        java.util.Arrays.sort(treatment);
        double baseMedian = medianSorted(baseline);
        double treatmentMedian = medianSorted(treatment);
        if (baseMedian <= 0.0) return treatmentMedian == 0.0 ? 0.0 : Double.NEGATIVE_INFINITY;
        return (baseMedian - treatmentMedian) / baseMedian;
    }

    private static double medianSorted(double[] sorted) {
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static double relativeChange(M8VerifiedEndpointEvidence[] records) {
        if (records == null || records.length == 0) return Double.POSITIVE_INFINITY;
        double baseline = 0.0;
        double treatment = 0.0;
        for (M8VerifiedEndpointEvidence record : records) {
            baseline += record.baselineValue();
            treatment += record.treatmentValue();
        }
        baseline /= records.length;
        treatment /= records.length;
        return baseline > 0.0 ? (treatment - baseline) / baseline : (treatment == 0.0 ? 0.0 : Double.POSITIVE_INFINITY);
    }

    private static M8VerifiedEndpointEvidence.EndpointId primaryEndpoint(String hypothesis) {
        return switch (hypothesis) {
            case "H1" -> M8VerifiedEndpointEvidence.EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET;
            case "H2" -> M8VerifiedEndpointEvidence.EndpointId.H2_FEASIBLE_RECOVERY_SUCCESS;
            case "H3" -> M8VerifiedEndpointEvidence.EndpointId.H3_VERIFIED_USEFUL_CONSUMPTION_PER_RESOURCE;
            case "H4" -> M8VerifiedEndpointEvidence.EndpointId.H4_OBJECTIVE_SATISFACTION;
            default -> throw new IllegalArgumentException("unknown hypothesis " + hypothesis);
        };
    }

    private static double meanEndpoint(Iterable<M8VerifiedEndpointEvidence> records, boolean baseline) {
        double sum = 0.0;
        int count = 0;
        for (M8VerifiedEndpointEvidence record : records) {
            sum += baseline ? record.baselineValue() : record.treatmentValue();
            count++;
        }
        if (count == 0) throw new IllegalArgumentException("endpoint set is empty");
        return sum / count;
    }

    private static double square(double value) { return value * value; }

    private static Map<String, Double> holm(Map<String, Double> pValues) {
        if (!pValues.keySet().equals(Set.of("H1", "H2", "H3", "H4", "H5")))
            throw new SecurityException("Holm correction requires all five preregistered primary hypotheses");
        List<Map.Entry<String, Double>> sorted = pValues.entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList();
        var adjusted = new HashMap<String, Double>();
        double monotone = 0.0;
        for (int index = 0; index < sorted.size(); index++) {
            double candidate = Math.min(1.0, (sorted.size() - index) * sorted.get(index).getValue());
            monotone = Math.max(monotone, candidate);
            adjusted.put(sorted.get(index).getKey(), monotone);
        }
        return Map.copyOf(adjusted);
    }

    private static Map<String, Set<M8VerifiedEndpointEvidence.EndpointId>> requiredEndpoints() {
        var map = new LinkedHashMap<String, Set<M8VerifiedEndpointEvidence.EndpointId>>();
        map.put("H1", Set.of(M8VerifiedEndpointEvidence.EndpointId.H1_VERIFIED_USEFULNESS_PER_RETRIEVAL_BUDGET,
                M8VerifiedEndpointEvidence.EndpointId.H1_RETRIEVAL_MAINTENANCE_COST));
        map.put("H2", Set.of(M8VerifiedEndpointEvidence.EndpointId.H2_FEASIBLE_RECOVERY_SUCCESS,
                M8VerifiedEndpointEvidence.EndpointId.H2_SEARCH_COST_TO_FIRST_FEASIBLE));
        map.put("H3", Set.of(M8VerifiedEndpointEvidence.EndpointId.H3_VERIFIED_USEFUL_CONSUMPTION_PER_RESOURCE,
                M8VerifiedEndpointEvidence.EndpointId.H3_WORKSPACE_RESOURCE_UNITS));
        map.put("H4", Set.of(M8VerifiedEndpointEvidence.EndpointId.H4_OBJECTIVE_SATISFACTION,
                M8VerifiedEndpointEvidence.EndpointId.H4_OBJECTIVE_SCORE,
                M8VerifiedEndpointEvidence.EndpointId.H4_TOTAL_RESOURCE_UNITS));
        return Map.copyOf(map);
    }

    public enum MpeStatus { SATISFIED, NOT_SATISFIED }

    private record MpeAssessment(MpeStatus status, String explanation) { }
    private record B6Summary(double rawMeanBrier, double shadowMeanBrier,
            M8PairedStatisticalEngine.Result stats, CalibrationMetrics calibrationMetrics,
            MpeStatus mpeStatus, String mpeExplanation) { }

    public record CalibrationDimension(int sampleCount, double rawEce, double shadowEce,
            double rawBrier, double shadowBrier) { }
    public record CalibrationMetrics(Map<UncertaintyDimension, CalibrationDimension> byDimension,
            double meanRawEce, double meanShadowEce, double meanRawBrier, double meanShadowBrier,
            double relativeBrierImprovement, String underlyingReportFingerprint) {
        public CalibrationMetrics {
            byDimension = Map.copyOf(new EnumMap<>(byDimension));
        }
    }

    public record HypothesisResult(String hypothesisId, String primaryEndpoint, double baselineMean,
            double treatmentMean, M8PairedStatisticalEngine.Result statistics,
            CalibrationMetrics b6CalibrationMetrics, MpeStatus mpeStatus, String mpeExplanation,
            Double unadjustedPValue, Double holmAdjustedPValue) {
        public HypothesisResult(String hypothesisId, String primaryEndpoint, double baselineMean,
                double treatmentMean, M8PairedStatisticalEngine.Result statistics,
                CalibrationMetrics b6CalibrationMetrics, MpeStatus mpeStatus, String mpeExplanation) {
            this(hypothesisId, primaryEndpoint, baselineMean, treatmentMean, statistics,
                    b6CalibrationMetrics, mpeStatus, mpeExplanation, null, null);
        }
    }

    public record AnalysisReport(String analysisVersion, String protocolId, String generatorId,
            ExperimentSplit split, int episodeCount, Map<String, HypothesisResult> hypotheses,
            boolean confirmatoryInferenceAllowed, boolean holmCorrectionApplied,
            String multiplicityPolicy, boolean allMinimumEffectThresholdsSatisfied,
            String evidenceClass, String safetyEvidenceFingerprint, String safetyVerifierId, Instant generatedAt) {
        public AnalysisReport {
            hypotheses = Map.copyOf(new LinkedHashMap<>(hypotheses));
            if (hypotheses.size() != 5) throw new IllegalArgumentException("analysis report must contain H1-H5");
            if (confirmatoryInferenceAllowed != (split == ExperimentSplit.HOLDOUT))
                throw new SecurityException("only exact HOLDOUT analysis may be marked confirmatory");
            if (holmCorrectionApplied != (split == ExperimentSplit.HOLDOUT))
                throw new SecurityException("Holm correction is required only for confirmatory HOLDOUT");
            if (!"SIMULATOR_ONLY_NOT_REAL_WORLD_OUTCOME_EVIDENCE".equals(evidenceClass))
                throw new SecurityException("analysis cannot be represented as real-world operational evidence");
            safetyEvidenceFingerprint = M8TrainingFreezeManifest.sha(safetyEvidenceFingerprint, "safetyEvidenceFingerprint");
            safetyVerifierId = Objects.requireNonNull(safetyVerifierId, "safetyVerifierId").trim();
            if (safetyVerifierId.isEmpty()) throw new IllegalArgumentException("safetyVerifierId cannot be blank");
        }

        public String toJson() {
            StringBuilder json = new StringBuilder(4096);
            json.append('{').append("\"recordType\":\"m8ConfirmatoryAnalysis\"")
                    .append(",\"analysisVersion\":").append(q(analysisVersion))
                    .append(",\"protocolId\":").append(q(protocolId))
                    .append(",\"generatorId\":").append(q(generatorId))
                    .append(",\"split\":").append(q(split.name()))
                    .append(",\"episodeCount\":").append(episodeCount)
                    .append(",\"confirmatoryInferenceAllowed\":").append(confirmatoryInferenceAllowed)
                    .append(",\"holmCorrectionApplied\":").append(holmCorrectionApplied)
                    .append(",\"multiplicityPolicy\":").append(q(multiplicityPolicy))
                    .append(",\"allMinimumEffectThresholdsSatisfied\":").append(allMinimumEffectThresholdsSatisfied)
                    .append(",\"evidenceClass\":").append(q(evidenceClass))
                    .append(",\"safetyEvidenceFingerprint\":").append(q(safetyEvidenceFingerprint))
                    .append(",\"safetyVerifierId\":").append(q(safetyVerifierId))
                    .append(",\"generatedAt\":").append(q(generatedAt.toString()))
                    .append(",\"hypotheses\":{");
            boolean first = true;
            for (String key : List.of("H1", "H2", "H3", "H4", "H5")) {
                HypothesisResult value = hypotheses.get(key);
                if (!first) json.append(',');
                first = false;
                json.append(q(key)).append(":{")
                        .append("\"primaryEndpoint\":").append(q(value.primaryEndpoint()))
                        .append(",\"baselineMean\":").append(value.baselineMean())
                        .append(",\"treatmentMean\":").append(value.treatmentMean())
                        .append(",\"meanImprovement\":").append(value.statistics().meanImprovement())
                        .append(",\"medianImprovement\":").append(value.statistics().medianImprovement())
                        .append(",\"ci95Lower\":").append(value.statistics().confidenceIntervalLower())
                        .append(",\"ci95Upper\":").append(value.statistics().confidenceIntervalUpper())
                        .append(",\"pairedPValue\":").append(value.statistics().twoSidedPValue())
                        .append(",\"inferenceMethod\":").append(q(value.statistics().inferenceMethod()))
                        .append(",\"holmAdjustedP\":").append(nullable(value.holmAdjustedPValue()))
                        .append(",\"pairedCohensDz\":").append(nullable(value.statistics().pairedCohensDz()))
                        .append(",\"sampleCount\":").append(value.statistics().sampleCount())
                        .append(",\"stratumCount\":").append(value.statistics().stratumCount())
                        .append(",\"bootstrapReplicates\":").append(value.statistics().bootstrapReplicates())
                        .append(",\"deterministicSeed\":").append(value.statistics().deterministicSeed())
                        .append(",\"mpeStatus\":").append(q(value.mpeStatus().name()))
                        .append(",\"mpeExplanation\":").append(q(value.mpeExplanation()));
                if (value.b6CalibrationMetrics() != null) {
                    var cal = value.b6CalibrationMetrics();
                    json.append(",\"b6MeanRawEce\":").append(cal.meanRawEce())
                            .append(",\"b6MeanShadowEce\":").append(cal.meanShadowEce())
                            .append(",\"b6MeanRawBrier\":").append(cal.meanRawBrier())
                            .append(",\"b6MeanShadowBrier\":").append(cal.meanShadowBrier())
                            .append(",\"b6RelativeBrierImprovement\":").append(cal.relativeBrierImprovement())
                            .append(",\"b6UnderlyingReportFingerprint\":").append(q(cal.underlyingReportFingerprint()));
                }
                json.append('}');
            }
            return json.append("}}\n").toString();
        }

        public void writeNew(Path output) throws IOException {
            Path target = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
            Path parent = target.getParent();
            if (parent == null) throw new IOException("analysis output must have a parent directory");
            Files.createDirectories(parent);
            Files.writeString(target, toJson(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    private static String nullable(Double value) { return value == null || !Double.isFinite(value) ? "null" : Double.toString(value); }
    private static String q(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        return out.append('"').toString();
    }
}
