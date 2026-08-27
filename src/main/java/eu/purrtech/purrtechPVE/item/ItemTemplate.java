package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * An admin-defined item blueprint. {@code baseItemSnapshot}-based rebasing
 * (dragging another item onto this template's base, or {@code /pve item
 * setbase} from hand) is a later phase - for now the base is just a vanilla
 * {@link Material} + optional custom model data.
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
 */
public record ItemTemplate(
        UUID id,
        String key,
        String displayName,
        Material baseMaterial,
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
        return new ItemTemplate(id, key, displayName, baseMaterial, customModelData, trinket, allowedSlots, armorClass,
                version + 1, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public ItemTemplate withSyncedVersion(int syncedVersion, long updatedAt) {
        return new ItemTemplate(id, key, displayName, baseMaterial, customModelData, trinket, allowedSlots, armorClass,
                version, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public boolean isFullySynced() {
        return syncedVersion == version;
    }
}
