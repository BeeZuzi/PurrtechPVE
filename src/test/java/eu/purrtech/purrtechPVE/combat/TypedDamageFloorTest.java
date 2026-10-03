package eu.purrtech.purrtechPVE.combat;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedDamageFloorTest {

    private static final double DELTA = 1e-9;

    @Test
    void aPenaltyShrinksItsOwnTypeWithoutTouchingTheOthers() {
        // stone-sword-based mace: 5 base slash, -80% slash (= -4), 9.5 flat blunt
        Map<String, Double> typed = new HashMap<>();
        typed.merge("slashing", 5.0, Double::sum);
        typed.merge("slashing", 5.0 * -80 / 100.0, Double::sum);
        typed.merge("blunt", 9.5, Double::sum);

        Map<String, Double> result = EquipmentResolver.nonNegative(typed);

        assertEquals(1.0, result.get("slashing"), DELTA);
        assertEquals(9.5, result.get("blunt"), DELTA);
    }

    @Test
    void aTypeNeverGoesBelowZero() {
        Map<String, Double> typed = new HashMap<>();
        typed.put("slashing", 5.0 - 7.0);
        typed.put("blunt", 9.5);

        Map<String, Double> result = EquipmentResolver.nonNegative(typed);

        assertEquals(0.0, result.get("slashing"), DELTA);
        assertEquals(9.5, result.get("blunt"), DELTA);
    }
}
