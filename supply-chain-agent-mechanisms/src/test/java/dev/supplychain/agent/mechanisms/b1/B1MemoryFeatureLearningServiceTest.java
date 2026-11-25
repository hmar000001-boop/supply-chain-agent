package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B1MemoryFeatureLearningServiceTest {
    private static final MemoryScopeId SCOPE = new MemoryScopeId("b1-learning-tenant");
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private B1MemoryCandidate candidate() {
        return new B1MemoryCandidate("episodic:episode-7:3", SCOPE, B1MemoryCandidateKind.EPISODIC,
                "episodic:episode-7@3", List.of(), Map.of(), 0.5, 2, Duration.ofMillis(10),
                new MaintenanceProfile(0.1, 0.1, 0.1, Duration.ofDays(30), NOW));
    }

    @Test void selectedAccessesAreDurableAndEnrichTheNextB1Candidate() {
        var repository = new InMemoryB1MemoryFeatureRepository();
        var service = new B1MemoryFeatureLearningService(repository);
        var candidate = candidate();
        service.recordSelected(List.of(candidate), Set.of("inventory.shortage"), "exec-7", "evidence-7", NOW);
        service.recordSelected(List.of(candidate), Set.of("inventory.shortage"), "exec-7", "evidence-7", NOW);

        var enriched = service.enrich(List.of(candidate), NOW.plusSeconds(1)).getFirst();
        assertEquals(List.of(NOW), enriched.accessHistory());
        assertEquals(0.5, enriched.cueAssociationStrengths().get("inventory.shortage"));
        assertEquals(0.5, enriched.expectedDownstreamUtility());
        assertEquals(1, service.find(new B1MemoryCandidateReference(B1MemoryCandidateKind.EPISODIC,
                SCOPE, "episode-7", 3)).orElseThrow().revision());
    }

    @Test void verifiedDownstreamOutcomeUpdatesPersistedUseUtilityAndCueStatistics() {
        var reference = new B1MemoryCandidateReference(B1MemoryCandidateKind.EPISODIC, SCOPE, "episode-7", 3);
        var initial = B1MemoryFeatureState.empty(reference, NOW.minusSeconds(1));
        var learned = initial.applyVerifiedOutcome("verified-outcome-7", DownstreamOutcomeSignal.USEFUL,
                Set.of("inventory.shortage"), NOW);

        assertEquals(List.of(NOW), learned.verifiedUseHistory());
        assertEquals(1.0, learned.expectedDownstreamUtility());
        assertEquals(0.6, learned.cueAssociationStrengths().get("inventory.shortage"));
        assertEquals(1, learned.usefulOutcomes());
        assertEquals(learned, learned.applyVerifiedOutcome("verified-outcome-7",
                DownstreamOutcomeSignal.HARMFUL, Set.of("inventory.shortage"), NOW.plusSeconds(1)),
                "same verified outcome must not be applied twice");

        var repository = new InMemoryB1MemoryFeatureRepository();
        repository.saveNew(new B1MemoryFeatureState(reference, List.of(), learned.verifiedUseHistory(),
                learned.cueAssociationStrengths(), learned.expectedDownstreamUtility(), learned.usefulOutcomes(),
                learned.harmfulOutcomes(), learned.neutralOutcomes(), learned.appliedOutcomeKeys(),
                learned.appliedAccessEventKeys(), 1, NOW));
        var service = new B1MemoryFeatureLearningService(repository);
        var providerCandidate = new B1MemoryCandidate("episodic:episode-7:3", SCOPE,
                B1MemoryCandidateKind.EPISODIC, "episodic:episode-7@3", List.of(), Map.of(),
                12.0, 2, Duration.ofMillis(10),
                new MaintenanceProfile(0.1, 0.1, 0.1, Duration.ofDays(30), NOW));
        var enriched = service.enrich(List.of(providerCandidate), NOW.plusSeconds(1)).getFirst();
        assertEquals(List.of(NOW), enriched.accessHistory(),
                "verified downstream use must affect the next B1 accessibility/activation input");
        assertEquals(12.0, enriched.expectedDownstreamUtility(),
                "normalized outcome statistics must not replace provider utility on another scale");
        assertEquals(0.6, enriched.cueAssociationStrengths().get("inventory.shortage"));
        assertEquals(1.0, service.find(reference).orElseThrow().expectedDownstreamUtility(),
                "normalized outcome utility remains persisted separately");
    }
}
