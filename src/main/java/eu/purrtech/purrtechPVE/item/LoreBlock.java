package eu.purrtech.purrtechPVE.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The 8 fixed sections {@link ItemRenderer#buildLore} can place in a template's rendered lore -
 * {@link ItemTemplate#loreOrder()} lists these keys in whatever order the admin has arranged them
 * (left-to-right in {@code LoreOrderMenu} = top-to-bottom in the final lore), independent of
 * {@link LoreHeader} (which only controls a header's visibility, not its position) and each
 * entry's own {@code visible} flag (which controls just that one line).
 *
 * <p>Each of the 7 stat blocks (everything but {@link #CUSTOM}) is ONE reorderable unit
 * regardless of how many lines it currently renders (zero included, e.g. no damage types
 * configured yet) - a weapon with 3 configured damage types doesn't get 3 separate slots to
 * juggle, and its position stays meaningful even before any type is added. {@link #CUSTOM} is
 * the one exception: the admin's own {@code customLore} lines move as a single block too (not
 * individually reorderable relative to the stat blocks) - a deliberate scope call, since
 * individually reordering free-form text lines that can be added/removed/replaced wholesale via
 * {@code /pve item lore set} would need those lines to carry a stable identity of their own,
 * which they don't.
 */
public enum LoreBlock {
    CUSTOM("custom"),
    DAMAGE("damage"),
    PASSIVE("passive"),
    RESIST("resist"),
    PENETRATION("penetration"),
    BLEED("bleed"),
    CRITICAL("critical"),
    ATTRIBUTES("attributes");

    private final String key;

    LoreBlock(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static LoreBlock fromKey(String key) {
        for (LoreBlock block : values()) {
            if (block.key.equals(key)) {
                return block;
            }
        }
        throw new IllegalArgumentException("Unknown lore block key: " + key);
    }

    /** {@code CUSTOM, DAMAGE, PASSIVE, RESIST, PENETRATION, BLEED, CRITICAL, ATTRIBUTES} - the order a brand-new template (or one whose stored order is empty/incomplete) renders in, matching {@code ItemRenderer}'s original hardcoded sequence exactly. */
    public static List<String> defaultOrderKeys() {
        List<String> keys = new ArrayList<>();
        for (LoreBlock block : values()) {
            keys.add(block.key);
        }
        return keys;
    }

    /**
     * {@code stored} filtered to recognized keys and de-duplicated, with any block missing from
     * it appended afterward in default order - so a template predating this feature (empty
     * {@code loreOrder}), or one whose stored list is missing a block for any other reason,
     * still renders every block exactly once instead of silently dropping it.
     */
    public static List<LoreBlock> canonicalize(List<String> stored) {
        List<LoreBlock> result = new ArrayList<>();
        Set<LoreBlock> seen = new LinkedHashSet<>();
        for (String key : stored) {
            LoreBlock block;
            try {
                block = fromKey(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (seen.add(block)) {
                result.add(block);
            }
        }
        for (LoreBlock block : values()) {
            if (seen.add(block)) {
                result.add(block);
            }
        }
        return result;
    }
}
