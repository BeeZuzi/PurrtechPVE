package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.LoreLine;
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
 * Reorders a template's individual {@link LoreLine}s - one paper per line (a header, a single
 * stat entry, or one custom-lore line), left-to-right/top-to-bottom in the row matching top-to-
 * bottom in the rendered lore. Left-click moves a paper one slot earlier (up in the lore),
 * right-click moves it one slot later (down) - no wraparound, a click at either end that can't
 * move further is just a no-op re-render. Line-level (not whole-category) granularity is
 * deliberate - see {@link LoreLine}'s javadoc for why: an admin needs to be able to interleave a
 * custom line between two stat lines, which a fixed category block could never allow.
 */
public final class LoreOrderMenu {

    private static final int SIZE = 54;
    // Bottom-right corner for Back/Close, same relative feel as every other menu in this GUI -
    // everything before them (slots 0-48) is available for line icons, comfortably more than any
    // realistic item's lore will ever need.
    private static final int BACK_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int CONTENT_CAPACITY = BACK_SLOT;

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
        List<LoreLine> lines = plugin.getItemTemplateService().loreLines(templateKey);

        for (int i = 0; i < lines.size() && i < CONTENT_CAPACITY; i++) {
            LoreLine line = lines.get(i);
            // The icon's name IS the real, fully-colored line it represents (exactly the
            // MiniMessage-rendered Component that would show up on the actual item) - not a
            // generic description - so the admin sees exactly what's about to move. Only italics
            // are normalized away here, same as every other icon name in this GUI; the color/
            // bold/etc. styling underneath is untouched.
            ItemStack icon = named(Material.PAPER, line.component());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(messages.render(locale, "gui.lore-order.position",
                    Placeholder.unparsed("position", String.valueOf(i + 1)), Placeholder.unparsed("total", String.valueOf(lines.size()))));
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
        if (slot < 0 || slot >= CONTENT_CAPACITY || (click != ClickType.LEFT && click != ClickType.RIGHT)) {
            return;
        }
        List<LoreLine> lines = plugin.getItemTemplateService().loreLines(holder.templateKey());
        if (slot >= lines.size()) {
            return;
        }
        LoreLine line = lines.get(slot);
        plugin.getItemTemplateService().moveLoreLine(holder.templateKey(), line.key(), click == ClickType.LEFT);
        render(plugin, holder.getInventory(), holder.templateKey(), player.locale());
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
