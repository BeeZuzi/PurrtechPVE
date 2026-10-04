package eu.purrtech.purrtechPVE.item;

import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When {@link ItemSyncService} re-renders a stack it starts from a fresh item, so everything the
 * player (or another plugin) did to the old one - anvil enchants, wear, a rename, an added lore
 * line, foreign attribute modifiers or PDC data - would be wiped on every template update. This
 * copies that state back onto the new render.
 *
 * <p>What counts as "ours" versus "theirs" is decided against {@code baseline}, the OLD template
 * version rendered fresh: an enchantment or attribute modifier the old stack has beyond/different
 * from the baseline was added by someone else and is kept; one that matches the baseline belongs to
 * the template and is replaced by the new version's value. Name and lore are compared by the
 * fingerprints {@link ItemRenderer} stamped when it rendered them (not by re-rendering, since lore
 * also depends on live data and the lang), so only lines it did not write are carried.
 */
public final class StackStateCarrier {

    private final ItemRenderer renderer;

    public StackStateCarrier(ItemRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Copies the player-owned state of {@code old} onto {@code fresh} in place.
     *
     * @param baseline {@code old}'s template version rendered fresh, or {@code null} if that snapshot is
     *                 gone - then enchantments and attribute modifiers can't be told apart from the
     *                 template's own and are left to the new render
     */
    public void carry(ItemStack old, ItemStack baseline, ItemStack fresh) {
        ItemMeta oldMeta = old.getItemMeta();
        ItemMeta meta = fresh.getItemMeta();
        if (oldMeta == null || meta == null) {
            return;
        }
        ItemMeta baselineMeta = baseline == null ? null : baseline.getItemMeta();
        if (baselineMeta != null) {
            carryEnchantments(oldMeta, baselineMeta, meta);
            carryAttributeModifiers(oldMeta, baselineMeta, meta);
        }
        carryDurability(old, fresh, oldMeta, meta);
        carryText(oldMeta, meta);
        // replace=false: the new render's own stamp (template key/version, lang hash, text fingerprints) must win
        oldMeta.getPersistentDataContainer().copyTo(meta.getPersistentDataContainer(), false);
        fresh.setItemMeta(meta);
    }

    private static void carryEnchantments(ItemMeta oldMeta, ItemMeta baselineMeta, ItemMeta meta) {
        for (Map.Entry<Enchantment, Integer> entry : oldMeta.getEnchants().entrySet()) {
            int onOld = entry.getValue();
            if (onOld == baselineMeta.getEnchantLevel(entry.getKey())) {
                continue; // exactly what the old template gave it - the new version decides
            }
            // never below what the new template grants, never below what the player has
            meta.addEnchant(entry.getKey(), Math.max(onOld, meta.getEnchantLevel(entry.getKey())), true);
        }
    }

    private static void carryAttributeModifiers(ItemMeta oldMeta, ItemMeta baselineMeta, ItemMeta meta) {
        Multimap<Attribute, AttributeModifier> oldModifiers = oldMeta.getAttributeModifiers();
        if (oldModifiers == null) {
            return;
        }
        Set<NamespacedKey> known = keysOf(baselineMeta.getAttributeModifiers());
        known.addAll(keysOf(meta.getAttributeModifiers()));
        for (Map.Entry<Attribute, AttributeModifier> entry : oldModifiers.entries()) {
            if (known.add(entry.getValue().getKey())) {
                meta.addAttributeModifier(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Set<NamespacedKey> keysOf(Multimap<Attribute, AttributeModifier> modifiers) {
        Set<NamespacedKey> keys = new HashSet<>();
        if (modifiers != null) {
            modifiers.values().forEach(modifier -> keys.add(modifier.getKey()));
        }
        return keys;
    }

    private static void carryDurability(ItemStack old, ItemStack fresh, ItemMeta oldMeta, ItemMeta meta) {
        if (!(oldMeta instanceof Damageable oldDamageable) || !(meta instanceof Damageable freshDamageable)
                || !oldDamageable.hasDamage()) {
            return;
        }
        int oldMax = oldDamageable.hasMaxDamage() ? oldDamageable.getMaxDamage() : old.getType().getMaxDurability();
        int newMax = freshDamageable.hasMaxDamage() ? freshDamageable.getMaxDamage() : fresh.getType().getMaxDurability();
        if (newMax <= 0) {
            return; // the new base item can't take damage at all
        }
        freshDamageable.setDamage(scaledDamage(oldDamageable.getDamage(), oldMax, newMax));
    }

    /**
     * The wear to put on an item with durability {@code newMax} given {@code damage} on one with
     * {@code oldMax}: unchanged when the maximum is the same, otherwise the same fraction worn, and
     * never enough to break the item outright.
     */
    static int scaledDamage(int damage, int oldMax, int newMax) {
        int scaled = (oldMax == newMax || oldMax <= 0) ? damage : (int) Math.round(damage * (double) newMax / oldMax);
        return Math.max(0, Math.min(scaled, newMax - 1));
    }

    /** A renamed item keeps its name, and lore lines the renderer didn't write are appended after the new lore. */
    private void carryText(ItemMeta oldMeta, ItemMeta meta) {
        PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();

        Integer renderedName = oldPdc.get(renderer.renderedNameHashPdc(), PersistentDataType.INTEGER);
        Component oldName = oldMeta.displayName();
        if (renderedName != null && oldName != null && ItemRenderer.textHash(oldName) != renderedName) {
            meta.displayName(oldName);
        }

        int[] renderedLore = oldPdc.get(renderer.renderedLoreHashesPdc(), PersistentDataType.INTEGER_ARRAY);
        List<Component> oldLore = oldMeta.lore();
        if (renderedLore == null || oldLore == null) {
            return; // stacks rendered before the fingerprints existed: nothing to tell ours from theirs
        }
        Set<Integer> ours = new HashSet<>();
        for (int hash : renderedLore) {
            ours.add(hash);
        }
        List<Component> foreign = new ArrayList<>();
        for (Component line : oldLore) {
            if (!ours.contains(ItemRenderer.textHash(line))) {
                foreign.add(line);
            }
        }
        if (foreign.isEmpty()) {
            return;
        }
        List<Component> merged = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        merged.addAll(foreign);
        meta.lore(merged);
    }
}
