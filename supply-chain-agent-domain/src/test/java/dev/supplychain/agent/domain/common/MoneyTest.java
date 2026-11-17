package dev.supplychain.agent.domain.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void moneyArithmeticIsCurrencySafe() {
        var total = Money.of("12.50", "CAD").plus(Money.of("2.50", "CAD"));
        assertEquals(Money.of("15", "CAD"), total);
        assertEquals(Money.of("25", "CAD"), Money.of("12.50", "CAD").multipliedBy(new java.math.BigDecimal("2")));
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("1", "CAD").plus(Money.of("1", "USD")));
    }
}
