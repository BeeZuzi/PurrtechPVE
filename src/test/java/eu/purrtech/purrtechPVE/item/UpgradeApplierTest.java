package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UpgradeApplierTest {

    private static TemplateSnapshot snapshot(List<DamageContribution> damage, List<TypeModifier> resist, CriticalEffect crit) {
        return new TemplateSnapshot(UUID.randomUUID(), "sword", 3, "Sword", List.of(), List.of(), List.of(), Material.IRON_SWORD,
                null, null, damage, resist, List.of(), List.of(), null, crit, null, null, List.of(), 0L);
    }

    @Test
    void noUpgradesReturnsTheSameSnapshot() {
        TemplateSnapshot base = snapshot(List.of(), List.of(), null);
        assertSame(base, UpgradeApplier.apply(base, ItemUpgrades.NONE));
    }

    @Test
    void damageUpgradeAddsToAnExistingContributionOfTheSameTypeAndContext() {
        TemplateSnapshot base = snapshot(List.of(new DamageContribution("slashing", 10, DamageMode.FLAT, ModifierContext.WIELDED, true)),
                List.of(), null);
        ItemUpgrades up = new ItemUpgrades(Map.of(ItemUpgrades.damageKey("slashing", ModifierContext.WIELDED), 3.0), Map.of(), Map.of());

        List<DamageContribution> out = UpgradeApplier.apply(base, up).damageContributions();

        assertEquals(1, out.size());
        assertEquals(13.0, out.get(0).amount());
    }

    @Test
    void damageUpgradeOnATypeTheTemplateLacksAddsAVisibleFlatContribution() {
        TemplateSnapshot base = snapshot(List.of(), List.of(), null);
        ItemUpgrades up = new ItemUpgrades(Map.of(ItemUpgrades.damageKey("fire", ModifierContext.WORN), 4.0), Map.of(), Map.of());

        DamageContribution added = UpgradeApplier.apply(base, up).damageContributions().get(0);

        assertEquals("fire", added.damageTypeKey());
        assertEquals(4.0, added.amount());
        assertEquals(DamageMode.FLAT, added.mode());
        assertEquals(ModifierContext.WORN, added.context());
    }

    @Test
    void sameTypeInAnotherContextIsNotMerged() {
        TemplateSnapshot base = snapshot(List.of(new DamageContribution("fire", 5, DamageMode.FLAT, ModifierContext.WIELDED, true)),
                List.of(), null);
        ItemUpgrades up = new ItemUpgrades(Map.of(ItemUpgrades.damageKey("fire", ModifierContext.WORN), 2.0), Map.of(), Map.of());

        assertEquals(2, UpgradeApplier.apply(base, up).damageContributions().size());
    }

    @Test
    void resistUpgradeAddsToExistingAndCreatesMissing() {
        TemplateSnapshot base = snapshot(List.of(), List.of(new TypeModifier("fire", 15, true)), null);
        ItemUpgrades up = new ItemUpgrades(Map.of(), Map.of("fire", 5.0, "frozen", 8.0), Map.of());

        List<TypeModifier> out = UpgradeApplier.apply(base, up).typeModifiers();

        assertEquals(2, out.size());
        assertEquals(20.0, out.stream().filter(m -> m.damageTypeKey().equals("fire")).findFirst().orElseThrow().percent());
        assertEquals(8.0, out.stream().filter(m -> m.damageTypeKey().equals("frozen")).findFirst().orElseThrow().percent());
    }

    @Test
    void effectUpgradeBuildsAnEffectTheTemplateDoesNotHave() {
        TemplateSnapshot base = snapshot(List.of(), List.of(), null);
        ItemUpgrades up = new ItemUpgrades(Map.of(), Map.of(),
                Map.of(UpgradeEffect.CRIT_CHANCE.name(), 10.0, UpgradeEffect.CRIT_BONUS.name(), 50.0));

        CriticalEffect crit = UpgradeApplier.apply(base, up).criticalEffect();

        assertNotNull(crit);
        assertEquals(10.0, crit.chancePercent());
        assertEquals(50.0, crit.bonusDamagePercent());
    }

    @Test
    void effectUpgradeAddsToAnExistingEffectAndLeavesOthersAlone() {
        TemplateSnapshot base = snapshot(List.of(), List.of(), new CriticalEffect(20, 40, true));
        ItemUpgrades up = new ItemUpgrades(Map.of(), Map.of(), Map.of(UpgradeEffect.CRIT_CHANCE.name(), 5.0));

        TemplateSnapshot out = UpgradeApplier.apply(base, up);

        assertEquals(25.0, out.criticalEffect().chancePercent());
        assertEquals(40.0, out.criticalEffect().bonusDamagePercent());
        assertNull(out.bleedEffect());
    }
}
