package eu.purrtech.purrtechPVE.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColorTranslatorTest {

    @Test
    void spacedHexAndNamedColorsBecomeMiniMessageTags() {
        String input = "&fSoučástí &x&7&9&3&2&E&BD&x&7&E&3&A&E&Ca &f(3/12)";
        assertEquals("<white>Součástí <#7932eb>D<#7e3aec>a <white>(3/12)", LegacyColorTranslator.toMiniMessage(input));
    }

    @Test
    void renderedComponentKeepsHexColor() {
        Component component = MiniMessage.miniMessage().deserialize(
                LegacyColorTranslator.toMiniMessage("&x&7&9&3&2&E&BD"));
        assertEquals("D", PlainTextComponentSerializer.plainText().serialize(component));
        assertTrue(containsColor(component, TextColor.fromHexString("#7932eb")));
    }

    @Test
    void ampersandHashHexAndSectionSignWork() {
        assertEquals("<#abcdef>x<red>y", LegacyColorTranslator.toMiniMessage("&#ABCDEFx§cy"));
    }

    @Test
    void colorAfterFormatResetsLikeLegacy() {
        assertEquals("<bold>a<reset><red>b", LegacyColorTranslator.toMiniMessage("&la&cb"));
    }

    @Test
    void plainAmpersandAndMiniMessageAreLeftAlone() {
        assertEquals("Tom & Jerry <gold>zlato</gold>", LegacyColorTranslator.toMiniMessage("Tom & Jerry <gold>zlato</gold>"));
        assertEquals("&zfoo", LegacyColorTranslator.toMiniMessage("&zfoo"));
    }

    private static boolean containsColor(Component component, TextColor color) {
        if (color.equals(component.color())) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsColor(child, color)) {
                return true;
            }
        }
        return false;
    }
}
