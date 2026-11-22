#!/usr/bin/env python3
"""Static preflight for the preregistered M8 scenario split register and SHA-256 parameter map."""
from __future__ import annotations
import csv
import hashlib
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROTOCOL = ROOT / "docs/research/M8-GAP01-GAP05-EXPERIMENT-SPLIT-REGISTER.csv"
RESOURCE = ROOT / "supply-chain-agent-evaluation/src/main/resources/experiment/m8-experiment-split-register.csv"
PROTOCOL_ID = "M8-GAP01-05-IEP-1.1"


def choose(prefix: str, name: str, minimum: int, maximum: int, step: int = 1) -> int:
    count = ((maximum - minimum) // step) + 1
    value = int.from_bytes(hashlib.sha256((prefix + name).encode()).digest(), "big") % count
    return minimum + value * step


def fingerprint(profile: str, values: dict[str, int]) -> str:
    ordered = {
        "demandQuantityUnits": values["demandQuantityUnits"],
        "inventoryCapacityRatioTenths": -1 if profile == "NO_FEASIBLE_CANDIDATE" else
            max(8, values["inventoryCapacityRatioTenths"]) if profile == "COMPETING_ALLOCATION" else values["inventoryCapacityRatioTenths"],
        "supplierCapacityRatioTenths": -1 if profile == "SUPPLIER_OUTAGE" else values["supplierCapacityRatioTenths"],
        "supplierOrRouteDelayDays": -1 if profile == "SUPPLIER_OUTAGE" else values["supplierOrRouteDelayDays"],
        "budgetRemainingPercent": -1 if profile == "BUDGET_EXHAUSTION" else values["budgetRemainingPercent"],
    }
    if profile == "DEMAND_SURGE": ordered["demandSurgeMultiplierTenths"] = values["demandSurgeMultiplierTenths"]
    if profile == "MULTI_ALTERNATIVE": ordered["multiAlternativeCount"] = values["multiAlternativeCount"]
    if profile == "COMPETING_ALLOCATION": ordered["competingReservationCount"] = values["competingReservationCount"]
    if profile == "BLOCKED_ROUTE": ordered["routeAvailabilityGate"] = 1 if values["routeCapacityRatioTenths"] > 0 else 0
    canonical = "|".join(f"{key}={value}" for key, value in ordered.items())
    return hashlib.sha256(canonical.encode()).hexdigest()


def main() -> int:
    assert PROTOCOL.read_bytes() == RESOURCE.read_bytes(), "runtime resource must exactly match the protocol split register"
    with PROTOCOL.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    totals = Counter()
    profile_units: dict[tuple[str, str], list[str]] = defaultdict(list)
    seeds: dict[str, set[int]] = defaultdict(set)
    families: dict[str, set[str]] = defaultdict(set)
    dev_rows = 0
    generated = 0
    for row in rows:
        split = row["split"]
        if split == "DEVELOPMENT_ONLY":
            dev_rows += 1
            continue
        count = int(row["variant_count"])
        first = int(row["seed_start_inclusive"])
        step = int(row["seed_step"])
        last = first + (count - 1) * step
        assert last <= int(row["seed_end_inclusive"]), f"seed range too short: {row['fixture_profile']}"
        totals[split] += count
        families[split].add(row["scenario_family"])
        for seed in range(first, last + 1, step):
            assert seed not in seeds[split], f"seed collision within {split}: {seed}"
            seeds[split].add(seed)
            prefix = f"{PROTOCOL_ID}|{split}|{row['fixture_profile']}|{seed}|"
            values = {
                "demandQuantityUnits": choose(prefix, "demandQuantityUnits", 40, 240, 10),
                "inventoryCapacityRatioTenths": choose(prefix, "inventoryCapacityRatioTenths", 0, 15),
                "supplierCapacityRatioTenths": choose(prefix, "supplierCapacityRatioTenths", 0, 15),
                "routeCapacityRatioTenths": choose(prefix, "routeCapacityRatioTenths", 0, 15),
                "supplierOrRouteDelayDays": choose(prefix, "supplierOrRouteDelayDays", 0, 30),
                "evidenceAgeHours": choose(prefix, "evidenceAgeHours", 0, 72),
                "demandSurgeMultiplierTenths": choose(prefix, "demandSurgeMultiplierTenths", 12, 20),
                "multiAlternativeCount": choose(prefix, "multiAlternativeCount", 2, 5),
                "competingReservationCount": choose(prefix, "competingReservationCount", 2, 5),
                "duplicateDeliveryCount": choose(prefix, "duplicateDeliveryCount", 2, 4),
                "budgetRemainingPercent": choose(prefix, "budgetRemainingPercent", 10, 100),
                "approvalLifetimeHours": choose(prefix, "approvalLifetimeHours", 0, 48),
            }
            profile_values = dict(values)
            effective = fingerprint(row["fixture_profile"], profile_values)
            profile_units[(split, row["fixture_profile"])].append(effective)
            generated += 1
    assert dev_rows == 25, f"expected 25 legacy development rows; found {dev_rows}"
    assert totals == {"TRAIN": 280, "HOLDOUT": 280, "TRANSFER": 210}, totals
    assert len(seeds["TRAIN"] & seeds["HOLDOUT"]) == 0
    assert len(seeds["TRAIN"] & seeds["TRANSFER"]) == 0
    assert len(seeds["HOLDOUT"] & seeds["TRANSFER"]) == 0
    assert len(families["TRAIN"]) == 6 and len(families["HOLDOUT"]) == 6 and len(families["TRANSFER"]) == 3
    for (split, profile), values in profile_units.items():
        expected = 30 if split == "TRANSFER" else 20
        assert len(values) == expected, (split, profile, len(values))
        assert len(set(values)) == expected, f"effective parameter collision: {split}/{profile}"
    assert generated == 770
    java_source = (ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/scenario/M8ExperimentScenarioFactory.java").read_text()
    assert 'PROTOCOL_ID + "|" + allocation.split() + "|" + allocation.fixtureProfile() + "|" + seed + "|"' in java_source
    assert "new java.util.Random" not in java_source and "Math.random(" not in java_source
    print("PASS: protocol/resource register byte equality")
    print("PASS: 25 development-only rows excluded from generator")
    print("PASS: 280 TRAIN + 280 HOLDOUT + 210 TRANSFER units; seed ranges disjoint")
    print("PASS: 6/6/3 family cardinality and 20/20/30 distinct effective parameter vectors per profile")
    print("PASS: SHA-256 parameter selection formula matches preregistration")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
