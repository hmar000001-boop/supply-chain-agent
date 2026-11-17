package dev.supplychain.agent.domain.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantityTest {
    @Test
    void arithmeticPreservesUnitAndNormalizesScale() {
        var quantity = Quantity.of("10.00", UnitOfMeasure.EACH);
        assertEquals(Quantity.of("12", UnitOfMeasure.EACH), quantity.plus(Quantity.of("2.0", UnitOfMeasure.EACH)));
        assertEquals(Quantity.of("7", UnitOfMeasure.EACH), quantity.minus(Quantity.of("3", UnitOfMeasure.EACH)));
    }

    @Test
    void arithmeticRejectsUnitMismatchAndUnderflow() {
        var each = Quantity.of("2", UnitOfMeasure.EACH);
        assertThrows(IllegalArgumentException.class,
                () -> each.plus(Quantity.of("1", UnitOfMeasure.KILOGRAM)));
        assertThrows(IllegalArgumentException.class,
                () -> each.minus(Quantity.of("3", UnitOfMeasure.EACH)));
    }

    @Test
    void quantitiesCannotBeNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> Quantity.of("-0.1", UnitOfMeasure.EACH));
    }
}
