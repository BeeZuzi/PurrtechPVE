package eu.purrtech.purrtechPVE.item;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One individually-reorderable line of a rendered item's final lore - a section header, a single
 * stat entry, or one line of the admin's free-form {@code customLore} - identified by a stable
 * {@code key} so {@link eu.purrtech.purrtechPVE.gui.LoreOrderMenu} can remember its position
 * across renders even as the underlying value (amount, color, ...) changes. This replaces an
 * earlier fixed-8-block model: an admin asked to interleave a custom line between two stat lines,
 * which a whole-category block can never allow no matter how the block itself is positioned.
 *
 * <p>Key scheme (stable as long as the underlying entry isn't removed): {@code custom#<index>}
 * for a {@code customLore} line (index into that list - see the caveat below), {@code
 * header#<category>} for one of the 5 auto-generated section headers (see {@link LoreHeader}),
 * {@code damage#<type>}/{@code passive#<type>} for a wielded/worn damage contribution, {@code
 * resist#<type>} for a type modifier, {@code penetration#<armorClass>}, {@code bleed}, {@code
 * critical}, and {@code attribute#<attribute>|<slot>} for one attribute modifier entry.
 *
 * <p><b>Custom-line caveat:</b> since {@code customLore} is a plain {@code List<String>} replaced
 * wholesale by {@code /pve item lore set} (it has no per-line identity of its own), a custom
 * line's identity is just its index. Retyping the whole custom lore therefore doesn't carry old
 * lines' positions over by content - the new lines simply show up as new (see {@link
 * #canonicalize}) and need repositioning, same as any other freshly-added line.
 *
 * <p>An empty category (e.g. no damage types configured) contributes zero candidate lines - there
 * is nothing to preview or pre-position, unlike the old per-block model. The moment an entry is
 * added it appears as a new line at the end of the order, ready to be moved into place.
 */
public record LoreLine(String key, Component component) {

    /**
     * Orders {@code candidates} (generated in a fixed "natural" default sequence - custom lines,
     * then damage/passive/resist/penetration/bleed/critical/attributes - only relevant for
     * brand-new lines below) by {@code storedOrder}'s key sequence: a stored key whose candidate no
     * longer exists is silently dropped (whatever it pointed at was removed/renamed), and a
     * candidate not yet in {@code storedOrder} (freshly added, or a template predating ordering
     * entirely) is appended at the end in its natural order - ready for the admin to move it into
     * place, never silently guessed into a "correct" position.
     */
    public static List<LoreLine> canonicalize(List<String> storedOrder, List<LoreLine> candidates) {
        Map<String, LoreLine> byKey = new LinkedHashMap<>();
        for (LoreLine candidate : candidates) {
            byKey.put(candidate.key(), candidate);
        }
        List<LoreLine> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String key : storedOrder) {
            LoreLine candidate = byKey.get(key);
            if (candidate != null && seen.add(key)) {
                result.add(candidate);
            }
        }
        for (LoreLine candidate : candidates) {
            if (seen.add(candidate.key())) {
                result.add(candidate);
            }
        }
        return result;
    }
}
