package dev.supplychain.agent.memory;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.semantic.FactKey;
import dev.supplychain.agent.memory.semantic.FactValue;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import java.time.Instant;

public final class MemoryFixtures {
    public static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    public static final MemoryScopeId SCOPE = new MemoryScopeId("tenant-a/request-1");
    public static final MemoryScopeId OTHER_SCOPE = new MemoryScopeId("tenant-b/request-9");
    public static final EntityReference SUPPLIER = new EntityReference("supplier", "SUP-01");
    public static final EvidenceReference SOURCE_EVIDENCE = new EvidenceReference("supplier-system-record", "SRC-101");
    public static final EvidenceReference REVIEW_EVIDENCE = new EvidenceReference("human-review", "REV-202");

    private MemoryFixtures() { }

    public static Provenance provenance(ProvenanceSource source, String sourceId, Instant recordedAt) {
        return new Provenance(source, sourceId, recordedAt.minusSeconds(30), recordedAt, "v1");
    }

    public static SemanticFact proposedCapacity(MemoryScopeId scope, String value, Instant from, Instant until,
                                         Instant recordedAt, ProvenanceSource source) {
        return SemanticFact.proposed(new FactKey(scope, SUPPLIER, "capacity.units"),
                new FactValue.Decimal(new java.math.BigDecimal(value)), from, until, recordedAt,
                provenance(source, "SRC-CAP", recordedAt));
    }
}
