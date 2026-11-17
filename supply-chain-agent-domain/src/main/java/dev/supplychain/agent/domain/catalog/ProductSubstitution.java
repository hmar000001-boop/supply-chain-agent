package dev.supplychain.agent.domain.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** An explicit, time-bounded qualification from a required product to an approved substitute. */
public record ProductSubstitution(
        SubstitutionId id,
        ProductId requiredProductId,
        ProductId substituteProductId,
        BigDecimal substituteUnitsPerRequiredUnit,
        SubstitutionStatus status,
        Instant validFrom,
        Instant validUntil) {

    public ProductSubstitution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredProductId, "requiredProductId");
        Objects.requireNonNull(substituteProductId, "substituteProductId");
        Objects.requireNonNull(substituteUnitsPerRequiredUnit, "substituteUnitsPerRequiredUnit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        if (requiredProductId.equals(substituteProductId)) {
            throw new IllegalArgumentException("A product cannot be its own substitute");
        }
        if (substituteUnitsPerRequiredUnit.signum() <= 0) {
            throw new IllegalArgumentException("Substitution ratio must be positive");
        }
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
    }

    public boolean approvedAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return status == SubstitutionStatus.APPROVED
                && !instant.isBefore(validFrom)
                && (validUntil == null || instant.isBefore(validUntil));
    }
}
