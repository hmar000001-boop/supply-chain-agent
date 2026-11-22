package dev.supplychain.agent.evaluation.experiment.research;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** Dependency-free, deterministic paired inference kernel locked to 10,000 resamples. */
public final class M8PairedStatisticalEngine {
    public static final int BOOTSTRAP_REPLICATES = 10_000;
    public static final String IMPLEMENTATION_ID = "M8_PAIRED_HIERARCHICAL_CLUSTER_STRATIFIED_BOOTSTRAP_V2";

    public record PairedValue(String unitId, String cluster, String stratum, double orientedImprovement) {
        public PairedValue(String unitId, String stratum, double orientedImprovement) {
            this(unitId, "", stratum, orientedImprovement);
        }
        public PairedValue {
            unitId = required(unitId, "unitId");
            cluster = cluster == null ? "" : cluster.trim();
            stratum = required(stratum, "stratum");
            if (!Double.isFinite(orientedImprovement)) throw new IllegalArgumentException("paired improvement must be finite");
        }
    }

    public record Result(int sampleCount, int stratumCount, double meanImprovement,
            double medianImprovement, double confidenceIntervalLower, double confidenceIntervalUpper,
            double twoSidedPValue, String inferenceMethod, Double pairedCohensDz, int bootstrapReplicates,
            long deterministicSeed, String implementationId) { }

    public Result analyze(List<PairedValue> supplied, String seedMaterial) {
        return analyze(supplied, seedMaterial, false);
    }

    /** Binary paired endpoints use exact McNemar/sign inference; continuous endpoints use sign-flip randomization. */
    public Result analyze(List<PairedValue> supplied, String seedMaterial, boolean binaryEndpoint) {
        List<PairedValue> values = List.copyOf(Objects.requireNonNull(supplied, "supplied"));
        if (values.size() < 2) throw new IllegalArgumentException("paired inference requires at least two independent episodes");
        if (values.stream().map(PairedValue::unitId).distinct().count() != values.size())
            throw new SecurityException("paired inference contains duplicate episode IDs");
        Map<String, Map<String, List<PairedValue>>> clusters = new TreeMap<>();
        values.stream().sorted(Comparator.comparing(PairedValue::unitId))
                .forEach(value -> clusters.computeIfAbsent(value.cluster().isEmpty() ? "" : value.cluster(),
                        ignored -> new TreeMap<>())
                        .computeIfAbsent(value.stratum(), ignored -> new ArrayList<>()).add(value));
        int stratumCount = clusters.values().stream().mapToInt(Map::size).sum();
        double[] deltas = values.stream().sorted(Comparator.comparing(PairedValue::unitId))
                .mapToDouble(PairedValue::orientedImprovement).toArray();
        double mean = mean(deltas);
        double median = median(deltas);
        long seed = digestLong(required(seedMaterial, "seedMaterial"));
        double[] bootstrap = hierarchicalBootstrap(clusters, seed ^ 0x6a09e667f3bcc909L);
        String method;
        double p;
        if (binaryEndpoint) {
            p = exactMcNemarSignP(deltas);
            method = "EXACT_MCNEMAR_SIGN_TEST";
        } else {
            p = signFlipPermutationP(deltas, seed ^ 0xbb67ae8584caa73bL, mean);
            method = "PAIRED_SIGN_FLIP_MONTE_CARLO_10000";
        }
        return new Result(values.size(), stratumCount, mean, median,
                percentile(bootstrap, 0.025), percentile(bootstrap, 0.975), p, method,
                pairedCohensDz(deltas, mean), BOOTSTRAP_REPLICATES, seed, IMPLEMENTATION_ID);
    }

    private static double[] hierarchicalBootstrap(
            Map<String, Map<String, List<PairedValue>>> clusters, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<String> clusterIds = List.copyOf(clusters.keySet());
        double[] means = new double[BOOTSTRAP_REPLICATES];
        boolean oneControlledCluster = clusterIds.size() == 1 && clusterIds.get(0).equals("");
        for (int replicate = 0; replicate < BOOTSTRAP_REPLICATES; replicate++) {
            double sum = 0.0;
            int sampledCount = 0;
            int clusterDraws = oneControlledCluster ? 1 : clusterIds.size();
            for (int clusterDraw = 0; clusterDraw < clusterDraws; clusterDraw++) {
                String clusterId = oneControlledCluster ? clusterIds.get(0)
                        : clusterIds.get(random.nextInt(clusterIds.size()));
                Map<String, List<PairedValue>> profileStrata = clusters.get(clusterId);
                for (List<PairedValue> stratum : profileStrata.values()) {
                    int size = stratum.size();
                    for (int draw = 0; draw < size; draw++) {
                        sum += stratum.get(random.nextInt(size)).orientedImprovement();
                        sampledCount++;
                    }
                }
            }
            means[replicate] = sum / sampledCount;
        }
        java.util.Arrays.sort(means);
        return means;
    }

    private static double exactMcNemarSignP(double[] deltas) {
        int positive = 0;
        int negative = 0;
        for (double delta : deltas) {
            if (Math.abs(delta) <= 1e-12) continue; // concordant pairs do not enter McNemar's discordant-pair denominator
            if (Math.abs(delta - 1.0) <= 1e-12) positive++;
            else if (Math.abs(delta + 1.0) <= 1e-12) negative++;
            else throw new IllegalArgumentException("binary endpoint paired differences must be -1, 0 or +1");
        }
        int discordant = positive + negative;
        if (discordant == 0) return 1.0;
        int tail = Math.min(positive, negative);
        // Exact two-sided Binomial(discordant, 0.5) tail; n <= the preregistered episode count.
        double probability = Math.scalb(1.0, -discordant);
        double cumulative = probability;
        for (int k = 1; k <= tail; k++) {
            probability *= (discordant - k + 1.0) / k;
            cumulative += probability;
        }
        return Math.min(1.0, 2.0 * cumulative);
    }

    private static double signFlipPermutationP(double[] deltas, long seed, double observedMean) {
        SplittableRandom random = new SplittableRandom(seed);
        double observed = Math.abs(observedMean);
        int extreme = 0;
        for (int replicate = 0; replicate < BOOTSTRAP_REPLICATES; replicate++) {
            double sum = 0.0;
            for (double delta : deltas) sum += random.nextBoolean() ? delta : -delta;
            double permuted = Math.abs(sum / deltas.length);
            if (permuted + 1e-15 >= observed) extreme++;
        }
        return (extreme + 1.0) / (BOOTSTRAP_REPLICATES + 1.0);
    }

    private static Double pairedCohensDz(double[] values, double mean) {
        if (values.length < 2) return null;
        double sumSquares = 0.0;
        for (double value : values) sumSquares += (value - mean) * (value - mean);
        double sd = Math.sqrt(sumSquares / (values.length - 1));
        return sd == 0.0 ? (mean == 0.0 ? 0.0 : null) : mean / sd;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static double percentile(double[] sorted, double probability) {
        if (sorted.length == 1) return sorted[0];
        double position = probability * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        double fraction = position - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    private static long digestLong(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
