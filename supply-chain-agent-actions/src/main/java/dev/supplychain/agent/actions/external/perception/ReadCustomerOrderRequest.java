package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.orders.CustomerOrderId;
import java.util.Objects;

public record ReadCustomerOrderRequest(CustomerOrderId orderId) {
    public ReadCustomerOrderRequest { orderId = Objects.requireNonNull(orderId, "orderId"); }
}
