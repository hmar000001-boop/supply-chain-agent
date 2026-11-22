package dev.supplychain.agent.evaluation.app;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Emits one split's scenario-unit manifest; it does not run treatments or create outcome labels. */
public final class M8ExperimentScenarioFactoryCli {
    private M8ExperimentScenarioFactoryCli() { }

    public static void main(String[] args) throws IOException {
        ExperimentSplit split = null;
        Path output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--split" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--split requires TRAIN, HOLDOUT or TRANSFER");
                    split = ExperimentSplit.valueOf(args[i].toUpperCase(java.util.Locale.ROOT));
                }
                case "--output" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--output requires a path");
                    output = Path.of(args[i]);
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (split == null || output == null) {
            throw new IllegalArgumentException("usage: M8ExperimentScenarioFactoryCli --split TRAIN|HOLDOUT|TRANSFER --output <csv-path>");
        }
        List<ExperimentScenarioUnit> units = M8ExperimentScenarioFactory.loadDefault().generate(split);
        var csv = new StringBuilder("protocol_id,split,scenario_family,fixture_profile,base_scenario_id,experiment_unit_id,profile_ordinal,variant_ordinal,seed,fixture_fingerprint,world_fingerprint,effective_input_fingerprint,demand_quantity_units,inventory_capacity_ratio_tenths,supplier_capacity_ratio_tenths,route_capacity_ratio_tenths,supplier_or_route_delay_days,evidence_age_hours,demand_surge_multiplier_tenths,multi_alternative_count,competing_reservation_count,duplicate_delivery_count,budget_remaining_percent,approval_lifetime_hours,applied_parameters,deferred_parameters\n");
        for (ExperimentScenarioUnit unit : units) {
            var p = unit.parameters();
            csv.append(row(unit.protocolId(), unit.split().name(), unit.scenarioFamily(), unit.fixtureProfile(),
                    unit.baseScenarioId(), unit.experimentUnitId(), Integer.toString(unit.profileOrdinal()),
                    Integer.toString(unit.variantOrdinal()), Long.toString(unit.seed()), unit.fixture().fixtureFingerprint(), unit.fixture().initialWorldFingerprint(),
                    unit.effectiveInputFingerprint(), Integer.toString(p.demandQuantityUnits()),
                    Integer.toString(p.inventoryCapacityRatioTenths()), Integer.toString(p.supplierCapacityRatioTenths()),
                    Integer.toString(p.routeCapacityRatioTenths()), Integer.toString(p.supplierOrRouteDelayDays()),
                    Integer.toString(p.evidenceAgeHours()), Integer.toString(p.demandSurgeMultiplierTenths()),
                    Integer.toString(p.multiAlternativeCount()), Integer.toString(p.competingReservationCount()),
                    Integer.toString(p.duplicateDeliveryCount()), Integer.toString(p.budgetRemainingPercent()),
                    Integer.toString(p.approvalLifetimeHours()),
                    String.join(";", p.appliedParameterNames(unit.fixtureProfile())),
                    String.join(";", p.deferredParameterNames(unit.fixtureProfile()))));
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, csv, StandardCharsets.UTF_8);
        System.out.println("Generated " + units.size() + " " + split + " scenario units -> " + output);
        System.out.println("No treatment executed; no labels or outcomes were produced.");
    }

    private static String row(String... values) {
        return Arrays.stream(values).map(M8ExperimentScenarioFactoryCli::escapeCsv)
                .collect(Collectors.joining(",")) + "\n";
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
