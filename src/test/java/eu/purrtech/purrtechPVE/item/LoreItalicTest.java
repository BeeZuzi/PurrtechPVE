package eu.purrtech.purrtechPVE.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoreItalicTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static TextDecoration.State italicOf(Component component) {
        return component.decoration(TextDecoration.ITALIC);
    }

    @Test
    void customLoreIsNotItalicByDefault() {
        Component out = ItemRenderer.withDefaultItalic(new LoreLine("custom#0", MM.deserialize("<gray>flavour text")));
        assertEquals(TextDecoration.State.FALSE, italicOf(out));
    }

    @Test
    void customLoreKeepsItalicWhereTheTextAsksForIt() {
        Component out = ItemRenderer.withDefaultItalic(new LoreLine("custom#1", MM.deserialize("<i>whispered</i> and plain")));

        assertEquals(TextDecoration.State.FALSE, italicOf(out));
        assertEquals(TextDecoration.State.TRUE, italicOf(out.children().get(0)));
    }

    @Test
    void blankCustomLineIsNotItalic() {
        Component out = ItemRenderer.withDefaultItalic(new LoreLine("custom#2", MM.deserialize("")));
        assertEquals(TextDecoration.State.FALSE, italicOf(out));
    }

    @Test
    void generatedLinesAreItalic() {
        for (String key : new String[]{"header#damage", "damage#fire", "passive#fire", "resist#fire", "penetration#HEAVY",
                "bleed", "critical", "stun", "reflect", "armor-class", "attribute#GENERIC_ARMOR|chest"}) {
            Component out = ItemRenderer.withDefaultItalic(new LoreLine(key, MM.deserialize("<red>+5 Fire")));
            assertEquals(TextDecoration.State.TRUE, italicOf(out), key);
        }
    }

    @Test
    void anExplicitDecisionOnAGeneratedLineIsRespected() {
        Component out = ItemRenderer.withDefaultItalic(new LoreLine("damage#fire", Component.text("x").decoration(TextDecoration.ITALIC, false)));
        assertEquals(TextDecoration.State.FALSE, italicOf(out));
    }
}
