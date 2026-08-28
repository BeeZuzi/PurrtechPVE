package eu.purrtech.purrtechPVE.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributeSlotsTest {

    private static final List<String> TRINKET_SLOTS = List.of("RING_1", "RING_2", "AMULET", "BELT");

    @Test
    void vanillaSlotGroupNameIsCaseInsensitiveAndCanonicalIsLowercase() {
        assertEquals("mainhand", AttributeSlots.parse("MAINHAND", TRINKET_SLOTS));
        assertEquals("mainhand", AttributeSlots.parse("mainhand", TRINKET_SLOTS));
        assertEquals("head", AttributeSlots.parse("Head", TRINKET_SLOTS));
        assertEquals("any", AttributeSlots.parse("ANY", TRINKET_SLOTS));
    }

    @Test
    void trinketSlotNameIsCaseInsensitiveAndCanonicalIsUppercase() {
        assertEquals("AMULET", AttributeSlots.parse("amulet", TRINKET_SLOTS));
        assertEquals("RING_1", AttributeSlots.parse("ring_1", TRINKET_SLOTS));
    }

    @Test
    void unknownSlotIsNull() {
        assertNull(AttributeSlots.parse("not-a-slot", TRINKET_SLOTS));
        assertNull(AttributeSlots.parse("", TRINKET_SLOTS));
        assertNull(AttributeSlots.parse(null, TRINKET_SLOTS));
    }

    @Test
    void trinketSlotNotConfiguredOnThisServerIsUnknown() {
        assertNull(AttributeSlots.parse("NECKLACE", TRINKET_SLOTS));
    }
}
