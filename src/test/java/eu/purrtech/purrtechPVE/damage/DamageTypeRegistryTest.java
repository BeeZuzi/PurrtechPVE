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

    @Test
    void everySeededTypeHasADistinctNonBlankIcon() {
        DamageTypeRegistry registry = new DamageTypeRegistry();
        java.util.Map<String, DamageType> all = registry.all();
        java.util.Set<String> icons = new java.util.HashSet<>();
        for (DamageType type : all.values()) {
            assertFalse(type.icon() == null || type.icon().isBlank(), "missing icon for " + type.key());
            assertTrue(icons.add(type.icon()), "duplicate icon '" + type.icon() + "' reused for " + type.key());
        }
    }
}
