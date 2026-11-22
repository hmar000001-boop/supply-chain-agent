package dev.supplychain.agent.evaluation.experiment.scenario;

import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.supplier.SupplierStatus;
import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.planning.api.PlanningRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M8ExperimentScenarioFactoryTest {
    private List<ExperimentScenarioUnit> allUnits;

    @BeforeAll
    void generateAndQualifyAllSplits() {
        allUnits = M8ExperimentScenarioFactory.loadDefault().generateAllForQualification();
    }

    private List<ExperimentScenarioUnit> units(ExperimentSplit split) {
        return allUnits.stream().filter(unit -> unit.split() == split).toList();
    }

    @Test
    void preregisteredSplitsHaveExactCountsAndDisjointIdentityDomains() {
        var training = units(ExperimentSplit.TRAIN);
        var holdout = units(ExperimentSplit.HOLDOUT);
        var transfer = units(ExperimentSplit.TRANSFER);

        assertEquals(280, training.size());
        assertEquals(280, holdout.size());
        assertEquals(210, transfer.size());
        assertEquals(770, allUnits.size());
        assertEquals(770, allUnits.stream().map(ExperimentScenarioUnit::experimentUnitId).distinct().count());
        assertEquals(770, allUnits.stream().map(ExperimentScenarioUnit::seed).distinct().count());
        assertEquals(770, allUnits.stream().map(unit -> unit.fixture().fixtureFingerprint()).distinct().count());
        assertEquals(770, allUnits.stream().map(unit -> unit.fixture().initialWorldFingerprint()).distinct().count());
        assertEquals(1, allUnits.stream().map(unit -> unit.fixture().planningRequest().workingMemory().scopeId())
                .distinct().count(), "learned B1/B2/B3/B6 artifacts require one synthetic research scope");
        assertEquals(M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID,
                allUnits.getFirst().fixture().planningRequest().workingMemory().scopeId().value());
        assertTrue(training.stream().allMatch(unit -> unit.seed() >= 1_000_000 && unit.seed() < 2_000_000));
        assertTrue(holdout.stream().allMatch(unit -> unit.seed() >= 2_000_000 && unit.seed() < 3_000_000));
        assertTrue(transfer.stream().allMatch(unit -> unit.seed() >= 3_000_000 && unit.seed() < 4_000_000));
    }

    @Test
    void parameterSelectionAndFixtureConstructionAreDeterministic() {
        var first = units(ExperimentSplit.TRAIN).getFirst();
        var last = units(ExperimentSplit.TRAIN).getLast();
        var replay = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN);

        assertEquals(first.parameters(), replay.getFirst().parameters());
        assertEquals(first.effectiveInputFingerprint(), replay.getFirst().effectiveInputFingerprint());
        assertEquals(first.fixture().fixtureFingerprint(), replay.getFirst().fixture().fixtureFingerprint());
        assertEquals(last.parameters(), replay.getLast().parameters());
        assertEquals(last.fixture().initialWorldFingerprint(), replay.getLast().fixture().initialWorldFingerprint());
    }

    @Test
    void eachProfileHasTwentyOrThirtyDistinctEffectiveInputVariants() {
        for (ExperimentSplit split : ExperimentSplit.values()) {
            var splitUnits = units(split);
            int expectedPerProfile = split == ExperimentSplit.TRANSFER ? 30 : 20;
            int expectedProfiles = split == ExperimentSplit.TRANSFER ? 7 : 14;
            int expectedFamilies = split == ExperimentSplit.TRANSFER ? 3 : 6;
            assertEquals(expectedProfiles, splitUnits.stream().map(ExperimentScenarioUnit::fixtureProfile).distinct().count());
            assertEquals(expectedFamilies, splitUnits.stream().map(ExperimentScenarioUnit::scenarioFamily).distinct().count());
            for (String profile : splitUnits.stream().map(ExperimentScenarioUnit::fixtureProfile).distinct().toList()) {
                var profileUnits = splitUnits.stream().filter(unit -> unit.fixtureProfile().equals(profile)).toList();
                assertEquals(expectedPerProfile, profileUnits.size(), profile + " count");
                assertEquals(expectedPerProfile, profileUnits.stream()
                        .map(ExperimentScenarioUnit::effectiveInputFingerprint).distinct().count(), profile + " effective uniqueness");
            }
        }
    }

    @Test
    void profileInvariantsOverrideGeneratedRangesAndAuthorityRemainsProposalOnly() {
        var impossible = allUnits.stream().filter(unit -> unit.fixtureProfile().equals("NO_FEASIBLE_CANDIDATE")).findFirst().orElseThrow();
        assertTrue(impossible.fixture().planningRequest().initialWorld().inventoryLots().values().stream()
                .allMatch(lot -> lot.quantityOnHand().isZero()));

        var outage = allUnits.stream().filter(unit -> unit.fixtureProfile().equals("SUPPLIER_OUTAGE")).findFirst().orElseThrow();
        var outageSupplier = outage.fixture().planningRequest().initialWorld().suppliers().values().iterator().next();
        assertEquals(SupplierStatus.SUSPENDED, outageSupplier.status());
        assertTrue(outageSupplier.capabilities().values().stream().allMatch(capability -> capability.capacity().isZero()));

        var qualityHold = allUnits.stream().filter(unit -> unit.fixtureProfile().equals("QUALITY_HOLD")).findFirst().orElseThrow();
        assertTrue(qualityHold.fixture().planningRequest().initialWorld().inventoryLots().values().stream()
                .anyMatch(lot -> lot.qualityStatus() == LotQualityStatus.QUALITY_HOLD));

        var readOnly = allUnits.stream().filter(unit -> unit.fixtureProfile().equals("READ_ONLY")).findFirst().orElseThrow();
        assertTrue(readOnly.fixture().planningRequest().availableActionDefinitions().isEmpty());

        var competing = allUnits.stream().filter(unit -> unit.fixtureProfile().equals("COMPETING_ALLOCATION")).findFirst().orElseThrow();
        assertEquals(competing.parameters().competingReservationCount(),
                competing.fixture().planningRequest().initialWorld().reservations().size());
        var competingWorld = competing.fixture().planningRequest().initialWorld();
        assertTrue(competingWorld.inventoryLots().keySet().stream()
                .anyMatch(lotId -> competingWorld.reservedPhysicalQuantity(lotId).isPositive()));

        for (ExperimentScenarioUnit unit : allUnits) {
            PlanningRequest request = unit.fixture().planningRequest();
            assertTrue(request.availableActionDefinitions().stream().allMatch(action ->
                    request.authorityPolicy().proposalScope().contains(action.requiredCapability())));
            assertEquals(M8ExperimentScenarioFactory.GENERATOR_ID, unit.fixture().definition().fixtureVersion());
        }
    }

    @Test
    void fullParameterVectorIsRangeCheckedAndDeferredDimensionsAreExplicit() {
        var unit = units(ExperimentSplit.TRAIN).getFirst();
        var p = unit.parameters();
        assertTrue(p.demandQuantityUnits() >= 40 && p.demandQuantityUnits() <= 240 && p.demandQuantityUnits() % 10 == 0);
        assertTrue(p.inventoryCapacityRatioTenths() >= 0 && p.inventoryCapacityRatioTenths() <= 15);
        assertTrue(p.supplierCapacityRatioTenths() >= 0 && p.supplierCapacityRatioTenths() <= 15);
        assertTrue(p.routeCapacityRatioTenths() >= 0 && p.routeCapacityRatioTenths() <= 15);
        assertTrue(p.supplierOrRouteDelayDays() >= 0 && p.supplierOrRouteDelayDays() <= 30);
        assertTrue(p.evidenceAgeHours() >= 0 && p.evidenceAgeHours() <= 72);
        assertTrue(p.demandSurgeMultiplierTenths() >= 12 && p.demandSurgeMultiplierTenths() <= 20);
        assertTrue(p.multiAlternativeCount() >= 2 && p.multiAlternativeCount() <= 5);
        assertTrue(p.competingReservationCount() >= 2 && p.competingReservationCount() <= 5);
        assertTrue(p.duplicateDeliveryCount() >= 2 && p.duplicateDeliveryCount() <= 4);
        assertTrue(p.budgetRemainingPercent() >= 10 && p.budgetRemainingPercent() <= 100);
        assertTrue(p.approvalLifetimeHours() >= 0 && p.approvalLifetimeHours() <= 48);
        assertTrue(p.deferredParameterNames(unit.fixtureProfile()).contains("evidenceAgeHours"));
        assertTrue(p.deferredParameterNames(unit.fixtureProfile()).contains("duplicateDeliveryCount"));
        assertTrue(p.deferredParameterNames(unit.fixtureProfile()).contains("approvalLifetimeHours"));
        assertTrue(p.deferredParameterNames(unit.fixtureProfile()).contains("routeCapacityRatioTenths"));
        assertFalse(p.appliedParameterNames(unit.fixtureProfile()).isEmpty());
    }
}
