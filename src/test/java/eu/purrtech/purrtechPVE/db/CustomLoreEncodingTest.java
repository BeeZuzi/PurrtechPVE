package eu.purrtech.purrtechPVE.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomLoreEncodingTest {

    private static List<String> roundTrip(List<String> lore) {
        return ItemTemplateRepository.decodeLore(ItemTemplateRepository.encodeLore(lore));
    }

    @Test
    void noLoreStaysNoLore() {
        assertNull(ItemTemplateRepository.encodeLore(List.of()));
        assertEquals(List.of(), ItemTemplateRepository.decodeLore(null));
        assertEquals(List.of(), ItemTemplateRepository.decodeLore(""));
    }

    @Test
    void aLoneBlankLineSurvives() {
        assertEquals(List.of(""), roundTrip(List.of("")));
    }

    @Test
    void severalBlankLinesSurvive() {
        assertEquals(List.of("", ""), roundTrip(List.of("", "")));
        assertEquals(List.of("", "", ""), roundTrip(List.of("", "", "")));
    }

    @Test
    void blankLinesNextToTextSurvive() {
        assertEquals(List.of("", "<gray>text"), roundTrip(List.of("", "<gray>text")));
        assertEquals(List.of("<gray>text", ""), roundTrip(List.of("<gray>text", "")));
        assertEquals(List.of("a", "", "b"), roundTrip(List.of("a", "", "b")));
    }

    @Test
    void ordinaryLinesWithCommasSurvive() {
        assertEquals(List.of("a, b", "c"), roundTrip(List.of("a, b", "c")));
    }

    @Test
    void loreStoredBeforeTheFixStillReads() {
        // the plain newline join is exactly what the old code wrote for everything but a lone blank line
        assertEquals(List.of("a", "b"), ItemTemplateRepository.decodeLore("a\nb"));
        assertEquals(List.of("a", ""), ItemTemplateRepository.decodeLore("a\n"));
    }
}
