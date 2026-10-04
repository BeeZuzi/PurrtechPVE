package eu.purrtech.purrtechPVE.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackStateCarrierTest {

    @Test
    void sameMaximumKeepsTheExactWear() {
        assertEquals(123, StackStateCarrier.scaledDamage(123, 250, 250));
    }

    @Test
    void changedMaximumKeepsTheWornFraction() {
        // half worn stays half worn when the template doubles the durability
        assertEquals(250, StackStateCarrier.scaledDamage(125, 250, 500));
        assertEquals(60, StackStateCarrier.scaledDamage(120, 500, 250));
    }

    @Test
    void wearNeverBreaksTheItem() {
        assertEquals(99, StackStateCarrier.scaledDamage(500, 100, 100));
        assertEquals(99, StackStateCarrier.scaledDamage(100, 100, 100));
    }

    @Test
    void unknownOldMaximumKeepsTheRawWear() {
        assertEquals(40, StackStateCarrier.scaledDamage(40, 0, 200));
    }

    @Test
    void negativeWearClampsToZero() {
        assertEquals(0, StackStateCarrier.scaledDamage(-3, 100, 100));
    }
}
