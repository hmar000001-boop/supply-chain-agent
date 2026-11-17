package dev.supplychain.agent.memory.maintenance;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.semantic.SemanticAdmissionPolicy;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryMaintenanceServiceTest {
    @Test
    void maintenancePurgesOnlyExpiredWorkingUnverifiedAndSupersededRecords() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        var workingRepo = new InMemoryWorkingMemoryRepository();
        var episodeRepo = new InMemoryEpisodeRepository();
        var semanticRepo = new InMemorySemanticFactRepository();
        var policy = new MemoryRetentionPolicy(Duration.ofHours(1), Duration.ofDays(3), Duration.ofDays(30));
        var working = new WorkingMemoryService(workingRepo, policy);
        var episodes = new EpisodicMemoryService(episodeRepo, policy);
        var semantic = new SemanticMemoryService(semanticRepo, SemanticAdmissionPolicy.conservativeDefault());
        var maintenance = new MemoryMaintenanceService(workingRepo, episodeRepo, semanticRepo, policy);

        Instant now = base.plus(Duration.ofDays(50));
        var expiredWorking = working.open(MemoryFixtures.SCOPE, base);
        var currentWorking = working.open(MemoryFixtures.SCOPE, now.minus(Duration.ofMinutes(30)));
        var oldEpisode = episodes.record(MemoryFixtures.SCOPE, "Expired unverified", "Old record", EpisodeOutcome.UNKNOWN,
                base, base.plusSeconds(1), base.plusSeconds(2), List.of(MemoryFixtures.SUPPLIER), List.of(), List.of(),
                MemoryFixtures.provenance(dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM,
                        "episode-old", base.plusSeconds(2)));
        var verified = episodes.record(MemoryFixtures.SCOPE, "Verified", "Retained record", EpisodeOutcome.SUCCESS,
                base, base.plusSeconds(1), base.plusSeconds(2), List.of(MemoryFixtures.SUPPLIER), List.of(), List.of(),
                MemoryFixtures.provenance(dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM,
                        "episode-verified", base.plusSeconds(2)));
        episodes.verify(verified.id(), 0, "reviewer", MemoryFixtures.REVIEW_EVIDENCE, base.plusSeconds(3));

        Instant oldFactAt = base;
        var oldFact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "500", oldFactAt, null,
                oldFactAt, dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM);
        semantic.propose(oldFact);
        semantic.activateTrusted(oldFact.id(), 0, "source", oldFactAt.plusSeconds(1));
        var replacement = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", oldFactAt, null,
                base.plus(Duration.ofDays(10)), dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM);
        semantic.propose(replacement);
        semantic.replaceActive(oldFact.id(), 1, replacement.id(), 0, "reviewer", MemoryFixtures.REVIEW_EVIDENCE,
                base.plus(Duration.ofDays(10)).plusSeconds(1));

        var result = maintenance.purgeExpired(now);
        assertEquals(1, result.workingMemoryDeleted());
        assertEquals(1, result.unverifiedEpisodesDeleted());
        assertEquals(1, result.supersededFactsDeleted());
        assertEquals(3, result.totalDeleted());
        assertTrue(workingRepo.findById(expiredWorking.id()).isEmpty());
        assertTrue(workingRepo.findById(currentWorking.id()).isPresent());
        assertTrue(episodeRepo.findById(oldEpisode.id()).isEmpty());
        assertEquals(dev.supplychain.agent.memory.episodic.EpisodeStatus.VERIFIED, episodes.load(verified.id()).status());
        assertTrue(semanticRepo.findById(oldFact.id()).isEmpty());
        assertEquals(dev.supplychain.agent.memory.semantic.FactStatus.ACTIVE, semantic.load(replacement.id()).status());
    }

    @Test
    void policyRejectsZeroOrNegativeRetentionDurations() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetentionPolicy(
                Duration.ZERO, Duration.ofDays(1), Duration.ofDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetentionPolicy(
                Duration.ofDays(1), Duration.ofDays(-1), Duration.ofDays(1)));
    }
}
