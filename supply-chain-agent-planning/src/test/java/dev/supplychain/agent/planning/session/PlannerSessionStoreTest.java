package dev.supplychain.agent.planning.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.PlanningTestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlannerSessionStoreTest {
    @Test
    void savesAndLoadsVersionedCheckpoint() {
        var store = PlanningTestFixtures.sessionStore();
        var checkpoint = new PlannerSessionCheckpoint("session-store", PlannerId.TOT_BOUNDED_SEARCH,
                "request-fp", "tot-config-v1", 1, PlannerSessionStatus.ACTIVE,
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, java.time.Duration.ZERO, PlanningTestFixtures.NOW);

        assertEquals(checkpoint, store.save(checkpoint, 0));
        assertEquals(checkpoint, store.find("session-store").orElseThrow());
    }

    @Test
    void rejectsConcurrentOrStaleCheckpointWrites() {
        var store = PlanningTestFixtures.sessionStore();
        var first = new PlannerSessionCheckpoint("session-conflict", PlannerId.TOT_BOUNDED_SEARCH,
                "request-fp", "tot-config-v1", 1, PlannerSessionStatus.ACTIVE,
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, java.time.Duration.ZERO, PlanningTestFixtures.NOW);
        store.save(first, 0);
        var conflicting = new PlannerSessionCheckpoint("session-conflict", PlannerId.TOT_BOUNDED_SEARCH,
                "request-fp", "tot-config-v1", 2, PlannerSessionStatus.COMPLETED,
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, java.time.Duration.ZERO, PlanningTestFixtures.NOW.plusSeconds(1));

        assertThrows(PlannerCheckpointConflictException.class, () -> store.save(conflicting, 0));
        assertEquals(1, store.find("session-conflict").orElseThrow().revision());
    }

    @Test
    void rejectsInvalidRevisionJump() {
        var store = PlanningTestFixtures.sessionStore();
        var invalid = new PlannerSessionCheckpoint("session-jump", PlannerId.RAP_INSPIRED_MCTS,
                "request-fp", "mcts-config-v1", 2, PlannerSessionStatus.ACTIVE,
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, java.time.Duration.ZERO, PlanningTestFixtures.NOW);
        assertThrows(PlannerCheckpointConflictException.class, () -> store.save(invalid, 0));
    }
}
