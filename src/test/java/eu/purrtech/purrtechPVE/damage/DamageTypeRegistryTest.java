package eu.purrtech.purrtechPVE.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageTypeRegistryTest {

    @Test
    void fallbackPhysicalTypeIsAlwaysRegistered() {
        DamageTypeRegistry registry = new DamageTypeRegistry();
        assertTrue(registry.find(DamageTypeRegistry.FALLBACK_PHYSICAL).isPresent());
    }

    @Test
    void seedIncludesRequestedCustomTypes() {
        DamageTypeRegistry registry = new DamageTypeRegistry();
        for (String key : new String[]{"frozen", "lightning", "bleed", "spirit", "radiant", "blunt", "piercing", "slashing"}) {
            assertTrue(registry.find(key).isPresent(), "missing seeded type: " + key);
        }
    }

    @Test
    void unknownKeyIsAbsent() {
        DamageTypeRegistry registry = new DamageTypeRegistry();
        assertFalse(registry.find("does-not-exist").isPresent());
    }

    @Test
    void bleedAndPoisonAreDotTypes() {
        DamageTypeRegistry registry = new DamageTypeRegistry();
        assertTrue(registry.find("bleed").orElseThrow().dot());
        assertTrue(registry.find("poison").orElseThrow().dot());
        assertFalse(registry.find("slashing").orElseThrow().dot());
    }
}
