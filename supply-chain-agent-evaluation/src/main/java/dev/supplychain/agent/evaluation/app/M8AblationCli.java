package dev.supplychain.agent.evaluation.app;

import dev.supplychain.agent.evaluation.ablation.MechanismAblationRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the deterministic M8-B3 mechanism probes; does not start Maven or execute JUnit. */
public final class M8AblationCli {
    private M8AblationCli() { }

    public static void main(String[] args) throws IOException {
        Path output = Path.of("target", "m8-b3-results");
        if (args.length != 0) {
            if (args.length != 2 || !"--output-dir".equals(args[0])) {
                throw new IllegalArgumentException("supported argument: --output-dir <path>");
            }
            output = Path.of(args[1]);
        }
        Files.createDirectories(output);
        var batch = new MechanismAblationRunner().run();
        Files.write(output.resolve("m8-b3-raw-ablations.jsonl"), batch.rawJsonLines(), StandardCharsets.UTF_8);
        Files.write(output.resolve("m8-b3-paired-analysis.jsonl"), batch.analysisJsonLines(), StandardCharsets.UTF_8);
        System.out.println("M8-B3 experiments written to " + output.toAbsolutePath());
        System.out.println("scenarios=" + batch.scenarioCount()
                + ", primaryObservations=" + batch.primaryObservationCount()
                + ", deterministicReplayRows=" + batch.deterministicReplayCount()
                + ", rawRows=" + batch.observations().size()
                + ", pairedContrasts=" + batch.comparisons().size()
                + ", replayMatches=" + batch.determinismReplayMatchCount());
    }
}
