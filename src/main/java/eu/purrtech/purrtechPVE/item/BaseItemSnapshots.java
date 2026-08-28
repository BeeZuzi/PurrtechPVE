package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Captures/restores the FULL original item as raw NBT bytes ({@link ItemStack#serializeAsBytes()} -
 * the same technique {@code AccessoryRepository} already uses to persist arbitrary held items with
 * full fidelity), so a template rendered from scratch can start from a real clone of it instead of a
 * bare {@code new ItemStack(baseMaterial)}.
 *
 * <p>This is deliberately NOT built on copying just the item's {@link PersistentDataContainer} (a
 * first pass at this feature was, and it silently never worked for the actual reported case). Bukkit's
 * PDC view of an item's {@code minecraft:custom_data} component is scoped to one specific sub-tag
 * Bukkit itself writes custom values under - {@code PublicBukkitValues} - and does NOT see raw NBT a
 * plugin writes directly under {@code custom_data} without going through the PDC API. That's exactly
 * what ItemsAdder (and, per its own documentation, Nexo/Oraxen the same way) does for its own
 * custom-item marker: a top-level {@code custom_data.itemsadder} tag that's a SIBLING of -
 * NOT nested inside - {@code PublicBukkitValues}, confirmed by comparing the raw NBT of an actual
 * ItemsAdder+ValhallaMMO item (where {@code itemsadder:{...}} and {@code PublicBukkitValues:
 * {valhallammo:...}} sit side by side under the same {@code custom_data} compound). {@code
 * PersistentDataContainer.copyTo}/{@code serializeToBytes} can only ever see the latter, so a
 * PDC-only capture silently drops exactly the tag third-party custom-item plugins need to recognize
 * and render their own model - which is why the rendered item's custom armor never showed once worn,
 * even though customModelData (a real component of its own) came through fine.
 *
 * <p>Serializing/restoring the whole {@link ItemStack} sidesteps the problem entirely - it doesn't
 * matter which plugin wrote what data or how, everything comes along in one raw NBT round-trip. This
 * plugin's own name/lore/enchants/attributes/version-stamp are then layered on top of the restored
 * item in {@link ItemRenderer}, exactly as before.
 */
public final class BaseItemSnapshots {

    private static final NamespacedKey VALHALLA_DEFAULT_STATS = new NamespacedKey("valhallammo", "default_stats");
    private static final NamespacedKey VALHALLA_ACTUAL_STATS = new NamespacedKey("valhallammo", "actual_stats");

    private BaseItemSnapshots() {
    }

    /**
     * {@code null} for nothing worth remembering (no item at all). ValhallaMMO's own {@code
     * default_stats}/{@code actual_stats} PDC keys are stripped here (on a clone - the player's
     * actual held item is never touched) per explicit instruction, "kopíruj i NBT nejlépe ale bez
     * dat z ValhallaMMO": redundant once translated into this plugin's own damage/resist system.
     */
    public static byte[] capture(ItemStack source) {
        if (source == null || source.getType() == Material.AIR) {
            return null;
        }
        ItemStack copy = source.clone();
        if (copy.hasItemMeta()) {
            ItemMeta meta = copy.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(VALHALLA_DEFAULT_STATS);
            pdc.remove(VALHALLA_ACTUAL_STATS);
            copy.setItemMeta(meta);
        }
        return copy.serializeAsBytes();
    }

    /** A real clone of whatever was captured, or a bare new stack of {@code fallbackMaterial} if nothing ever was (or the blob is unreadable). */
    public static ItemStack restore(byte[] snapshotBytes, Material fallbackMaterial) {
        if (snapshotBytes == null || snapshotBytes.length == 0) {
            return new ItemStack(fallbackMaterial);
        }
        try {
            return ItemStack.deserializeBytes(snapshotBytes);
        } catch (Exception e) {
            // A stale blob from an incompatible/older server version shouldn't break the whole render.
            return new ItemStack(fallbackMaterial);
        }
    }
}
