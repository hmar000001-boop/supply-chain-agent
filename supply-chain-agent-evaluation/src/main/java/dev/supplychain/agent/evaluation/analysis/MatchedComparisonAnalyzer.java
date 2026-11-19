package dev.supplychain.agent.evaluation.analysis;

import dev.supplychain.agent.evaluation.runner.EvaluationBatch;
import dev.supplychain.agent.evaluation.runner.EvaluationRunRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/** Validates the matched experiment matrix and produces neutral aggregate and paired comparisons. */
public final class MatchedComparisonAnalyzer {
    public static final int BOOTSTRAP_REPLICATES = 2_000;
    public static final double SCORE_TIE_TOLERANCE = 1.0e-9;

    public Analysis analyze(EvaluationBatch batch) {
        Objects.requireNonNull(batch, "batch");
        validateMatchedMatrix(batch);
        List<AggregateRecord> aggregates = aggregates(batch);
        List<PairedComparisonRecord> pairs = pairwise(batch);
        return new Analysis(batch.manifest().baselineId(), batch.manifest().corpusVersion(),
                batch.manifest().corpusFingerprint(), batch.manifest().plannerConfigurationFingerprint(),
                batch.manifest().scenarioCount(), batch.manifest().runCount(), batch.manifest().plannerIds(),
                aggregates, pairs, BOOTSTRAP_REPLICATES, SCORE_TIE_TOLERANCE,
                "PLANNER_ONLY;FEASIBILITY_FIRST;RUNTIME_DESCRIPTIVE_NOT_RANKED");
    }

    public void validateMatchedMatrix(EvaluationBatch batch) {
        List<String> planners = batch.manifest().plannerIds();
        if (batch.manifest().scenarioCount() < 1) throw new IllegalArgumentException("comparison requires at least one scenario");
        if (planners.size() < 2 || new TreeSet<>(planners).size() != planners.size()) {
            throw new IllegalArgumentException("comparison requires at least two unique planner IDs");
        }
        if (batch.manifest().runCount() != batch.manifest().scenarioCount() * planners.size()) {
            throw new IllegalArgumentException("manifest does not describe a complete scenario × planner matrix");
        }
        Map<String, Map<String, EvaluationRunRecord>> matrix = new TreeMap<>();
        for (EvaluationRunRecord row : batch.runs()) {
            if (!row.plannerOnly() || row.externalDispatchAttempted()) {
                throw new IllegalArgumentException("comparison rows must remain planner-only with no external dispatch");
            }
            if (!planners.contains(row.plannerId())) throw new IllegalArgumentException("unregistered planner in result: " + row.plannerId());
            EvaluationRunRecord previous = matrix.computeIfAbsent(row.scenarioId(), ignored -> new TreeMap<>())
                    .putIfAbsent(row.plannerId(), row);
            if (previous != null) throw new IllegalArgumentException("duplicate scenario/planner row: " + row.scenarioId() + "/" + row.plannerId());
        }
        if (matrix.size() != batch.manifest().scenarioCount()) throw new IllegalArgumentException("scenario count differs from manifest");
        var scenarioOrderIndexes = new TreeSet<Integer>();
        for (var scenarioEntry : matrix.entrySet()) {
            Map<String, EvaluationRunRecord> rows = scenarioEntry.getValue();
            if (!rows.keySet().equals(new TreeSet<>(planners))) {
                throw new IllegalArgumentException("missing comparator result for scenario " + scenarioEntry.getKey());
            }
            List<EvaluationRunRecord> scenarioRows = new ArrayList<>(rows.values());
            EvaluationRunRecord reference = scenarioRows.getFirst();
            if (!scenarioOrderIndexes.add(reference.scenarioOrderIndex())) {
                throw new IllegalArgumentException("duplicate scenario-order index " + reference.scenarioOrderIndex());
            }
            var orderIndices = new TreeSet<Integer>();
            for (EvaluationRunRecord row : scenarioRows) {
                if (!row.fixtureVersion().equals(reference.fixtureVersion())
                        || row.seed() != reference.seed()
                        || !row.budgetProfile().equals(reference.budgetProfile())
                        || !row.fixtureFingerprint().equals(reference.fixtureFingerprint())
                        || !row.requestFingerprint().equals(reference.requestFingerprint())
                        || !row.scenarioKind().equals(reference.scenarioKind())
                        || !row.fixtureProfile().equals(reference.fixtureProfile())
                        || row.scenarioOrderIndex() != reference.scenarioOrderIndex()
                        || !row.expectedEffectBoundary().equals(reference.expectedEffectBoundary())) {
                    throw new IllegalArgumentException("comparators did not receive a matched fixture for " + scenarioEntry.getKey());
                }
                if (!orderIndices.add(row.executionOrderIndex())) {
                    throw new IllegalArgumentException("duplicate execution-order index in scenario " + scenarioEntry.getKey());
                }
                int expectedOrderIndex = Math.floorMod(planners.indexOf(row.plannerId()) - row.scenarioOrderIndex(), planners.size());
                if (row.executionOrderIndex() != expectedOrderIndex) {
                    throw new IllegalArgumentException("comparator order rotation mismatch in scenario " + scenarioEntry.getKey()
                            + " for planner " + row.plannerId());
                }
            }
            if (!orderIndices.equals(new TreeSet<>(java.util.stream.IntStream.range(0, planners.size()).boxed().toList()))) {
                throw new IllegalArgumentException("execution-order indices must form a complete rotation for " + scenarioEntry.getKey());
            }
        }
        if (!scenarioOrderIndexes.equals(new TreeSet<>(java.util.stream.IntStream.range(0, batch.manifest().scenarioCount()).boxed().toList()))) {
            throw new IllegalArgumentException("scenario-order indices must form a complete corpus-order sequence");
        }
    }

    private List<AggregateRecord> aggregates(EvaluationBatch batch) {
        var strata = new LinkedHashMap<String, Map<String, List<EvaluationRunRecord>>>();
        strata.put("OVERALL", Map.of("ALL", batch.runs()));
        strata.put("BUDGET_PROFILE", group(batch.runs(), EvaluationRunRecord::budgetProfile));
        strata.put("SCENARIO_KIND", group(batch.runs(), EvaluationRunRecord::scenarioKind));
        strata.put("FIXTURE_PROFILE", group(batch.runs(), EvaluationRunRecord::fixtureProfile));
        var output = new ArrayList<AggregateRecord>();
        for (var dimension : strata.entrySet()) {
            for (var stratum : dimension.getValue().entrySet()) {
                Map<String, List<EvaluationRunRecord>> byPlanner = stratum.getValue().stream()
                        .collect(Collectors.groupingBy(EvaluationRunRecord::plannerId, TreeMap::new, Collectors.toList()));
                byPlanner.forEach((plannerId, rows) -> output.add(aggregate(dimension.getKey(), stratum.getKey(), plannerId, rows)));
            }
        }
        return List.copyOf(output);
    }

    private static Map<String, List<EvaluationRunRecord>> group(List<EvaluationRunRecord> rows,
                                                                 java.util.function.Function<EvaluationRunRecord, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }

    private AggregateRecord aggregate(String dimension, String value, String plannerId, List<EvaluationRunRecord> rows) {
        int feasibleScenarios = (int) rows.stream().filter(row -> row.bestFeasibleOutcome() != null).count();
        List<EvaluationRunRecord.CandidateOutcome> selected = rows.stream().map(EvaluationRunRecord::bestFeasibleOutcome)
                .filter(Objects::nonNull).toList();
        Double meanScore = meanOrNull(selected.stream().map(EvaluationRunRecord.CandidateOutcome::score).toList());
        Double meanProgress = meanOrNull(selected.stream().map(EvaluationRunRecord.CandidateOutcome::expectedFulfillmentProgress).toList());
        Double meanCost = meanOrNull(selected.stream().map(candidate -> new java.math.BigDecimal(candidate.estimatedCost()).doubleValue()).toList());
        String currency = selected.stream().map(EvaluationRunRecord.CandidateOutcome::currency).distinct().sorted()
                .collect(Collectors.joining(","));
        if (currency.contains(",")) throw new IllegalArgumentException("mixed currencies cannot be averaged in one aggregate stratum");
        Double meanRisk = meanOrNull(selected.stream().map(EvaluationRunRecord.CandidateOutcome::aggregateRisk).toList());
        Double meanRobustness = meanOrNull(selected.stream().map(EvaluationRunRecord.CandidateOutcome::robustness).toList());
        long violations = rows.stream().mapToLong(EvaluationRunRecord::hardViolationCount).sum();
        long approvals = rows.stream().flatMap(row -> row.candidateOutcomes().stream())
                .filter(EvaluationRunRecord.CandidateOutcome::approvalRequired).count();
        long deadlineBreaches = rows.stream().flatMap(row -> row.candidateOutcomes().stream())
                .filter(EvaluationRunRecord.CandidateOutcome::targetDeadlineBreach).count();
        Map<String, Long> terminations = rows.stream().collect(Collectors.groupingBy(EvaluationRunRecord::terminationReason,
                TreeMap::new, Collectors.counting()));
        var resources = new TreeMap<String, Long>();
        rows.forEach(row -> row.resourceUsage().forEach((unit, amount) -> resources.merge(unit, amount, Long::sum)));
        return new AggregateRecord(dimension, value, plannerId, rows.size(), feasibleScenarios,
                (double) feasibleScenarios / rows.size(), meanScore, meanProgress, meanCost,
                currency.isEmpty() ? null : currency, meanRisk, meanRobustness, violations, approvals,
                deadlineBreaches, terminations, resources,
                rows.stream().mapToLong(EvaluationRunRecord::expansions).average().orElse(0.0),
                rows.stream().mapToLong(EvaluationRunRecord::evaluations).average().orElse(0.0),
                rows.stream().mapToLong(EvaluationRunRecord::runtimeNanos).average().orElse(0.0));
    }

    private List<PairedComparisonRecord> pairwise(EvaluationBatch batch) {
        var byScenario = batch.runs().stream().collect(Collectors.groupingBy(EvaluationRunRecord::scenarioId));
        List<String> plannerIds = batch.manifest().plannerIds();
        var output = new ArrayList<PairedComparisonRecord>();
        for (int leftIndex = 0; leftIndex < plannerIds.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < plannerIds.size(); rightIndex++) {
                String left = plannerIds.get(leftIndex);
                String right = plannerIds.get(rightIndex);
                var scoreDeltas = new ArrayList<Double>();
                var feasibilityDeltas = new ArrayList<Double>();
                int wins = 0, ties = 0, losses = 0;
                for (String scenario : byScenario.keySet().stream().sorted().toList()) {
                    Map<String, EvaluationRunRecord> rows = byScenario.get(scenario).stream()
                            .collect(Collectors.toMap(EvaluationRunRecord::plannerId, row -> row));
                    EvaluationRunRecord a = rows.get(left), b = rows.get(right);
                    boolean aFeasible = a.bestFeasibleOutcome() != null;
                    boolean bFeasible = b.bestFeasibleOutcome() != null;
                    feasibilityDeltas.add((aFeasible ? 1.0 : 0.0) - (bFeasible ? 1.0 : 0.0));
                    double aScore = aFeasible ? a.bestFeasibleOutcome().score() : 0.0;
                    double bScore = bFeasible ? b.bestFeasibleOutcome().score() : 0.0;
                    if (aFeasible && bFeasible) scoreDeltas.add(aScore - bScore);
                    int comparison;
                    if (aFeasible != bFeasible) comparison = aFeasible ? 1 : -1;
                    else if (!aFeasible) comparison = 0;
                    else comparison = Math.abs(aScore - bScore) <= SCORE_TIE_TOLERANCE ? 0 : Double.compare(aScore, bScore);
                    if (comparison > 0) wins++; else if (comparison < 0) losses++; else ties++;
                }
                String pairSeed = batch.manifest().corpusFingerprint() + "|" + left + "|" + right;
                Interval scoreInterval = bootstrapMeanInterval(scoreDeltas, pairSeed + "|score");
                Interval feasibleInterval = bootstrapMeanInterval(feasibilityDeltas, pairSeed + "|feasible");
                output.add(new PairedComparisonRecord(left, right, feasibilityDeltas.size(), scoreDeltas.size(),
                        wins, ties, losses, meanOrNull(scoreDeltas), scoreInterval.lower(), scoreInterval.upper(),
                        mean(feasibilityDeltas), feasibleInterval.lower(), feasibleInterval.upper(), BOOTSTRAP_REPLICATES,
                        "FEASIBILITY_FIRST_THEN_BEST_FEASIBLE_SCORE;SCORE_DELTA_ONLY_WHEN_BOTH_FEASIBLE"));
            }
        }
        return List.copyOf(output);
    }

    private static Interval bootstrapMeanInterval(List<Double> values, String seedMaterial) {
        if (values.isEmpty()) return new Interval(null, null);
        long seed = digestLong(seedMaterial);
        SplittableRandom random = new SplittableRandom(seed);
        double[] samples = new double[BOOTSTRAP_REPLICATES];
        for (int replicate = 0; replicate < BOOTSTRAP_REPLICATES; replicate++) {
            double sum = 0.0;
            for (int index = 0; index < values.size(); index++) sum += values.get(random.nextInt(values.size()));
            samples[replicate] = sum / values.size();
        }
        java.util.Arrays.sort(samples);
        return new Interval(percentile(samples, 0.025), percentile(samples, 0.975));
    }

    private static long digestLong(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.nio.ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 1) return sorted[0];
        double position = p * (sorted.length - 1);
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        double fraction = position - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    private static Double meanOrNull(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
    private static String nullableNumber(Double value) {
        return value == null ? "null" : Double.toString(value);
    }

    private record Interval(Double lower, Double upper) { }

    public record Analysis(String baselineId, String corpusVersion, String corpusFingerprint,
                           String plannerConfigurationFingerprint, int scenarioCount, int runCount,
                           List<String> plannerIds, List<AggregateRecord> aggregates,
                           List<PairedComparisonRecord> pairwiseComparisons, int bootstrapReplicates,
                           double scoreTieTolerance, String interpretationPolicy) {
        public Analysis {
            plannerIds = List.copyOf(plannerIds);
            aggregates = List.copyOf(aggregates);
            pairwiseComparisons = List.copyOf(pairwiseComparisons);
        }
        public String toJsonLines() {
            StringBuilder out = new StringBuilder();
            out.append("{\"recordType\":\"comparisonManifest\",\"baselineId\":").append(q(baselineId))
                    .append(",\"corpusVersion\":").append(q(corpusVersion))
                    .append(",\"corpusFingerprint\":").append(q(corpusFingerprint))
                    .append(",\"plannerConfigurationFingerprint\":").append(q(plannerConfigurationFingerprint))
                    .append(",\"scenarioCount\":").append(scenarioCount).append(",\"runCount\":").append(runCount)
                    .append(",\"plannerIds\":[").append(plannerIds.stream().map(MatchedComparisonAnalyzer::q).collect(Collectors.joining(",")))
                    .append("],\"bootstrapReplicates\":").append(bootstrapReplicates)
                    .append(",\"scoreTieTolerance\":").append(scoreTieTolerance)
                    .append(",\"interpretationPolicy\":").append(q(interpretationPolicy)).append("}\n");
            aggregates.forEach(row -> out.append(row.toJson()).append('\n'));
            pairwiseComparisons.forEach(row -> out.append(row.toJson()).append('\n'));
            return out.toString();
        }
    }

    public record AggregateRecord(String dimension, String stratum, String plannerId, int scenarioCount,
                                  int feasibleScenarioCount, double feasibilityRate, Double meanBestFeasibleScore,
                                  Double meanFulfillmentProgress, Double meanEstimatedCost, String currency,
                                  Double meanAggregateRisk, Double meanRobustness, long candidateConstraintViolationCount,
                                  long approvalRequiredCandidateCount, long deadlineBreachCandidateCount,
                                  Map<String, Long> terminationCounts, Map<String, Long> resourceUsageTotals,
                                  double meanExpansions, double meanEvaluations, double meanRuntimeNanos) {
        public AggregateRecord {
            dimension = Objects.requireNonNull(dimension, "dimension");
            stratum = Objects.requireNonNull(stratum, "stratum");
            plannerId = Objects.requireNonNull(plannerId, "plannerId");
            if (scenarioCount < 1 || feasibleScenarioCount < 0 || feasibleScenarioCount > scenarioCount
                    || !Double.isFinite(feasibilityRate) || feasibilityRate < 0 || feasibilityRate > 1) {
                throw new IllegalArgumentException("invalid aggregate sample counts or feasibility rate");
            }
            terminationCounts = Map.copyOf(new TreeMap<>(terminationCounts));
            resourceUsageTotals = Map.copyOf(new TreeMap<>(resourceUsageTotals));
        }
        String toJson() {
            return "{\"recordType\":\"strategyAggregate\",\"dimension\":" + q(dimension)
                    + ",\"stratum\":" + q(stratum) + ",\"plannerId\":" + q(plannerId)
                    + ",\"scenarioCount\":" + scenarioCount + ",\"feasibleScenarioCount\":" + feasibleScenarioCount
                    + ",\"feasibilityRate\":" + feasibilityRate + ",\"meanBestFeasibleScore\":" + nullableNumber(meanBestFeasibleScore)
                    + ",\"meanFulfillmentProgress\":" + nullableNumber(meanFulfillmentProgress) + ",\"meanEstimatedCost\":" + nullableNumber(meanEstimatedCost)
                    + ",\"currency\":" + nullableQ(currency) + ",\"meanAggregateRisk\":" + nullableNumber(meanAggregateRisk)
                    + ",\"meanRobustness\":" + nullableNumber(meanRobustness) + ",\"candidateConstraintViolationCount\":" + candidateConstraintViolationCount
                    + ",\"approvalRequiredCandidateCount\":" + approvalRequiredCandidateCount
                    + ",\"deadlineBreachCandidateCount\":" + deadlineBreachCandidateCount
                    + ",\"terminationCounts\":" + longMap(terminationCounts) + ",\"resourceUsageTotals\":" + longMap(resourceUsageTotals)
                    + ",\"meanExpansions\":" + meanExpansions + ",\"meanEvaluations\":" + meanEvaluations
                    + ",\"meanRuntimeNanos\":" + meanRuntimeNanos + "}";
        }
    }

    public record PairedComparisonRecord(String leftPlannerId, String rightPlannerId, int pairedScenarioCount,
                                         int scoreComparedScenarioCount, int leftWins, int ties, int rightWins,
                                         Double meanBestFeasibleScoreDelta, Double scoreDeltaCi95Lower,
                                         Double scoreDeltaCi95Upper, double meanFeasibilityDelta,
                                         Double feasibilityDeltaCi95Lower, Double feasibilityDeltaCi95Upper,
                                         int bootstrapReplicates, String winPolicy) {
        public PairedComparisonRecord {
            leftPlannerId = Objects.requireNonNull(leftPlannerId, "leftPlannerId");
            rightPlannerId = Objects.requireNonNull(rightPlannerId, "rightPlannerId");
            winPolicy = Objects.requireNonNull(winPolicy, "winPolicy");
            if (pairedScenarioCount < 1 || scoreComparedScenarioCount < 0 || scoreComparedScenarioCount > pairedScenarioCount
                    || leftWins < 0 || ties < 0 || rightWins < 0
                    || leftWins + ties + rightWins != pairedScenarioCount || bootstrapReplicates < 1) {
                throw new IllegalArgumentException("invalid paired-comparison counts");
            }
            if (scoreComparedScenarioCount == 0 && (meanBestFeasibleScoreDelta != null
                    || scoreDeltaCi95Lower != null || scoreDeltaCi95Upper != null)) {
                throw new IllegalArgumentException("score comparison values require jointly feasible scenario samples");
            }
            if (scoreComparedScenarioCount > 0 && (meanBestFeasibleScoreDelta == null
                    || scoreDeltaCi95Lower == null || scoreDeltaCi95Upper == null
                    || scoreDeltaCi95Lower > scoreDeltaCi95Upper)) {
                throw new IllegalArgumentException("jointly feasible score comparisons require a valid interval");
            }
            if (feasibilityDeltaCi95Lower == null || feasibilityDeltaCi95Upper == null
                    || feasibilityDeltaCi95Lower > feasibilityDeltaCi95Upper) {
                throw new IllegalArgumentException("paired feasibility comparison requires a valid interval");
            }
        }
        String toJson() {
            return "{\"recordType\":\"pairedComparison\",\"leftPlannerId\":" + q(leftPlannerId)
                    + ",\"rightPlannerId\":" + q(rightPlannerId) + ",\"pairedScenarioCount\":" + pairedScenarioCount
                    + ",\"scoreComparedScenarioCount\":" + scoreComparedScenarioCount
                    + ",\"leftWins\":" + leftWins + ",\"ties\":" + ties + ",\"rightWins\":" + rightWins
                    + ",\"meanBestFeasibleScoreDelta\":" + nullableNumber(meanBestFeasibleScoreDelta)
                    + ",\"scoreDeltaCi95Lower\":" + nullableNumber(scoreDeltaCi95Lower)
                    + ",\"scoreDeltaCi95Upper\":" + nullableNumber(scoreDeltaCi95Upper)
                    + ",\"meanFeasibilityDelta\":" + meanFeasibilityDelta
                    + ",\"feasibilityDeltaCi95Lower\":" + nullableNumber(feasibilityDeltaCi95Lower)
                    + ",\"feasibilityDeltaCi95Upper\":" + nullableNumber(feasibilityDeltaCi95Upper)
                    + ",\"bootstrapReplicates\":" + bootstrapReplicates + ",\"winPolicy\":" + q(winPolicy) + "}";
        }
    }

    private static String longMap(Map<String, Long> values) {
        return "{" + new TreeMap<>(values).entrySet().stream()
                .map(entry -> q(entry.getKey()) + ":" + entry.getValue()).collect(Collectors.joining(",")) + "}";
    }
    private static String q(String value) { return EvaluationRunRecord.quote(value); }
    private static String nullableQ(String value) { return EvaluationRunRecord.nullableQuote(value); }
}
