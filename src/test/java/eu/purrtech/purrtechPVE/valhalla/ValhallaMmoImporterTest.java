package eu.purrtech.purrtechPVE.valhalla;

import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures below are shaped exactly like ValhallaMMO's own {@code
 * ItemAttributesRegistry.serializeStats}/{@code getStats} format
 * (ATTRIBUTE:value:OPERATION:hidden;...), confirmed by reading that class's
 * source rather than guessed.
 */
class ValhallaMmoImporterTest {

    @Test
    void emptyOrBlankInputYieldsNothing() {
        var result = ValhallaMmoImporter.parse("");
        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertTrue(result.skipped().isEmpty());
        assertTrue(ValhallaMmoImporter.parse(null).contributions().isEmpty());
    }

    @Test
    void extraDamageAttributeBecomesFlatWieldedContribution() {
        var result = ValhallaMmoImporter.parse("EXTRA_FIRE_DAMAGE:4.0:ADD_NUMBER:false");

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
        var result = ValhallaMmoImporter.parse("FIRE_RESISTANCE:0.25:ADD_NUMBER:false");

        assertEquals(1, result.modifiers().size());
        TypeModifier m = result.modifiers().get(0);
        assertEquals("fire", m.damageTypeKey());
        assertEquals(25.0, m.percent());
    }

    @Test
    void multipleAttributesAllParsed() {
        var result = ValhallaMmoImporter.parse(
                "EXTRA_FIRE_DAMAGE:4.0:ADD_NUMBER:false;EXTRA_LIGHTNING_DAMAGE:2.5:ADD_NUMBER:false;FIRE_RESISTANCE:0.2:ADD_NUMBER:false");

        assertEquals(2, result.contributions().size());
        assertEquals(1, result.modifiers().size());
    }

    @Test
    void unmappedAttributesAreReportedAsSkippedNotDropped() {
        var result = ValhallaMmoImporter.parse("CRIT_CHANCE:0.15:ADD_NUMBER:false;LIFE_STEAL:0.1:ADD_NUMBER:false");

        assertTrue(result.contributions().isEmpty());
        assertTrue(result.modifiers().isEmpty());
        assertEquals(2, result.skipped().size());
        assertTrue(result.skipped().contains("CRIT_CHANCE"));
        assertTrue(result.skipped().contains("LIFE_STEAL"));
    }

    @Test
    void malformedEntriesAreIgnoredWithoutCrashing() {
        var result = ValhallaMmoImporter.parse("GARBAGE;EXTRA_FIRE_DAMAGE:not-a-number:ADD_NUMBER:false;;FIRE_RESISTANCE:0.1:ADD_NUMBER:false");

        assertTrue(result.contributions().isEmpty());
        assertEquals(1, result.modifiers().size());
    }

    @Test
    void hiddenAndOperationFieldsAreIgnoredButDoNotBreakParsing() {
        var result = ValhallaMmoImporter.parse("EXTRA_NECROTIC_DAMAGE:3:MULTIPLY_SCALAR_1:true");
        assertEquals(1, result.contributions().size());
        assertEquals("necrotic", result.contributions().get(0).damageTypeKey());
        assertEquals(3.0, result.contributions().get(0).amount());
    }
}
