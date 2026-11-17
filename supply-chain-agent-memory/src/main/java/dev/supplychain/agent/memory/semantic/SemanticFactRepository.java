package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SemanticFactRepository {
    SemanticFact stage(SemanticFact fact);
    Optional<SemanticFact> findById(SemanticFactId id);
    SemanticFact activate(SemanticFactId id, long expectedVersion, String actor, EvidenceReference approvalEvidence, Instant at);
    SemanticFact supersedeAndActivate(SemanticFactId oldId, long expectedOldVersion,
                                      SemanticFactId newId, long expectedNewVersion, String actor,
                                      EvidenceReference approvalEvidence, Instant at);
    List<SemanticFact> supersedeAndActivateSegments(SemanticFactId oldId, long expectedOldVersion,
                                                    List<SemanticFactRevision> replacements, String actor,
                                                    EvidenceReference approvalEvidence, Instant at);
    SemanticFact reject(SemanticFactId id, long expectedVersion, String reviewer,
                        EvidenceReference reviewEvidence, Instant at);
    List<SemanticFact> findActive(SemanticFactQuery query);
    int deleteExpiredSuperseded(Instant cutoff);
}
