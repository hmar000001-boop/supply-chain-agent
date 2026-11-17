package dev.supplychain.agent.domain.procurement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.supplier.SupplierId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseOrderInvariantTest {
    private static final Instant CREATED_AT = Instant.parse("2026-11-17T12:00:00Z");
    private static final SupplierId SUPPLIER = new SupplierId("SUP-PO-1");
    private static final ProductId PRODUCT = new ProductId("SKU-PO-1");

    @Test
    void draftPurchaseOrderCannotContainReceivedQuantity() {
        assertThrows(IllegalArgumentException.class, () -> order(
                PurchaseOrderStatus.DRAFT, "10", "1"));
    }

    @Test
    void receivedPurchaseOrderMustHaveNoOpenQuantity() {
        assertThrows(IllegalArgumentException.class, () -> order(
                PurchaseOrderStatus.RECEIVED, "10", "9"));
    }

    @Test
    void partiallyReceivedPurchaseOrderMustHaveBothReceivedAndOpenQuantity() {
        assertThrows(IllegalArgumentException.class, () -> order(
                PurchaseOrderStatus.PARTIALLY_RECEIVED, "10", "0"));
        assertThrows(IllegalArgumentException.class, () -> order(
                PurchaseOrderStatus.PARTIALLY_RECEIVED, "10", "10"));
    }

    @Test
    void validPartialReceiptIsAccepted() {
        assertDoesNotThrow(() -> order(PurchaseOrderStatus.PARTIALLY_RECEIVED, "10", "4"));
    }

    private static PurchaseOrder order(PurchaseOrderStatus status, String ordered, String received) {
        var line = new PurchaseOrderLine(
                PRODUCT,
                Quantity.of(ordered, UnitOfMeasure.EACH),
                Quantity.of(received, UnitOfMeasure.EACH),
                Money.of("12.50", "CAD"));
        return new PurchaseOrder(
                new PurchaseOrderId("PO-TEST"),
                SUPPLIER,
                CREATED_AT,
                CREATED_AT.plusSeconds(86_400),
                status,
                List.of(line),
                0);
    }
}
