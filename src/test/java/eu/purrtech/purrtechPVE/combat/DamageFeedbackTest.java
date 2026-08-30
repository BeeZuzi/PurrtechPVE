package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Component}/its serializers are a plain library, no live Bukkit
 * server needed - unlike ItemStack/ItemMeta rendering elsewhere in this
 * project, this one IS fully unit-testable.
 */
class DamageFeedbackTest {

    private static final DamageTypeRegistry REGISTRY = new DamageTypeRegistry();

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void rendersIconAndAmountForEachType() {
        Map<String, Double> perType = new LinkedHashMap<>();
        perType.put("fire", 4.0);

        Component result = DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED);

        String icon = REGISTRY.find("fire").orElseThrow().icon();
        assertEquals(icon + " 4", plain(result));
    }

    @Test
    void multipleTypesAreSeparatedAndOrderPreserved() {
        Map<String, Double> perType = new LinkedHashMap<>();
        perType.put("slashing", 3.0);
        perType.put("fire", 4.5);

        String text = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED));

        String slashingIcon = REGISTRY.find("slashing").orElseThrow().icon();
        String fireIcon = REGISTRY.find("fire").orElseThrow().icon();
        assertEquals(slashingIcon + " 3  " + fireIcon + " 4.5", text);
    }

    @Test
    void unknownDamageTypeKeyFallsBackToQuestionMark() {
        Map<String, Double> perType = Map.of("not-a-real-type", 2.0);
        String text = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED));
        assertTrue(text.startsWith("? "));
    }

    @Test
    void zeroOrNegativeAmountsAreOmitted() {
        Map<String, Double> perType = new LinkedHashMap<>();
        perType.put("fire", 0.0);
        perType.put("frozen", -1.0);
        perType.put("lightning", 2.0);

        String text = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED));

        String lightningIcon = REGISTRY.find("lightning").orElseThrow().icon();
        assertEquals(lightningIcon + " 2", text);
    }

    @Test
    void emptyBreakdownRendersEmptyComponent() {
        assertEquals("", plain(DamageFeedback.render(Map.of(), REGISTRY, NamedTextColor.RED)));
    }

    @Test
    void criticalFlagPrependsAMarker() {
        Map<String, Double> perType = Map.of("fire", 4.0);
        String text = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, true));

        String fireIcon = REGISTRY.find("fire").orElseThrow().icon();
        assertEquals("CRIT! " + fireIcon + " 4", text);
    }

    @Test
    void nonCriticalFourArgOverloadMatchesThreeArgOverload() {
        Map<String, Double> perType = Map.of("fire", 4.0);
        String withFlag = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, false));
        String withoutFlag = plain(DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED));
        assertEquals(withoutFlag, withFlag);
    }

    @Test
    void effectivenessColorsOffKeepsTheFlatColor() {
        Map<String, Double> perType = Map.of("fire", 4.0);
        Map<String, Double> resistance = Map.of("fire", -50.0);

        Component result = DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, false, resistance, false);

        assertEquals(NamedTextColor.RED, soleChildColor(result));
    }

    @Test
    void effectivenessColorsOnColorsAWeaknessYellow() {
        Map<String, Double> perType = Map.of("fire", 4.0);
        Map<String, Double> resistance = Map.of("fire", -50.0);

        Component result = DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, false, resistance, true);

        assertEquals(NamedTextColor.YELLOW, soleChildColor(result));
    }

    @Test
    void effectivenessColorsOnColorsAResistanceGray() {
        Map<String, Double> perType = Map.of("fire", 4.0);
        Map<String, Double> resistance = Map.of("fire", 25.0);

        Component result = DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, false, resistance, true);

        assertEquals(NamedTextColor.GRAY, soleChildColor(result));
    }

    @Test
    void effectivenessColorsOnColorsNormalWhite() {
        Map<String, Double> perType = Map.of("fire", 4.0);

        Component result = DamageFeedback.render(perType, REGISTRY, NamedTextColor.RED, false, Map.of(), true);

        assertEquals(NamedTextColor.WHITE, soleChildColor(result));
    }

    @Test
    void formatAmountRoundsToOneDecimalPlace() {
        assertEquals("5", DamageFeedback.formatAmount(5.0));
        assertEquals("2.5", DamageFeedback.formatAmount(2.5));
        assertEquals("2.5", DamageFeedback.formatAmount(2.5049));
    }

    private static NamedTextColor soleChildColor(Component result) {
        assertEquals(1, result.children().size());
        return (NamedTextColor) result.children().get(0).color();
    }
}
