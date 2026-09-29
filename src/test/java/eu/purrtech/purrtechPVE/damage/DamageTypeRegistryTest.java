package eu.purrtech.purrtechPVE.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void physicalExpandsToAllThreeSubtypesAndOtherKeysToThemselves() {
        assertEquals(java.util.List.of("blunt", "piercing", "slashing"), DamageTypeRegistry.expandPhysical("physical"));
        assertEquals(java.util.List.of("fire"), DamageTypeRegistry.expandPhysical("fire"));
        DamageTypeRegistry registry = new DamageTypeRegistry();
        for (String subtype : DamageTypeRegistry.PHYSICAL_SUBTYPES) {
            assertTrue(registry.find(subtype).isPresent(), "subtype not registered: " + subtype);
            assertTrue(DamageTypeRegistry.PHYSICAL_TYPES.contains(subtype));
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
