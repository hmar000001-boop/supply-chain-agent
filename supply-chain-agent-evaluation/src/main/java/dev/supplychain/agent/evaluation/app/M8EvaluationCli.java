package dev.supplychain.agent.evaluation.app;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.runner.M8EvaluationRunner;

/** Prints one batch manifest and one JSONL row per scenario/procedure run. */
public final class M8EvaluationCli {
    private M8EvaluationCli() { }

    public static void main(String[] args) {
        var catalog = ScenarioCatalog.loadDefault();
        var runner = new M8EvaluationRunner(catalog);
        if (args.length == 1 && "--list".equals(args[0])) {
            catalog.scenarios().forEach(value -> System.out.println(value.scenarioId() + "," + value.fixtureVersion() + "," + value.fixtureProfile()));
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("supported argument: --list");
        System.out.print(runner.runAll().toJsonLines());
    }
}
