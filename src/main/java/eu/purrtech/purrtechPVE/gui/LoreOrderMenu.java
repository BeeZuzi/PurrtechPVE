package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.LoreBlock;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reorders a template's 8 {@link LoreBlock}s (see that enum's javadoc for what a "block" is and
 * why {@link LoreBlock#CUSTOM} moves as one unit rather than per-line) - one paper per block,
 * left-to-right in the row matching top-to-bottom in the rendered lore. Left-click moves a paper
 * one slot left (up in the lore), right-click moves it one slot right (down) - no wraparound, a
 * click at either end that can't move further is just a no-op re-render.
 */
public final class LoreOrderMenu {

    private static final int SIZE = 27;
    private static final int BLOCK_COUNT = LoreBlock.values().length;
    private static final int BACK_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private LoreOrderMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, String templateKey, ItemEditorTab returnTab) {
        Locale locale = player.locale();
        LoreOrderHolder holder = new LoreOrderHolder(templateKey, returnTab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().render(locale, "gui.lore-order.title"));
        holder.setInventory(inventory);
        render(plugin, inventory, templateKey, locale);
        player.openInventory(inventory);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, String templateKey, Locale locale) {
        inventory.clear();
        Messages messages = plugin.getMessages();
        ItemTemplate template = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow();
        List<LoreBlock> order = LoreBlock.canonicalize(template.loreOrder());

        for (int i = 0; i < order.size(); i++) {
            LoreBlock block = order.get(i);
            ItemStack icon = named(Material.PAPER, messages.render(locale, labelKey(block)));
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(messages.render(locale, "gui.lore-order.position",
                    Placeholder.unparsed("position", String.valueOf(i + 1)), Placeholder.unparsed("total", String.valueOf(order.size()))));
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.lore-order.hint-left"));
            lore.add(messages.render(locale, "gui.lore-order.hint-right"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(i, icon);
        }

        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    public static void handleClick(PurrtechPVE plugin, Player player, LoreOrderHolder holder, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            ItemEditorMenu.open(plugin, player, holder.templateKey(), holder.returnTab());
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= BLOCK_COUNT || (click != ClickType.LEFT && click != ClickType.RIGHT)) {
            return;
        }
        ItemTemplate template = plugin.getItemTemplateService().findByKey(holder.templateKey()).orElseThrow();
        List<LoreBlock> order = LoreBlock.canonicalize(template.loreOrder());
        if (slot >= order.size()) {
            return;
        }
        LoreBlock block = order.get(slot);
        plugin.getItemTemplateService().moveLoreBlock(holder.templateKey(), block, click == ClickType.LEFT);
        render(plugin, holder.getInventory(), holder.templateKey(), player.locale());
    }

    private static String labelKey(LoreBlock block) {
        return switch (block) {
            case CUSTOM -> "gui.lore-order.block.custom";
            case DAMAGE -> "gui.lore-order.block.damage";
            case PASSIVE -> "gui.lore-order.block.passive";
            case RESIST -> "gui.lore-order.block.resist";
            case PENETRATION -> "gui.lore-order.block.penetration";
            case BLEED -> "gui.lore-order.block.bleed";
            case CRITICAL -> "gui.lore-order.block.critical";
            case ATTRIBUTES -> "gui.lore-order.block.attributes";
        };
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
