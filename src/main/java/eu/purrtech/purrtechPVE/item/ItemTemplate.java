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
 */
public record ItemTemplate(
        UUID id,
        String key,
        String displayName,
        Material baseMaterial,
        Integer customModelData,
        boolean trinket,
        List<String> allowedSlots,
        int version,
        int syncedVersion,
        long createdAt,
        long updatedAt,
        String createdBy
) {

    public ItemTemplate withBumpedVersion(long updatedAt) {
        return new ItemTemplate(id, key, displayName, baseMaterial, customModelData, trinket, allowedSlots,
                version + 1, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public ItemTemplate withSyncedVersion(int syncedVersion, long updatedAt) {
        return new ItemTemplate(id, key, displayName, baseMaterial, customModelData, trinket, allowedSlots,
                version, syncedVersion, createdAt, updatedAt, createdBy);
    }

    public boolean isFullySynced() {
        return syncedVersion == version;
    }
}
