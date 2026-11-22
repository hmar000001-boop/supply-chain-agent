package dev.supplychain.agent.evaluation.experiment.scenario;

import java.util.List;

/**
 * Fully recorded, seed-derived parameter vector for one experiment unit.
 *
 * <p>Not every dimension is represented by the current M8 planning fixture model. The factory
 * reports which dimensions were actually applied and which remain deferred; a recorded but
 * deferred value must never be interpreted as an exercised treatment.</p>
 */
public record ExperimentScenarioParameters(
        int demandQuantityUnits,
        int inventoryCapacityRatioTenths,
        int supplierCapacityRatioTenths,
        int routeCapacityRatioTenths,
        int supplierOrRouteDelayDays,
        int evidenceAgeHours,
        int demandSurgeMultiplierTenths,
        int multiAlternativeCount,
        int competingReservationCount,
        int duplicateDeliveryCount,
        int budgetRemainingPercent,
        int approvalLifetimeHours) {

    public ExperimentScenarioParameters {
        range(demandQuantityUnits, 40, 240, "demandQuantityUnits");
        range(inventoryCapacityRatioTenths, 0, 15, "inventoryCapacityRatioTenths");
        range(supplierCapacityRatioTenths, 0, 15, "supplierCapacityRatioTenths");
        range(routeCapacityRatioTenths, 0, 15, "routeCapacityRatioTenths");
        range(supplierOrRouteDelayDays, 0, 30, "supplierOrRouteDelayDays");
        range(evidenceAgeHours, 0, 72, "evidenceAgeHours");
        range(demandSurgeMultiplierTenths, 12, 20, "demandSurgeMultiplierTenths");
        range(multiAlternativeCount, 2, 5, "multiAlternativeCount");
        range(competingReservationCount, 2, 5, "competingReservationCount");
        range(duplicateDeliveryCount, 2, 4, "duplicateDeliveryCount");
        range(budgetRemainingPercent, 10, 100, "budgetRemainingPercent");
        range(approvalLifetimeHours, 0, 48, "approvalLifetimeHours");
    }

    public List<String> appliedParameterNames(String fixtureProfile) {
        var applied = new java.util.ArrayList<String>();
        applied.add("demandQuantityUnits");
        if (!fixtureProfile.equals("NO_FEASIBLE_CANDIDATE")) applied.add("inventoryCapacityRatioTenths");
        if (!fixtureProfile.equals("SUPPLIER_OUTAGE")) applied.add("supplierCapacityRatioTenths");
        if (!fixtureProfile.equals("SUPPLIER_OUTAGE")) applied.add("supplierOrRouteDelayDays");
        if (!fixtureProfile.equals("BUDGET_EXHAUSTION")) applied.add("budgetRemainingPercent");
        switch (fixtureProfile) {
            case "DEMAND_SURGE" -> applied.add("demandSurgeMultiplierTenths");
            case "MULTI_ALTERNATIVE" -> applied.add("multiAlternativeCount");
            case "COMPETING_ALLOCATION" -> applied.add("competingReservationCount");
            case "BLOCKED_ROUTE" -> applied.add("routeAvailabilityGateDerivedFromCapacityRatio");
            default -> { }
        }
        return List.copyOf(applied);
    }

    public List<String> deferredParameterNames(String fixtureProfile) {
        var deferred = new java.util.ArrayList<>(List.of(
                "evidenceAgeHours", "duplicateDeliveryCount", "approvalLifetimeHours"));
        if (fixtureProfile.equals("NO_FEASIBLE_CANDIDATE")) deferred.add("inventoryCapacityRatioTenths");
        if (fixtureProfile.equals("SUPPLIER_OUTAGE")) {
            deferred.add("supplierCapacityRatioTenths");
            deferred.add("supplierOrRouteDelayDays");
        }
        if (fixtureProfile.equals("BUDGET_EXHAUSTION")) deferred.add("budgetRemainingPercent");
        if (!fixtureProfile.equals("DEMAND_SURGE")) deferred.add("demandSurgeMultiplierTenths");
        if (!fixtureProfile.equals("MULTI_ALTERNATIVE")) deferred.add("multiAlternativeCount");
        if (!fixtureProfile.equals("COMPETING_ALLOCATION")) deferred.add("competingReservationCount");
        deferred.add("routeCapacityRatioTenths");
        return List.copyOf(deferred);
    }

    private static void range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + "," + maximum + "]");
        }
    }
}
