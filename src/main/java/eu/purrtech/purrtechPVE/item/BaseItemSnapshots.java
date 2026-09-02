package eu.purrtech.purrtechPVE.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    /**
     * The source item's own lore, each line re-serialized to a MiniMessage string - the seed for
     * {@link ItemTemplate#customLore()} on import, so an item's original flavor text (colors, bold,
     * whatever formatting it had) isn't just silently dropped in favor of this plugin's own
     * stat-derived lore. Empty (not null) when the source has no lore at all.
     */
    public static List<String> captureLore(ItemStack source) {
        if (source == null || !source.hasItemMeta()) {
            return List.of();
        }
        List<Component> lore = source.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        return lore.stream().map(line -> MiniMessage.miniMessage().serialize(line)).toList();
    }

    /**
     * The item's own display name, plain-text (colors/formatting stripped), if it has a real one
     * set (e.g. anvil-renamed) - empty otherwise. Shared by every "derive this template's display
     * name from a real item" flow: ValhallaMMO import (single-item and bulk), and the item
     * list menu's "hold an item, click + Create item" flow.
     */
    public static Optional<String> ownDisplayName(ItemStack stack) {
        if (stack != null && stack.hasItemMeta()) {
            Component name = stack.getItemMeta().displayName();
            if (name != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(name);
                if (!plain.isBlank()) {
                    return Optional.of(plain);
                }
            }
        }
        return Optional.empty();
    }

    /** Fallback for {@link #ownDisplayName} when the item has no display name of its own: its material name humanized ("IRON_SWORD" -> "Iron Sword"). */
    public static String humanizedMaterialName(Material material) {
        String[] words = material.name().split("_");
        StringBuilder humanized = new StringBuilder();
        for (String word : words) {
            if (!humanized.isEmpty()) {
                humanized.append(' ');
            }
            humanized.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return humanized.toString();
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
