package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioCatalogTest {
    @Test
    void loadsEveryRegisteredRequestAndFailureScenario() {
        ScenarioCatalog catalog = ScenarioCatalog.loadDefault();
        assertEquals(25, catalog.scenarios().size());
        assertEquals(11, catalog.requestFamilyCount());
        assertEquals(14, catalog.failureScenarioCount());
        assertTrue(catalog.scenarios().stream().allMatch(s -> s.fixtureVersion().equals("M8-B1-v1")));
    }
}
