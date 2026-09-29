package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.lang.Messages;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobEquipmentListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The {@code /pve mobs} admin GUI - every MythicMobs type in one paginated list; clicking one opens
 * its equipment overview (what it wears/holds per slot, its damage profile, how many are alive right
 * now), where each slot can be assigned an item template or cleared; picking a slot opens a
 * paginated picker of all templates. Assignments are the very same {@code mob_equipment} rows the
 * item editor's MOBS tab writes, so the two always agree. Changing an assignment also re-equips every
 * already-spawned mob of that type immediately, instead of only affecting mobs spawned afterwards.
 *
 * <p>Same conventions as {@link ItemListMenu}/{@link ArmorClassMenu}: one chest inventory per
 * screen, opened fresh on navigation, a back button on every nested screen.
 */
public final class MobMenu {

    private static final int LIST_SIZE = 54;
    private static final int MOB_SIZE = 36;
    private static final int BACK_SLOT = 0;
    private static final int PREV_SLOT = 3;
    private static final int INFO_SLOT = 4;
    private static final int NEXT_SLOT = 5;
    private static final int CLOSE_SLOT = 8;
    private static final int CONTENT_START = 9;
    private static final int PAGE_SIZE = LIST_SIZE - CONTENT_START;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    // Same order as SLOTS - weapon/off-hand on the left, armor pieces top-to-bottom order on the right.
    private static final int[] SLOT_POSITIONS = {19, 20, 22, 23, 24, 25};

    private MobMenu() {
    }

    // ---- open ----

    public static void openList(PurrtechPVE plugin, Player player, int page) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        if (plugin.getMythicMobsBridge() == null) {
            player.sendMessage(messages.render(locale, "gui.mobs.no-mythicmobs"));
            return;
        }
        List<String> mobs = mobTypes(plugin);
        int clamped = clampPage(page, mobs.size());
        MobMenuHolder holder = new MobMenuHolder(MobMenuHolder.View.LIST, null, null, clamped, clamped);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, messages.render(locale, "gui.mobs.title-list"));
        holder.setInventory(inventory);
        renderList(plugin, inventory, clamped, mobs, locale);
        player.openInventory(inventory);
    }

    public static void openMob(PurrtechPVE plugin, Player player, String mobType, int returnPage) {
        Locale locale = player.locale();
        MobMenuHolder holder = new MobMenuHolder(MobMenuHolder.View.MOB, mobType, null, 0, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, MOB_SIZE,
                plugin.getMessages().render(locale, "gui.mobs.title-mob", Placeholder.unparsed("mob", mobType)));
        holder.setInventory(inventory);
        renderMob(plugin, inventory, mobType, locale);
        player.openInventory(inventory);
    }

    public static void openPick(PurrtechPVE plugin, Player player, String mobType, EquipmentSlot slot, int page, int returnPage) {
        Locale locale = player.locale();
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int clamped = clampPage(page, templates.size());
        MobMenuHolder holder = new MobMenuHolder(MobMenuHolder.View.PICK, mobType, slot, clamped, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, plugin.getMessages().render(locale, "gui.mobs.title-pick",
                Placeholder.unparsed("mob", mobType), Placeholder.unparsed("slot", slotLabel(plugin.getMessages(), locale, slot))));
        holder.setInventory(inventory);
        renderPick(plugin, inventory, holder, templates, locale);
        player.openInventory(inventory);
    }

    // ---- render ----

    private static void renderList(PurrtechPVE plugin, Inventory inventory, int page, List<String> mobs, Locale locale) {
        Messages messages = plugin.getMessages();
        renderPager(messages, inventory, page, mobs.size(), locale);
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
        if (mobs.isEmpty()) {
            inventory.setItem(CONTENT_START, named(Material.PAPER, messages.render(locale, "gui.mobs.none")));
            return;
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < mobs.size(); i++) {
            String mob = mobs.get(start + i);
            ItemStack icon = named(Material.ZOMBIE_HEAD, messages.render(locale, "gui.mobs.mob-icon", Placeholder.unparsed("mob", mob)));
            ItemMeta meta = icon.getItemMeta();
            meta.lore(List.of(
                    messages.render(locale, "gui.mobs.mob-equipped-count",
                            Placeholder.unparsed("count", String.valueOf(plugin.getMobEquipmentRepository().findByMob(mob).size()))),
                    Component.empty(),
                    messages.render(locale, "gui.mobs.hint-open")));
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    private static void renderMob(PurrtechPVE plugin, Inventory inventory, String mobType, Locale locale) {
        Messages messages = plugin.getMessages();
        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));

        ItemStack info = named(Material.ZOMBIE_HEAD, messages.render(locale, "gui.mobs.mob-icon", Placeholder.unparsed("mob", mobType)));
        ItemMeta infoMeta = info.getItemMeta();
        List<Component> infoLore = new ArrayList<>();
        Map<String, Double> profile = new TreeMap<>(plugin.getMobDamageProfileRepository().findByMob(mobType));
        if (profile.isEmpty()) {
            infoLore.add(messages.render(locale, "gui.mobs.profile-none"));
        } else {
            infoLore.add(messages.render(locale, "gui.mobs.profile-header"));
            profile.forEach((type, percent) -> infoLore.add(messages.render(locale, "gui.mobs.profile-line",
                    Placeholder.unparsed("type", type),
                    Placeholder.unparsed("percent", (percent > 0 ? "+" : "") + DamageFeedback.formatAmount(percent)))));
        }
        int live = liveCount(plugin, mobType);
        if (live >= 0) {
            infoLore.add(messages.render(locale, "gui.mobs.live-count", Placeholder.unparsed("count", String.valueOf(live))));
        }
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO_SLOT, info);

        Map<String, UUID> assigned = plugin.getMobEquipmentRepository().findByMob(mobType);
        List<ItemTemplate> templates = plugin.getItemTemplateService().listAll();
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            String label = slotLabel(messages, locale, slot);
            UUID templateId = assigned.get(slot.name());
            Optional<ItemTemplate> template = templateId == null ? Optional.empty()
                    : templates.stream().filter(t -> t.id().equals(templateId)).findFirst();
            inventory.setItem(SLOT_POSITIONS[i], template.isPresent()
                    ? assignedIcon(plugin, template.get(), label, locale)
                    : emptyIcon(messages, label, locale));
        }
    }

    private static ItemStack assignedIcon(PurrtechPVE plugin, ItemTemplate template, String label, Locale locale) {
        Messages messages = plugin.getMessages();
        ItemStack icon;
        try {
            icon = plugin.getItemTemplateService().renderGiveable(template.key());
        } catch (TemplateNotFoundException e) {
            return emptyIcon(messages, label, locale);
        }
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.empty());
        lore.add(messages.render(locale, "gui.mobs.slot-label", Placeholder.unparsed("slot", label)));
        lore.add(messages.render(locale, "gui.mobs.slot-item-key", Placeholder.unparsed("key", template.key())));
        lore.add(Component.empty());
        lore.add(messages.render(locale, "gui.mobs.hint-pick"));
        lore.add(messages.render(locale, "gui.mobs.hint-remove"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack emptyIcon(Messages messages, String label, Locale locale) {
        ItemStack icon = named(Material.GRAY_STAINED_GLASS_PANE, messages.render(locale, "gui.mobs.slot-empty", Placeholder.unparsed("slot", label)));
        ItemMeta meta = icon.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.mobs.hint-pick")));
        icon.setItemMeta(meta);
        return icon;
    }

    private static void renderPick(PurrtechPVE plugin, Inventory inventory, MobMenuHolder holder, List<ItemTemplate> templates, Locale locale) {
        Messages messages = plugin.getMessages();
        renderPager(messages, inventory, holder.page(), templates.size(), locale);
        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
        String label = slotLabel(messages, locale, holder.slot());
        int start = holder.page() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < templates.size(); i++) {
            ItemTemplate template = templates.get(start + i);
            ItemStack icon;
            try {
                icon = plugin.getItemTemplateService().renderGiveable(template.key());
            } catch (TemplateNotFoundException e) {
                continue;
            }
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(messages.render(locale, "gui.mobs.slot-item-key", Placeholder.unparsed("key", template.key())));
            lore.add(messages.render(locale, "gui.mobs.pick-hint", Placeholder.unparsed("slot", label)));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_START + i, icon);
        }
    }

    /** Prev/Info/Next strip shared by the mob list and the item picker. */
    private static void renderPager(Messages messages, Inventory inventory, int page, int count, Locale locale) {
        int totalPages = lastPage(count) + 1;
        ItemStack info = named(Material.BOOK, messages.render(locale, "gui.mobs.page",
                Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("total", String.valueOf(totalPages))));
        ItemMeta meta = info.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.mobs.count", Placeholder.unparsed("count", String.valueOf(count)))));
        info.setItemMeta(meta);
        inventory.setItem(INFO_SLOT, info);
        if (page > 0) {
            inventory.setItem(PREV_SLOT, named(Material.ARROW, messages.render(locale, "gui.mobs.prev-page")));
        }
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, named(Material.ARROW, messages.render(locale, "gui.mobs.next-page")));
        }
    }

    // ---- clicks ----

    public static void handleClick(PurrtechPVE plugin, Player player, MobMenuHolder holder, int slot, boolean shift) {
        switch (holder.view()) {
            case LIST -> handleListClick(plugin, player, holder, slot);
            case MOB -> handleMobClick(plugin, player, holder, slot, shift);
            case PICK -> handlePickClick(plugin, player, holder, slot);
        }
    }

    private static void handleListClick(PurrtechPVE plugin, Player player, MobMenuHolder holder, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == PREV_SLOT) {
            openList(plugin, player, holder.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            openList(plugin, player, holder.page() + 1);
            return;
        }
        if (slot < CONTENT_START) {
            return;
        }
        List<String> mobs = mobTypes(plugin);
        int index = holder.page() * PAGE_SIZE + (slot - CONTENT_START);
        if (index >= 0 && index < mobs.size()) {
            openMob(plugin, player, mobs.get(index), holder.page());
        }
    }

    private static void handleMobClick(PurrtechPVE plugin, Player player, MobMenuHolder holder, int slot, boolean shift) {
        if (slot == BACK_SLOT) {
            openList(plugin, player, holder.returnPage());
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            if (slot != SLOT_POSITIONS[i]) {
                continue;
            }
            EquipmentSlot equipmentSlot = SLOTS[i];
            if (shift) {
                remove(plugin, player, holder, equipmentSlot);
            } else {
                openPick(plugin, player, holder.mobType(), equipmentSlot, 0, holder.returnPage());
            }
            return;
        }
    }

    private static void handlePickClick(PurrtechPVE plugin, Player player, MobMenuHolder holder, int slot) {
        if (slot == BACK_SLOT) {
            openMob(plugin, player, holder.mobType(), holder.returnPage());
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == PREV_SLOT) {
            openPick(plugin, player, holder.mobType(), holder.slot(), holder.page() - 1, holder.returnPage());
            return;
        }
        if (slot == NEXT_SLOT) {
            openPick(plugin, player, holder.mobType(), holder.slot(), holder.page() + 1, holder.returnPage());
            return;
        }
        if (slot < CONTENT_START) {
            return;
        }
        List<ItemTemplate> templates = sortedTemplates(plugin);
        int index = holder.page() * PAGE_SIZE + (slot - CONTENT_START);
        if (index < 0 || index >= templates.size()) {
            return;
        }
        assign(plugin, player, holder, templates.get(index));
    }

    private static void assign(PurrtechPVE plugin, Player player, MobMenuHolder holder, ItemTemplate template) {
        Messages messages = plugin.getMessages();
        Locale locale = player.locale();
        plugin.getMobEquipmentRepository().set(holder.mobType(), holder.slot().name(), template.id());
        player.sendMessage(messages.render(locale, "gui.mobs.assigned",
                Placeholder.unparsed("mob", holder.mobType()), Placeholder.unparsed("slot", slotLabel(messages, locale, holder.slot())),
                Placeholder.unparsed("key", template.key())));
        reportSync(plugin, player, syncLiving(plugin, holder.mobType(), null));
        openMob(plugin, player, holder.mobType(), holder.returnPage());
    }

    private static void remove(PurrtechPVE plugin, Player player, MobMenuHolder holder, EquipmentSlot slot) {
        Messages messages = plugin.getMessages();
        Locale locale = player.locale();
        if (plugin.getMobEquipmentRepository().remove(holder.mobType(), slot.name())) {
            player.sendMessage(messages.render(locale, "gui.mobs.removed",
                    Placeholder.unparsed("mob", holder.mobType()), Placeholder.unparsed("slot", slotLabel(messages, locale, slot))));
            reportSync(plugin, player, syncLiving(plugin, holder.mobType(), slot));
        }
        openMob(plugin, player, holder.mobType(), holder.returnPage());
    }

    private static void reportSync(PurrtechPVE plugin, Player player, int updated) {
        if (updated > 0) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "gui.mobs.synced",
                    Placeholder.unparsed("count", String.valueOf(updated))));
        }
    }

    /**
     * Re-equips every currently living mob of {@code mobType} from the current assignments (and first
     * empties {@code clearedSlot}, when a slot was just cleared, since {@code equip} only ever sets
     * slots that still have an assignment). Returns how many mobs were touched; 0 without MythicMobs.
     */
    private static int syncLiving(PurrtechPVE plugin, String mobType, EquipmentSlot clearedSlot) {
        MythicMobsBridge bridge = plugin.getMythicMobsBridge();
        MythicMobEquipmentListener listener = plugin.getMobEquipmentListener();
        if (bridge == null || listener == null) {
            return 0;
        }
        try {
            List<LivingEntity> living = bridge.activeMobsOfType(mobType);
            for (LivingEntity mob : living) {
                if (clearedSlot != null && mob.getEquipment() != null) {
                    mob.getEquipment().setItem(clearedSlot, null);
                }
                listener.equip(mob, mobType);
            }
            return living.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---- helpers ----

    private static int liveCount(PurrtechPVE plugin, String mobType) {
        MythicMobsBridge bridge = plugin.getMythicMobsBridge();
        if (bridge == null) {
            return -1;
        }
        try {
            return bridge.activeMobsOfType(mobType).size();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static List<String> mobTypes(PurrtechPVE plugin) {
        try {
            return plugin.getMythicMobsBridge().listMobTypeInternalNames();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<ItemTemplate> sortedTemplates(PurrtechPVE plugin) {
        return plugin.getItemTemplateService().listAll().stream()
                .sorted(Comparator.comparing(ItemTemplate::key))
                .toList();
    }

    private static String slotLabel(Messages messages, Locale locale, EquipmentSlot slot) {
        return messages.plain(locale, "gui.mobs.slot." + slot.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static int lastPage(int count) {
        return Math.max(0, (count - 1) / PAGE_SIZE);
    }

    private static int clampPage(int page, int count) {
        return Math.max(0, Math.min(page, lastPage(count)));
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
