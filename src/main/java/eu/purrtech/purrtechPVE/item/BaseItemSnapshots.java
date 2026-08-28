package eu.purrtech.purrtechPVE.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;

/**
 * Captures a held/decoded item's {@link PersistentDataContainer} (its
 * {@code minecraft:custom_data} component) so a later from-scratch render of
 * the template built from it can carry the same custom_data forward - most
 * third-party custom-item plugins (ItemsAdder chief among them) key their own
 * client-side model/behavior off a PDC entry there, which our own render
 * would otherwise silently drop (that's exactly what caused a custom-model
 * helmet to render fine in hand/inventory but show no texture at all once
 * worn - nothing was carrying its {@code itemsadder:...} marker over).
 *
 * <p>Deliberately excludes ValhallaMMO's own {@code default_stats}/{@code
 * actual_stats} keys (redundant once translated into this plugin's own
 * damage/resist system - per explicit instruction, "kopíruj i NBT nejlépe
 * ale bez dat z ValhallaMMO"). This plugin's own stamp keys aren't stripped
 * here - not worth threading an {@code ItemRenderer} reference through
 * every capture call site for it, since {@link ItemRenderer#render} always
 * applies this snapshot FIRST and stamps {@code template_key}/{@code
 * template_version} fresh afterwards, so a stale stamp riding along in the
 * blob (e.g. the held item happened to be another one of our own rendered
 * items) is harmlessly overwritten every time regardless.
 */
public final class BaseItemSnapshots {

    private static final NamespacedKey VALHALLA_DEFAULT_STATS = new NamespacedKey("valhallammo", "default_stats");
    private static final NamespacedKey VALHALLA_ACTUAL_STATS = new NamespacedKey("valhallammo", "actual_stats");

    private BaseItemSnapshots() {
    }

    /** {@code null} when there's nothing worth carrying forward (no item, no meta, or an empty/all-excluded PDC). */
    public static byte[] capture(ItemStack source) {
        if (source == null || !source.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer sourcePdc = source.getItemMeta().getPersistentDataContainer();
        if (sourcePdc.isEmpty()) {
            return null;
        }
        PersistentDataContainer scratch = scratchContainer();
        sourcePdc.copyTo(scratch, true);
        scratch.remove(VALHALLA_DEFAULT_STATS);
        scratch.remove(VALHALLA_ACTUAL_STATS);
        if (scratch.isEmpty()) {
            return null;
        }
        try {
            return scratch.serializeToBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** No-op on {@code null}/empty bytes or a blob this server can't read back (corrupt, or from an incompatible version). */
    public static void apply(ItemMeta meta, byte[] snapshotBytes) {
        if (snapshotBytes == null || snapshotBytes.length == 0) {
            return;
        }
        try {
            PersistentDataContainer scratch = scratchContainer();
            scratch.readFromBytes(snapshotBytes, true);
            scratch.copyTo(meta.getPersistentDataContainer(), true);
        } catch (IOException e) {
            // Carrying forward third-party custom_data is a best-effort nicety - a stale/corrupt
            // blob shouldn't break the whole render.
        }
    }

    /** A throwaway container to serialize into/deserialize out of - PersistentDataContainer has no standalone constructor, only ever obtained off real ItemMeta. */
    private static PersistentDataContainer scratchContainer() {
        return new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
    }
}
