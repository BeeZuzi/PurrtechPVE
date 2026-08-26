package eu.purrtech.purrtechPVE.damage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamagePipelineTest {

    private static final double DELTA = 1e-9;

    @Test
    void noSplitAndNoResistPassesDamageThrough() {
        double result = DamagePipeline.apply(10.0, Map.of(), Map.of());
        assertEquals(10.0, result, DELTA);
    }

    @Test
    void noSplitFallsBackToPhysicalBucket() {
        double result = DamagePipeline.apply(10.0, null, Map.of(DamageTypeRegistry.FALLBACK_PHYSICAL, 50.0));
        assertEquals(5.0, result, DELTA);
    }

    @Test
    void splitAcrossTypesEachResistedIndependently() {
        // 6 slashing at 50% resist, 4 fire at 0% resist
        Map<String, Double> typed = Map.of("slashing", 6.0, "fire", 4.0);
        Map<String, Double> resist = Map.of("slashing", 50.0);

        double result = DamagePipeline.apply(10.0, typed, resist);

        // slashing bucket: 6.0 * (1 - 0.5) = 3.0 ; fire bucket: 4.0 * 1 = 4.0
        assertEquals(7.0, result, DELTA);
    }

    @Test
    void bonusTypedDamageOnTopOfBaseIsAddedNotReplaced() {
        // e.g. a weapon's full 10 physical damage plus a trinket's flat +4 fire bonus on top
        Map<String, Double> typed = Map.of(DamageTypeRegistry.FALLBACK_PHYSICAL, 10.0, "fire", 4.0);

        double result = DamagePipeline.apply(10.0, typed, Map.of());

        assertEquals(14.0, result, DELTA);
    }

    @Test
    void negativePercentIsWeaknessAndAmplifiesDamage() {
        Map<String, Double> typed = Map.of("frozen", 10.0);
        Map<String, Double> resist = Map.of("frozen", -50.0);

        double result = DamagePipeline.apply(10.0, typed, resist);

        assertEquals(15.0, result, DELTA);
    }

    @Test
    void resistIsClampedToConfiguredMaximum() {
        Map<String, Double> typed = Map.of("fire", 10.0);
        Map<String, Double> resist = Map.of("fire", 500.0);

        double result = DamagePipeline.apply(10.0, typed, resist);

        assertEquals(10.0 * (1 - DamagePipeline.MAX_RESIST_PERCENT / 100.0), result, DELTA);
    }

    @Test
    void weaknessIsClampedToConfiguredMinimum() {
        Map<String, Double> typed = Map.of("fire", 10.0);
        Map<String, Double> resist = Map.of("fire", -5000.0);

        double result = DamagePipeline.apply(10.0, typed, resist);

        assertEquals(10.0 * (1 - DamagePipeline.MIN_RESIST_PERCENT / 100.0), result, DELTA);
    }

    @Test
    void resultNeverGoesBelowZeroEvenAtMaxResist() {
        // resist is clamped to MAX_RESIST_PERCENT (95%), so 5% of the bucket always remains -
        // this asserts the pipeline's floor guard doesn't accidentally clip that remainder to 0.
        Map<String, Double> typed = Map.of("fire", 10.0);
        Map<String, Double> resist = Map.of("fire", 100.0);

        double result = DamagePipeline.apply(10.0, typed, resist);

        assertEquals(10.0 * (1 - DamagePipeline.MAX_RESIST_PERCENT / 100.0), result, DELTA);
    }

    @Test
    void nonPositiveRawDamageIsReturnedUnchanged() {
        assertEquals(0.0, DamagePipeline.apply(0.0, Map.of("fire", 1.0), Map.of("fire", -100.0)), DELTA);
        assertEquals(-5.0, DamagePipeline.apply(-5.0, Map.of("fire", 1.0), Map.of("fire", -100.0)), DELTA);
    }
}
