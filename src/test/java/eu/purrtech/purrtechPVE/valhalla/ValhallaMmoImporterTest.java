package eu.purrtech.purrtechPVE.valhalla;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures below are shaped exactly like ValhallaMMO's own {@code
 * ItemAttributesRegistry.serializeStats}/{@code getStats} format
 * (ATTRIBUTE:value:OPERATION:hidden;...), confirmed by reading that class's
 * source rather than guessed.
 */
class ValhallaMmoImporterTest {

    private static final Set<String> DAMAGE_TYPE_KEYS = new DamageTypeRegistry().all().keySet();

    @Test
    void emptyOrBlankInputYieldsNothing() {
        var result = ValhallaMmoImporter.parse("", DAMAGE_TYPE_KEYS);
        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertTrue(ValhallaMmoImporter.parse(null, DAMAGE_TYPE_KEYS).contributions().isEmpty());
    }

    @Test
    void extraDamageAttributeBecomesFlatWieldedContribution() {
        var result = ValhallaMmoImporter.parse("EXTRA_FIRE_DAMAGE:4.0:ADD_NUMBER:false", DAMAGE_TYPE_KEYS);

        assertEquals(1, result.contributions().size());
        DamageContribution c = result.contributions().get(0);
        assertEquals("fire", c.damageTypeKey());
        assertEquals(4.0, c.amount());
        assertEquals(DamageMode.FLAT, c.mode());
        assertEquals(ModifierContext.WIELDED, c.context());
        assertTrue(result.modifiers().isEmpty());
    }

    @Test
    void resistanceAttributeBecomesTypeModifierScaledToPercent() {
        var result = ValhallaMmoImporter.parse("FIRE_RESISTANCE:0.25:ADD_NUMBER:false", DAMAGE_TYPE_KEYS);

        assertEquals(1, result.modifiers().size());
        TypeModifier m = result.modifiers().get(0);
        assertEquals("fire", m.damageTypeKey());
        assertEquals(25.0, m.percent());
    }

    @Test
    void multipleAttributesAllParsed() {
        var result = ValhallaMmoImporter.parse(
                "EXTRA_FIRE_DAMAGE:4.0:ADD_NUMBER:false;EXTRA_LIGHTNING_DAMAGE:2.5:ADD_NUMBER:false;FIRE_RESISTANCE:0.2:ADD_NUMBER:false",
                DAMAGE_TYPE_KEYS);

        assertEquals(2, result.contributions().size());
        assertEquals(1, result.modifiers().size());
    }

    @Test
    void unmappedAttributesAreReportedAsSkippedNotDropped() {
        var result = ValhallaMmoImporter.parse("CRIT_CHANCE:0.15:ADD_NUMBER:false;LIFE_STEAL:0.1:ADD_NUMBER:false", DAMAGE_TYPE_KEYS);

        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertEquals(2, result.skipped().size());
        assertTrue(result.skipped().contains("CRIT_CHANCE"));
        assertTrue(result.skipped().contains("LIFE_STEAL"));
    }

    @Test
    void malformedEntriesAreIgnoredWithoutCrashing() {
        var result = ValhallaMmoImporter.parse(
                "GARBAGE;EXTRA_FIRE_DAMAGE:not-a-number:ADD_NUMBER:false;;FIRE_RESISTANCE:0.1:ADD_NUMBER:false", DAMAGE_TYPE_KEYS);

        assertTrue(result.contributions().isEmpty());
        assertEquals(1, result.modifiers().size());
    }

    @Test
    void hiddenAndOperationFieldsAreIgnoredButDoNotBreakParsing() {
        var result = ValhallaMmoImporter.parse("EXTRA_NECROTIC_DAMAGE:3:MULTIPLY_SCALAR_1:true", DAMAGE_TYPE_KEYS);
        assertEquals(1, result.contributions().size());
        assertEquals("necrotic", result.contributions().get(0).damageTypeKey());
        assertEquals(3.0, result.contributions().get(0).amount());
    }

    // fromAttributes() is the entry point ValhallaMmoBulkImporter feeds its already-decoded
    // items.json attribute/value pairs through - same mapping tables as parse(), just skipping
    // the "ATTR:value:OP:hidden" string format entirely.

    @Test
    void fromAttributesMapsDamageAndResistanceAndReportsUnmapped() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "EXTRA_FIRE_DAMAGE", 6.0,
                "FREEZING_RESISTANCE", 0.25,
                "CRIT_DAMAGE", 10.0), DAMAGE_TYPE_KEYS);

        assertEquals(1, result.contributions().size());
        assertEquals("fire", result.contributions().get(0).damageTypeKey());
        assertEquals(6.0, result.contributions().get(0).amount());

        assertEquals(1, result.modifiers().size());
        assertEquals("frozen", result.modifiers().get(0).damageTypeKey());
        assertEquals(25.0, result.modifiers().get(0).percent());

        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().contains("CRIT_DAMAGE"));
    }

    @Test
    void fromAttributesOnEmptyMapYieldsNothing() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of(), DAMAGE_TYPE_KEYS);
        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    // The newer attribute families below (bleed/arrow/all flats, the extra resistance keys, the
    // DAMAGE_RESISTANCE fan-out, and the DAMAGE_<TYPE> "% of what it already deals" multipliers)
    // were added on top of the original mapping above - see ValhallaMmoImporter's class javadoc
    // for exactly which ValhallaMMO StatFormat backs each family and why.

    @Test
    void arrowAndAllDamageAreFlatNotPercent() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "ARROW_DAMAGE", 1.5,
                "DAMAGE_ALL", 3.0), DAMAGE_TYPE_KEYS);

        assertEquals(2, result.contributions().size());
        assertEquals(1.5, findContribution(result, "piercing").amount());
        assertEquals(3.0, findContribution(result, "physical").amount());
    }

    /** "bleed" isn't a valid DamageContribution type any more (see BleedEffect's javadoc) - BLEED_DAMAGE comes back as its own field instead of folding into the normal contribution list. */
    @Test
    void bleedDamageComesBackAsItsOwnFieldNotAContribution() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of("BLEED_DAMAGE", 2.5), DAMAGE_TYPE_KEYS);

        assertTrue(result.contributions().isEmpty());
        assertEquals(2.5, result.bleedDamageAmount());
    }

    @Test
    void noBleedDamageAttributeLeavesBleedDamageAmountNull() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of("ARROW_DAMAGE", 1.5), DAMAGE_TYPE_KEYS);

        assertEquals(null, result.bleedDamageAmount());
    }

    @Test
    void newResistanceAttributesMapToTheirTypes() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "BLEED_RESISTANCE", 0.1,
                "BLUDGEONING_RESISTANCE", 0.2,
                "PROJECTILE_RESISTANCE", 0.3), DAMAGE_TYPE_KEYS);

        assertEquals(3, result.modifiers().size());
        assertEquals(10.0, findModifier(result, "bleed").percent());
        assertEquals(20.0, findModifier(result, "blunt").percent());
        assertEquals(30.0, findModifier(result, "piercing").percent());
    }

    @Test
    void damageResistanceFansOutToEveryKnownDamageType() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of("DAMAGE_RESISTANCE", 0.1), DAMAGE_TYPE_KEYS);

        assertEquals(DAMAGE_TYPE_KEYS.size(), result.modifiers().size());
        assertTrue(result.modifiers().stream().allMatch(m -> m.percent() == 10.0));
        assertEquals(DAMAGE_TYPE_KEYS, result.modifiers().stream().map(TypeModifier::damageTypeKey)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void damagePercentMultiplierScalesTheMatchingFlatContribution() {
        // 4 base fire damage, +50% from DAMAGE_FIRE -> 6.0
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "EXTRA_FIRE_DAMAGE", 4.0,
                "DAMAGE_FIRE", 0.5), DAMAGE_TYPE_KEYS);

        assertEquals(1, result.contributions().size());
        assertEquals(6.0, findContribution(result, "fire").amount());
    }

    @Test
    void damagePercentMultiplierWithNoMatchingFlatContributionBecomesPercentOfTotal() {
        // the item deals no fire damage to begin with - ValhallaMMO's own multiplier has
        // nothing to multiply, so per updated spec it's imported directly as our own
        // PERCENT_OF_TOTAL contribution instead of being dropped.
        var result = ValhallaMmoImporter.fromAttributes(Map.of("DAMAGE_FIRE", 0.5), DAMAGE_TYPE_KEYS);

        assertEquals(1, result.contributions().size());
        DamageContribution c = findContribution(result, "fire");
        assertEquals(50.0, c.amount());
        assertEquals(DamageMode.PERCENT_OF_TOTAL, c.mode());
        assertEquals(ModifierContext.WIELDED, c.context());
    }

    @Test
    void damagePercentMultiplierMixOfMatchedAndUnmatchedTypesInOneImport() {
        // fire has a flat base to scale, lightning doesn't - each should be handled independently.
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "EXTRA_FIRE_DAMAGE", 4.0,
                "DAMAGE_FIRE", 0.5,
                "DAMAGE_LIGHTNING", 0.2), DAMAGE_TYPE_KEYS);

        assertEquals(2, result.contributions().size());
        DamageContribution fire = findContribution(result, "fire");
        assertEquals(6.0, fire.amount());
        assertEquals(DamageMode.FLAT, fire.mode());
        DamageContribution lightning = findContribution(result, "lightning");
        assertEquals(20.0, lightning.amount());
        assertEquals(DamageMode.PERCENT_OF_TOTAL, lightning.mode());
    }

    @Test
    void meleeCritPlayerAndVelocityStatsAreNotDamageTypeKeyedSoTheyStaySkipped() {
        var result = ValhallaMmoImporter.fromAttributes(Map.of(
                "DAMAGE_MELEE", 0.1,
                "CRIT_DAMAGE", 0.5,
                "DAMAGE_PLAYER", 0.2,
                "VELOCITY_DAMAGE", 1.0), DAMAGE_TYPE_KEYS);

        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertEquals(4, result.skipped().size());
    }

    private static DamageContribution findContribution(ValhallaMmoImporter.ImportResult result, String typeKey) {
        return result.contributions().stream().filter(c -> c.damageTypeKey().equals(typeKey)).findFirst().orElseThrow();
    }

    private static TypeModifier findModifier(ValhallaMmoImporter.ImportResult result, String typeKey) {
        return result.modifiers().stream().filter(m -> m.damageTypeKey().equals(typeKey)).findFirst().orElseThrow();
    }
}
