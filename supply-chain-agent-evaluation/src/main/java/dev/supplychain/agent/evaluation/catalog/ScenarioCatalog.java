package dev.supplychain.agent.evaluation.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads and validates the versioned M8 scenario register bundled with the evaluation module. */
public final class ScenarioCatalog {
    public static final String RESOURCE = "/scenarios/m8-scenario-register.csv";
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "scenario_id", "kind", "scenario", "setup", "expected_invariant_or_behavior",
            "comparators_or_controls", "primary_measurements", "status", "fixture_version",
            "fixture_profile", "expected_effect_boundary", "seed", "budget_profile");

    private final List<ScenarioDefinition> scenarios;
    private final Map<String, ScenarioDefinition> byId;

    private ScenarioCatalog(List<ScenarioDefinition> scenarios) {
        this.scenarios = List.copyOf(scenarios);
        var index = new LinkedHashMap<String, ScenarioDefinition>();
        for (ScenarioDefinition scenario : this.scenarios) {
            if (index.putIfAbsent(scenario.scenarioId(), scenario) != null) {
                throw new IllegalArgumentException("duplicate scenario ID: " + scenario.scenarioId());
            }
        }
        this.byId = Map.copyOf(index);
    }

    public static ScenarioCatalog loadDefault() {
        InputStream input = ScenarioCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("missing scenario register resource: " + RESOURCE);
        try (input) {
            return load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("could not load scenario register", ex);
        }
    }

    static ScenarioCatalog load(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<List<String>> rows = parseCsv(reader);
            if (rows.size() < 2) throw new IllegalArgumentException("scenario register must contain a header and at least one scenario");
            List<String> header = rows.getFirst();
            if (!header.equals(REQUIRED_COLUMNS)) {
                throw new IllegalArgumentException("scenario register columns differ from the required versioned schema: " + header);
            }
            var definitions = new ArrayList<ScenarioDefinition>();
            for (int index = 1; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                if (row.size() != header.size()) throw new IllegalArgumentException("scenario register row " + (index + 1) + " has " + row.size() + " fields; expected " + header.size());
                definitions.add(new ScenarioDefinition(
                        row.get(0), row.get(1), row.get(2), row.get(3), row.get(4), row.get(5), row.get(6), row.get(7),
                        row.get(8), row.get(9), ScenarioDefinition.ExpectedEffectBoundary.valueOf(row.get(10)),
                        Long.parseLong(row.get(11)), row.get(12)));
            }
            return new ScenarioCatalog(definitions);
        }
    }

    public List<ScenarioDefinition> scenarios() { return scenarios; }

    public ScenarioDefinition require(String scenarioId) {
        ScenarioDefinition result = byId.get(Objects.requireNonNull(scenarioId, "scenarioId"));
        if (result == null) throw new IllegalArgumentException("unknown scenario ID: " + scenarioId);
        return result;
    }

    public int requestFamilyCount() {
        return (int) scenarios.stream().filter(value -> value.kind().equalsIgnoreCase("Request family")).count();
    }

    public int failureScenarioCount() {
        return (int) scenarios.stream().filter(value -> value.kind().toLowerCase(java.util.Locale.ROOT).startsWith("failure")).count();
    }

    private static List<List<String>> parseCsv(BufferedReader reader) throws IOException {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        int current;
        while ((current = reader.read()) != -1) {
            char ch = (char) current;
            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else field.append(ch);
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(stripCarriageReturn(field.toString()));
                field.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isEmpty())) rows.add(List.copyOf(row));
                row.clear();
            } else field.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("unterminated quoted CSV field");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(stripCarriageReturn(field.toString()));
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }
}
