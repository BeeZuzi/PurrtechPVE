package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * An admin-defined item blueprint. The base is a vanilla {@link Material} +
 * optional custom model data, plus (activating what this field's own javadoc
 * used to call "a later phase") {@code baseItemSnapshot}: a serialized
 * {@link org.bukkit.persistence.PersistentDataContainer} captured from
 * whatever real item was in hand when this template was created/rebased
 * (import, {@code /pve item setbase}/{@code replace}, or the BASE tab's
 * rebase click) - see {@code BaseItemSnapshots} for exactly what's kept
 * (everything except ValhallaMMO's own two stat keys and this plugin's own
 * stamp keys) and why (third-party plugins - ItemsAdder chief among them -
 * key their own custom armor/item rendering off a {@code custom_data} PDC
 * entry that our own from-scratch render would otherwise silently drop,
 * which is what caused a custom-model helmet to show no texture at all once
 * worn even though its in-hand/inventory model was fine).
 *
 * <p>{@code version} bumps on every saved change; {@code syncedVersion} is
 * the version that has actually been pushed out to circulating items
 * ({@code /pve item sync}). They diverge whenever an edit was made but the
 * admin chose not to propagate it - existing stacks stay pinned at
 * {@code syncedVersion} (or older) until a later sync catches them up,
 * while freshly given items always render from the live {@code version}.
 *
 * <p>{@code armorClass} is {@code null} for anything that isn't armor -
 * like {@code allowedSlots}/{@code trinket}, it's a live classification, not
 * a pinned stat: changing it doesn't bump {@code version}, and the actual
 * resistance/weakness it grants lives separately in {@code
 * armor_class_profile} (see {@code ArmorClassProfileRepository}) and applies
 * immediately to every piece of that class, already-issued ones included.
 *
 * <p>{@code customLore} is extra, admin-authored lore shown above whatever
 * stat lines get auto-generated - each line is a raw MiniMessage string (see
 * {@code ItemRenderer}), same as {@code displayName} itself, which is also
 * parsed as MiniMessage rather than shown as literal text. On import, it's
 * seeded from the source item's own lore (serialized back to MiniMessage),
 * so an imported item's original flavor text isn't just silently dropped in
 * favor of this plugin's own stat lore - the admin can still edit/clear it
 * afterwards like any other stat.
 *
 * <p>{@code hiddenHeaders} lists which of the 5 auto-generated section
 * headers (see {@link LoreHeader}) are suppressed for this template - a
 * header stays hidden even if its category still has visible stat lines
 * under it (see {@code ItemRenderer.buildLore}). Independent of each
 * individual stat entry's own {@code visible} flag (e.g. {@code
 * DamageContribution.visible()}), which hides just that one line.
 *
 * <p>{@code loreOrder} lists {@link LoreLine} keys in the order the admin has
 * arranged them (see {@code LoreOrderMenu}) - individual lore lines (a
 * section header, one stat entry, one custom-lore line, ...), not whole
 * categories, so a custom line can be interleaved between two stat lines.
 * Independent of both {@code hiddenHeaders} (visibility, not position) and
 * each entry's own {@code visible} flag. See {@link LoreLine#canonicalize}
 * for how a template whose stored order is empty, stale, or missing a line
 * (anything predating this field, or added since it was last saved) still
 * renders every current line, appending anything not yet positioned.
 */
public record ItemTemplate(
        UUID id,
        String key,
        String displayName,
        List<String> customLore,
        List<String> hiddenHeaders,
        List<String> loreOrder,
        Material baseMaterial,
        byte[] baseItemSnapshot,
        Integer customModelData,
        boolean trinket,
        List<String> allowedSlots,
        ArmorClass armorClass,
        int version,
        int syncedVersion,
        long createdAt,
        long updatedAt,
        String createdBy
) {

    public ItemTemplate withBumpedVersion(long updatedAt) {
        return new ItemTemplate(id, key, displayName, customLore, hiddenHeaders, loreOrder, baseMaterial, baseItemSnapshot, customModelData,
                trinket, allowedSlots, armorClass, version + 1, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public ItemTemplate withSyncedVersion(int syncedVersion, long updatedAt) {
        return new ItemTemplate(id, key, displayName, customLore, hiddenHeaders, loreOrder, baseMaterial, baseItemSnapshot, customModelData,
                trinket, allowedSlots, armorClass, version, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public boolean isFullySynced() {
        return syncedVersion == version;
    }
}
