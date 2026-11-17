package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import java.time.Instant;
import java.util.Objects;

/** Current lot-level quality disposition; not a substitute for the underlying inspection report. */
public record QualityDisposition(InventoryLotId lotId, LotQualityStatus status,
                                 Instant heldAt, String holdReason, long lotVersion) {
    public QualityDisposition {
        lotId = Objects.requireNonNull(lotId, "lotId");
        status = Objects.requireNonNull(status, "status");
        if (lotVersion < 0) throw new IllegalArgumentException("lotVersion cannot be negative");
        if (status == LotQualityStatus.QUALITY_HOLD && (heldAt == null || holdReason == null || holdReason.isBlank())) {
            throw new IllegalArgumentException("quality-hold disposition requires its timestamp and reason");
        }
    }
}
