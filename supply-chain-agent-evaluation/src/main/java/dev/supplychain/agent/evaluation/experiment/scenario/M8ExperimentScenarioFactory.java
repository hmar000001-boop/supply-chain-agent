package dev.supplychain.agent.evaluation.experiment.scenario;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic generator for the preregistered M8 train/holdout/transfer scenario units.
 *
 * <p>Parameter selection follows SHA-256(protocolId|split|fixtureProfile|seed|parameterName).
 * It uses neither ambient time nor process-global random state. The generated fixtures are
 * simulator inputs only and never represent execution authorization.</p>
 */
public final class M8ExperimentScenarioFactory {
    public static final String PROTOCOL_ID = "M8-GAP01-05-IEP-1.0";
    public static final String GENERATOR_ID = "M8ExperimentScenarioFactory-v1";
    public static final String SPLIT_REGISTER_RESOURCE = "/experiment/m8-experiment-split-register.csv";

    private final ScenarioCatalog catalog;
    private final ScenarioFixtureFactory fixtureFactory;
    private final List<Allocation> allocations;

    public M8ExperimentScenarioFactory(ScenarioCatalog catalog, ScenarioFixtureFactory fixtureFactory) {
        this(catalog, fixtureFactory, loadDefaultAllocations());
    }

    M8ExperimentScenarioFactory(ScenarioCatalog catalog, ScenarioFixtureFactory fixtureFactory,
                                List<Allocation> allocations) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.fixtureFactory = Objects.requireNonNull(fixtureFactory, "fixtureFactory");
        this.allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations"));
        validateAllocations(this.allocations, this.catalog);
    }

    public static M8ExperimentScenarioFactory loadDefault() {
        ScenarioCatalog catalog = ScenarioCatalog.loadDefault();
        return new M8ExperimentScenarioFactory(catalog, new ScenarioFixtureFactory(catalog));
    }

    /** Generates only the explicitly requested split. */
    public List<ExperimentScenarioUnit> generate(ExperimentSplit split) {
        Objects.requireNonNull(split, "split");
        var units = new ArrayList<ExperimentScenarioUnit>();
        for (Allocation allocation : allocations) {
            if (allocation.split() == split) units.addAll(generate(allocation));
        }
        validateGeneratedSplit(split, units);
        return List.copyOf(units);
    }

    /** Qualification helper: constructs descriptors/fixtures for all splits, but runs no treatment. */
    public List<ExperimentScenarioUnit> generateAllForQualification() {
        var all = new ArrayList<ExperimentScenarioUnit>();
        for (ExperimentSplit split : ExperimentSplit.values()) all.addAll(generate(split));
        validateCrossSplitDisjointness(all);
        return List.copyOf(all);
    }

    private List<ExperimentScenarioUnit> generate(Allocation allocation) {
        var units = new ArrayList<ExperimentScenarioUnit>(allocation.variantCount());
        long seed = allocation.seedStart();
        for (int ordinal = 1; ordinal <= allocation.variantCount(); ordinal++, seed += allocation.seedStep()) {
            ScenarioDefinition base = catalog.require(allocation.baseScenarioId());
            ExperimentScenarioParameters parameters = parameters(allocation, seed);
            String scenarioId = "EXP-" + allocation.split() + "-" + allocation.fixtureProfile() + "-" + seed;
            ScenarioDefinition variantDefinition = new ScenarioDefinition(
                    scenarioId,
                    base.kind(),
                    base.scenario() + " [seeded experiment variant " + seed + "]",
                    base.setup() + " [" + allocation.split() + "; seed=" + seed + "]",
                    base.expectedInvariantOrBehavior(),
                    base.comparatorsOrControls(),
                    base.primaryMeasurements(),
                    base.status(),
                    GENERATOR_ID,
                    allocation.fixtureProfile(),
                    base.expectedEffectBoundary(),
                    seed,
                    base.budgetProfile());
            ScenarioFixture fixture = fixtureFactory.create(variantDefinition, parameters);
            String unitId = allocation.experimentUnitIdTemplate().replace("{seed}", Long.toString(seed));
            String effectiveFingerprint = effectiveInputFingerprint(allocation.fixtureProfile(), parameters);
            units.add(new ExperimentScenarioUnit(PROTOCOL_ID, allocation.split(), allocation.scenarioFamily(),
                    allocation.fixtureProfile(), allocation.baseScenarioId(), unitId, allocation.profileOrdinal(), ordinal, seed,
                    parameters, effectiveFingerprint, fixture));
        }
        return List.copyOf(units);
    }

    private static ExperimentScenarioParameters parameters(Allocation allocation, long seed) {
        String prefix = PROTOCOL_ID + "|" + allocation.split() + "|" + allocation.fixtureProfile() + "|" + seed + "|";
        return new ExperimentScenarioParameters(
                choose(prefix, "demandQuantityUnits", 40, 240, 10),
                choose(prefix, "inventoryCapacityRatioTenths", 0, 15, 1),
                choose(prefix, "supplierCapacityRatioTenths", 0, 15, 1),
                choose(prefix, "routeCapacityRatioTenths", 0, 15, 1),
                choose(prefix, "supplierOrRouteDelayDays", 0, 30, 1),
                choose(prefix, "evidenceAgeHours", 0, 72, 1),
                choose(prefix, "demandSurgeMultiplierTenths", 12, 20, 1),
                choose(prefix, "multiAlternativeCount", 2, 5, 1),
                choose(prefix, "competingReservationCount", 2, 5, 1),
                choose(prefix, "duplicateDeliveryCount", 2, 4, 1),
                choose(prefix, "budgetRemainingPercent", 10, 100, 1),
                choose(prefix, "approvalLifetimeHours", 0, 48, 1));
    }

    private static int choose(String prefix, String parameterName, int minimum, int maximum, int step) {
        int count = ((maximum - minimum) / step) + 1;
        int index = new BigInteger(1, sha256(prefix + parameterName)).mod(BigInteger.valueOf(count)).intValueExact();
        return minimum + index * step;
    }

    private static String effectiveInputFingerprint(String profile, ExperimentScenarioParameters p) {
        var values = new LinkedHashMap<String, Integer>();
        values.put("demandQuantityUnits", p.demandQuantityUnits());
        values.put("inventoryCapacityRatioTenths", profile.equals("NO_FEASIBLE_CANDIDATE") ? -1
                : profile.equals("COMPETING_ALLOCATION") ? Math.max(8, p.inventoryCapacityRatioTenths()) : p.inventoryCapacityRatioTenths());
        values.put("supplierCapacityRatioTenths", profile.equals("SUPPLIER_OUTAGE") ? -1 : p.supplierCapacityRatioTenths());
        values.put("supplierOrRouteDelayDays", profile.equals("SUPPLIER_OUTAGE") ? -1 : p.supplierOrRouteDelayDays());
        values.put("budgetRemainingPercent", profile.equals("BUDGET_EXHAUSTION") ? -1 : p.budgetRemainingPercent());
        if (profile.equals("DEMAND_SURGE")) values.put("demandSurgeMultiplierTenths", p.demandSurgeMultiplierTenths());
        if (profile.equals("MULTI_ALTERNATIVE")) values.put("multiAlternativeCount", p.multiAlternativeCount());
        if (profile.equals("COMPETING_ALLOCATION")) values.put("competingReservationCount", p.competingReservationCount());
        if (profile.equals("BLOCKED_ROUTE")) values.put("routeAvailabilityGate", p.routeCapacityRatioTenths() > 0 ? 1 : 0);
        String canonical = values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("|"));
        return HexFormat.of().formatHex(sha256(canonical));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void validateGeneratedSplit(ExperimentSplit split, List<ExperimentScenarioUnit> units) {
        int expected = split == ExperimentSplit.TRANSFER ? 210 : 280;
        if (units.size() != expected) {
            throw new IllegalStateException(split + " generated " + units.size() + " units; expected " + expected);
        }
        ensureUnique(units.stream().map(ExperimentScenarioUnit::experimentUnitId).toList(),
                split + " experiment unit IDs");
        ensureUnique(units.stream().map(unit -> unit.fixture().fixtureFingerprint()).toList(),
                split + " fixture fingerprints");
        ensureUnique(units.stream().map(unit -> unit.fixture().initialWorldFingerprint()).toList(),
                split + " initial-world fingerprints");
        long minimumSeed = switch (split) {
            case TRAIN -> 1_000_000L;
            case HOLDOUT -> 2_000_000L;
            case TRANSFER -> 3_000_000L;
        };
        long maximumSeedExclusive = minimumSeed + 1_000_000L;
        if (units.stream().anyMatch(unit -> unit.seed() < minimumSeed || unit.seed() >= maximumSeedExclusive)) {
            throw new IllegalStateException(split + " contains a seed outside its preregistered band");
        }
        int expectedProfiles = split == ExperimentSplit.TRANSFER ? 7 : 14;
        int expectedFamilies = split == ExperimentSplit.TRANSFER ? 3 : 6;
        if (units.stream().map(ExperimentScenarioUnit::fixtureProfile).distinct().count() != expectedProfiles) {
            throw new IllegalStateException(split + " profile cardinality differs from the preregistered " + expectedProfiles);
        }
        if (units.stream().map(ExperimentScenarioUnit::scenarioFamily).distinct().count() != expectedFamilies) {
            throw new IllegalStateException(split + " family cardinality differs from the preregistered " + expectedFamilies);
        }
        int expectedPerProfile = split == ExperimentSplit.TRANSFER ? 30 : 20;
        for (String profile : units.stream().map(ExperimentScenarioUnit::fixtureProfile).distinct().toList()) {
            List<ExperimentScenarioUnit> profileUnits = units.stream()
                    .filter(unit -> unit.fixtureProfile().equals(profile)).toList();
            if (profileUnits.size() != expectedPerProfile) {
                throw new IllegalStateException(split + " profile " + profile + " has " + profileUnits.size()
                        + " units; expected " + expectedPerProfile);
            }
            long distinctInputs = profileUnits.stream().map(ExperimentScenarioUnit::effectiveInputFingerprint)
                    .distinct().count();
            if (distinctInputs != profileUnits.size()) {
                throw new IllegalStateException(split + " profile " + profile
                        + " has duplicate effective inputs: " + distinctInputs + "/" + profileUnits.size());
            }
        }
    }

    private static void validateCrossSplitDisjointness(List<ExperimentScenarioUnit> units) {
        ensureUnique(units.stream().map(ExperimentScenarioUnit::experimentUnitId).toList(), "cross-split experiment unit IDs");
        ensureUnique(units.stream().map(ExperimentScenarioUnit::seed).map(String::valueOf).toList(), "cross-split seeds");
        ensureUnique(units.stream().map(unit -> unit.fixture().fixtureFingerprint()).toList(), "cross-split fixture fingerprints");
        ensureUnique(units.stream().map(unit -> unit.fixture().initialWorldFingerprint()).toList(), "cross-split world fingerprints");
    }

    private static void ensureUnique(List<String> values, String label) {
        if (values.size() != values.stream().distinct().count()) {
            throw new IllegalStateException("duplicate " + label + " detected");
        }
    }

    private static void validateAllocations(List<Allocation> allocations, ScenarioCatalog catalog) {
        if (allocations.isEmpty()) throw new IllegalArgumentException("experiment split register is empty");
        var profileOrdinalsBySplit = new java.util.EnumMap<ExperimentSplit, java.util.HashSet<Integer>>(ExperimentSplit.class);
        for (ExperimentSplit split : ExperimentSplit.values()) profileOrdinalsBySplit.put(split, new java.util.HashSet<>());
        for (Allocation allocation : allocations) {
            allocation.allBaseScenarioIds().forEach(catalog::require);
            if (allocation.variantCount() < 1 || allocation.seedStep() < 1 || allocation.seedStart() < 0) {
                throw new IllegalArgumentException("invalid seed allocation for " + allocation.fixtureProfile());
            }
            long lastSeed = allocation.seedStart() + (long) (allocation.variantCount() - 1) * allocation.seedStep();
            if (lastSeed != allocation.seedEnd()) {
                throw new IllegalArgumentException("inexact seed allocation for " + allocation.fixtureProfile());
            }
            if (!profileOrdinalsBySplit.get(allocation.split()).add(allocation.profileOrdinal())) {
                throw new IllegalArgumentException("duplicate profile ordinal " + allocation.profileOrdinal()
                        + " in " + allocation.split());
            }
            if (allocation.profileOrdinal() < 1) {
                throw new IllegalArgumentException("profile ordinal must be positive for " + allocation.fixtureProfile());
            }
            if (allocation.experimentUnitIdTemplate().isBlank() || !allocation.experimentUnitIdTemplate().contains("{seed}")) {
                throw new IllegalArgumentException("unit ID template must contain {seed} for " + allocation.fixtureProfile());
            }
        }
        var allSeeds = new java.util.HashSet<Long>();
        for (Allocation allocation : allocations) {
            for (int i = 0; i < allocation.variantCount(); i++) {
                long seed = allocation.seedStart() + (long) i * allocation.seedStep();
                if (!allSeeds.add(seed)) throw new IllegalArgumentException("cross-split seed overlap: " + seed);
            }
        }
        for (ExperimentSplit split : ExperimentSplit.values()) {
            long rows = allocations.stream().filter(value -> value.split() == split).count();
            if (rows == 0) throw new IllegalArgumentException("split register has no rows for " + split);
            int expectedUnits = split == ExperimentSplit.TRANSFER ? 210 : 280;
            long minimumSeed = switch (split) {
                case TRAIN -> 1_000_000L;
                case HOLDOUT -> 2_000_000L;
                case TRANSFER -> 3_000_000L;
            };
            long maximumSeedExclusive = minimumSeed + 1_000_000L;
            int expectedPerProfile = split == ExperimentSplit.TRANSFER ? 30 : 20;
            if (allocations.stream().filter(value -> value.split() == split)
                    .anyMatch(value -> value.seedStart() < minimumSeed || value.seedEnd() >= maximumSeedExclusive
                            || value.variantCount() != expectedPerProfile)) {
                throw new IllegalArgumentException(split + " allocation violates preregistered seed band or per-profile count");
            }
            int actualUnits = allocations.stream().filter(value -> value.split() == split)
                    .mapToInt(Allocation::variantCount).sum();
            if (actualUnits != expectedUnits) {
                throw new IllegalArgumentException(split + " split register declares " + actualUnits
                        + " units; expected " + expectedUnits);
            }
            var seeds = new java.util.HashSet<Long>();
            for (Allocation allocation : allocations.stream().filter(value -> value.split() == split).toList()) {
                for (int i = 0; i < allocation.variantCount(); i++) {
                    long seed = allocation.seedStart() + (long) i * allocation.seedStep();
                    if (!seeds.add(seed)) throw new IllegalArgumentException("overlapping " + split + " seed " + seed);
                }
            }
        }
    }

    private static List<Allocation> loadDefaultAllocations() {
        InputStream input = M8ExperimentScenarioFactory.class.getResourceAsStream(SPLIT_REGISTER_RESOURCE);
        if (input == null) throw new IllegalStateException("missing experiment split register: " + SPLIT_REGISTER_RESOURCE);
        try (input; var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<List<String>> rows = parseCsv(reader);
            List<String> header = List.of("split", "role", "scenario_family", "fixture_profile", "base_scenario_ids",
                    "profile_ordinal", "variant_count", "seed_start_inclusive", "seed_end_inclusive", "seed_step",
                    "experiment_unit_id_template", "generator_version", "allowed_use", "notes");
            if (rows.size() < 2 || !rows.getFirst().equals(header)) {
                throw new IllegalArgumentException("experiment split register header/schema is invalid");
            }
            var result = new ArrayList<Allocation>();
            int legacyDevelopmentRows = 0;
            for (int index = 1; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                if (row.size() != header.size()) throw new IllegalArgumentException("invalid split register row " + (index + 1));
                if (row.get(0).equals("DEVELOPMENT_ONLY")) {
                    legacyDevelopmentRows++;
                    continue;
                }
                ExperimentSplit split = ExperimentSplit.valueOf(row.get(0));
                String expectedUse = switch (split) {
                    case TRAIN -> "TRAINING_ONLY";
                    case HOLDOUT -> "CONFIRMATORY_ONLY";
                    case TRANSFER -> "FROZEN_ARTIFACT_TRANSFER_ONLY";
                };
                if (!expectedUse.equals(row.get(12))) {
                    throw new IllegalArgumentException("allowed_use does not match split on register row " + (index + 1));
                }
                if (!expectedUse.equals(row.get(1))) {
                    throw new IllegalArgumentException("role does not match split on register row " + (index + 1));
                }
                String baseIds = row.get(4);
                List<String> baseScenarioIds = java.util.Arrays.stream(baseIds.split(";", -1))
                        .map(String::trim).filter(value -> !value.isEmpty()).toList();
                if (baseScenarioIds.isEmpty()) throw new IllegalArgumentException("base scenario IDs are empty on register row " + (index + 1));
                String baseScenarioId = baseScenarioIds.getFirst();
                if (!GENERATOR_ID.equals(row.get(11))) throw new IllegalArgumentException("unexpected generator version in row " + (index + 1));
                result.add(new Allocation(split, row.get(2), row.get(3), baseScenarioId, baseScenarioIds,
                        Integer.parseInt(row.get(5)), Integer.parseInt(row.get(6)),
                        Long.parseLong(row.get(7)), Long.parseLong(row.get(8)), Integer.parseInt(row.get(9)),
                        row.get(10), row.get(11), row.get(12)));
            }
            if (legacyDevelopmentRows != 25) {
                throw new IllegalArgumentException("split register must preserve exactly 25 DEVELOPMENT_ONLY rows; found " + legacyDevelopmentRows);
            }
            return List.copyOf(result);
        } catch (IOException ex) {
            throw new IllegalStateException("could not load experiment split register", ex);
        }
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
        if (quoted) throw new IllegalArgumentException("unterminated split register CSV field");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(stripCarriageReturn(field.toString()));
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    private record Allocation(ExperimentSplit split, String scenarioFamily, String fixtureProfile,
                              String baseScenarioId, List<String> allBaseScenarioIds, int profileOrdinal, int variantCount,
                              long seedStart, long seedEnd, int seedStep,
                              String experimentUnitIdTemplate, String generatorVersion, String allowedUse) { }
}
