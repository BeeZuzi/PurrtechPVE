package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldToggleEvaluatorTest {

    @Test
    void activeByDefault() {
        WorldToggleSettings settings = WorldToggleSettings.defaults();
        assertTrue(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVP));
        assertTrue(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVE));
    }

    @Test
    void globallyDisabledWorldWinsOverEverything() {
        WorldToggleSettings settings = new WorldToggleSettings(Set.of("creative"), true, Set.of(), true, Set.of());
        assertFalse(WorldToggleEvaluator.isActive(settings, "creative", CombatKind.PVP));
        assertFalse(WorldToggleEvaluator.isActive(settings, "creative", CombatKind.PVE));
        assertTrue(WorldToggleEvaluator.isActive(settings, "survival", CombatKind.PVP));
    }

    @Test
    void pvpDisabledGloballyBlocksOnlyPvp() {
        WorldToggleSettings settings = new WorldToggleSettings(Set.of(), false, Set.of(), true, Set.of());
        assertFalse(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVP));
        assertTrue(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVE));
    }

    @Test
    void pveDisabledGloballyBlocksOnlyPve() {
        WorldToggleSettings settings = new WorldToggleSettings(Set.of(), true, Set.of(), false, Set.of());
        assertTrue(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVP));
        assertFalse(WorldToggleEvaluator.isActive(settings, "world", CombatKind.PVE));
    }

    @Test
    void perWorldPvpOverrideBlocksJustThatWorld() {
        WorldToggleSettings settings = new WorldToggleSettings(Set.of(), true, Set.of("arena"), true, Set.of());
        assertFalse(WorldToggleEvaluator.isActive(settings, "arena", CombatKind.PVP));
        assertTrue(WorldToggleEvaluator.isActive(settings, "arena", CombatKind.PVE));
        assertTrue(WorldToggleEvaluator.isActive(settings, "other", CombatKind.PVP));
    }

    @Test
    void perWorldPveOverrideBlocksJustThatWorld() {
        WorldToggleSettings settings = new WorldToggleSettings(Set.of(), true, Set.of(), true, Set.of("spawn"));
        assertFalse(WorldToggleEvaluator.isActive(settings, "spawn", CombatKind.PVE));
        assertTrue(WorldToggleEvaluator.isActive(settings, "spawn", CombatKind.PVP));
    }
}
