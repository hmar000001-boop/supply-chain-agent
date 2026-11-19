package dev.supplychain.agent.evaluation.app;

import dev.supplychain.agent.evaluation.analysis.DeterminismVerifier;
import dev.supplychain.agent.evaluation.analysis.MatchedComparisonAnalyzer;
import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.runner.M8EvaluationRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs matched planner comparisons and writes immutable raw, aggregate and replay artifacts. */
public final class M8ComparisonCli {
    private M8ComparisonCli() { }

    public static void main(String[] args) throws IOException {
        Path outputDirectory = Path.of("target", "m8-b2-results");
        if (args.length > 0) {
            if (args.length != 2 || !"--output-dir".equals(args[0])) {
                throw new IllegalArgumentException("supported argument: --output-dir <path>");
            }
            outputDirectory = Path.of(args[1]);
        }
        Files.createDirectories(outputDirectory);

        var catalog = ScenarioCatalog.loadDefault();
        var runner = new M8EvaluationRunner(catalog);
        var batch = runner.runAll();
        var analyzer = new MatchedComparisonAnalyzer();
        var comparison = analyzer.analyze(batch);
        var determinism = new DeterminismVerifier().verify(batch, runner);

        write(outputDirectory.resolve("m8-b2-raw-runs.jsonl"), batch.toJsonLines());
        write(outputDirectory.resolve("m8-b2-comparison-report.jsonl"), comparison.toJsonLines());
        write(outputDirectory.resolve("m8-b2-determinism-check.jsonl"), determinism.toJsonLine() + System.lineSeparator());
        System.out.println("M8-B2 artifacts written to " + outputDirectory.toAbsolutePath());
        System.out.println("scenarioCount=" + batch.manifest().scenarioCount()
                + ", plannerCount=" + batch.manifest().plannerIds().size()
                + ", runCount=" + batch.manifest().runCount()
                + ", pairedComparisons=" + comparison.pairwiseComparisons().size()
                + ", logicalDeterminism=" + (determinism.deterministic() ? "MATCH" : "MISMATCH"));
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
