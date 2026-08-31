package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/**
 * A generic "+/- buttons to nudge a number, plus a lore-visibility toggle" screen, opened from
 * clicking a configured entry in RESIST/ARMOR_PENETRATION/SPECIAL_EFFECTS, or an existing
 * ATTRIBUTES entry - one shared layout for every purely-numeric field this GUI edits, so an
 * admin doesn't have to drop into chat just to bump one number or hide one stat line. See {@link
 * ValueEditorKind} for what each kind reads/writes and which tab "Back" returns to.
 * {@link ValueEditorKind#BLEED_DAMAGE} additionally gets a flat/percent mode toggle (see {@code
 * BleedEffect}'s javadoc) - every other kind is a plain number, no mode concept at all.
 *
 * <p>Deliberately NOT used for DAMAGE or for creating a brand-new ATTRIBUTES entry: those still
 * need a non-numeric choice up front (mode/context for damage; slot/operation for a new
 * attribute), which stays on the existing chat-prompt flow - this screen only ever adjusts a
 * value that's already been given its non-numeric shape.
 */
public final class ValueEditorMenu {

    private static final int SIZE = 27;
    private static final int DEC_10 = 0;
    private static final int DEC_5 = 1;
    private static final int DEC_1 = 2;
    private static final int DEC_POINT_1 = 3;
    private static final int VALUE_SLOT = 4;
    private static final int INC_POINT_1 = 5;
    private static final int INC_1 = 6;
    private static final int INC_5 = 7;
    private static final int INC_10 = 8;
    private static final int VISIBLE_TOGGLE_SLOT = 13;
    private static final int MODE_TOGGLE_SLOT = 15;
    private static final int BACK_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private ValueEditorMenu() {
    }

    public static void open(PurrtechPVE plugin, Player player, String templateKey, ValueEditorKind kind, String entryId) {
        Locale locale = player.locale();
        ValueEditorHolder holder = new ValueEditorHolder(templateKey, kind, entryId);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, plugin.getMessages().render(locale, "gui.value-editor.title"));
        holder.setInventory(inventory);
        render(plugin, inventory, holder, locale);
        player.openInventory(inventory);
    }

    private static void render(PurrtechPVE plugin, Inventory inventory, ValueEditorHolder holder, Locale locale) {
        inventory.clear();
        Messages messages = plugin.getMessages();
        CurrentState state = currentState(plugin, holder);

        inventory.setItem(DEC_10, stepButton(messages, locale, false, "10"));
        inventory.setItem(DEC_5, stepButton(messages, locale, false, "5"));
        inventory.setItem(DEC_1, stepButton(messages, locale, false, "1"));
        inventory.setItem(DEC_POINT_1, stepButton(messages, locale, false, "0.1"));
        inventory.setItem(INC_POINT_1, stepButton(messages, locale, true, "0.1"));
        inventory.setItem(INC_1, stepButton(messages, locale, true, "1"));
        inventory.setItem(INC_5, stepButton(messages, locale, true, "5"));
        inventory.setItem(INC_10, stepButton(messages, locale, true, "10"));

        ItemStack valueIcon = named(Material.BOOK, messages.render(locale, "gui.value-editor.current-value",
                Placeholder.unparsed("value", formatAmount(state.value()))));
        ItemMeta valueMeta = valueIcon.getItemMeta();
        valueMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-adjust")));
        valueIcon.setItemMeta(valueMeta);
        inventory.setItem(VALUE_SLOT, valueIcon);

        Material toggleMaterial = state.visible() ? Material.LIME_DYE : Material.GRAY_DYE;
        String toggleKey = state.visible() ? "gui.value-editor.visible-on" : "gui.value-editor.visible-off";
        ItemStack toggle = named(toggleMaterial, messages.render(locale, toggleKey));
        ItemMeta toggleMeta = toggle.getItemMeta();
        toggleMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-toggle-visible")));
        toggle.setItemMeta(toggleMeta);
        inventory.setItem(VISIBLE_TOGGLE_SLOT, toggle);

        if (holder.kind().hasMode()) {
            boolean percent = state.mode() == DamageMode.PERCENT_OF_TOTAL;
            String modeKey = percent ? "gui.value-editor.mode-percent" : "gui.value-editor.mode-flat";
            ItemStack modeButton = named(Material.HOPPER, messages.render(locale, modeKey));
            ItemMeta modeMeta = modeButton.getItemMeta();
            modeMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-toggle-mode")));
            modeButton.setItemMeta(modeMeta);
            inventory.setItem(MODE_TOGGLE_SLOT, modeButton);
        }

        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    private static ItemStack stepButton(Messages messages, Locale locale, boolean increase, String amount) {
        String key = increase ? "gui.value-editor.increase" : "gui.value-editor.decrease";
        return named(increase ? Material.LIME_DYE : Material.RED_DYE,
                messages.render(locale, key, Placeholder.unparsed("amount", amount)));
    }

    public static void handleClick(PurrtechPVE plugin, Player player, ValueEditorHolder holder, int slot) {
        Locale locale = player.locale();
        Double delta = deltaFor(slot);
        if (delta != null) {
            CurrentState state = currentState(plugin, holder);
            applyValue(plugin, holder, state.value() + delta, state.visible(), state.mode());
            render(plugin, holder.getInventory(), holder, locale);
            return;
        }
        switch (slot) {
            case VISIBLE_TOGGLE_SLOT -> {
                CurrentState state = currentState(plugin, holder);
                applyValue(plugin, holder, state.value(), !state.visible(), state.mode());
                render(plugin, holder.getInventory(), holder, locale);
            }
            case MODE_TOGGLE_SLOT -> {
                if (!holder.kind().hasMode()) {
                    return;
                }
                CurrentState state = currentState(plugin, holder);
                DamageMode flipped = state.mode() == DamageMode.PERCENT_OF_TOTAL ? DamageMode.FLAT : DamageMode.PERCENT_OF_TOTAL;
                applyValue(plugin, holder, state.value(), state.visible(), flipped);
                render(plugin, holder.getInventory(), holder, locale);
            }
            case BACK_SLOT -> ItemEditorMenu.open(plugin, player, holder.templateKey(), holder.kind().returnTab());
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
        }
    }

    private static Double deltaFor(int slot) {
        return switch (slot) {
            case DEC_10 -> -10.0;
            case DEC_5 -> -5.0;
            case DEC_1 -> -1.0;
            case DEC_POINT_1 -> -0.1;
            case INC_POINT_1 -> 0.1;
            case INC_1 -> 1.0;
            case INC_5 -> 5.0;
            case INC_10 -> 10.0;
            default -> null;
        };
    }

    /** {@code mode} is meaningless outside {@link ValueEditorKind#BLEED_DAMAGE} - always {@code DamageMode.FLAT} there, ignored by every other kind. */
    private record CurrentState(double value, boolean visible, DamageMode mode) {
    }

    private static CurrentState currentState(PurrtechPVE plugin, ValueEditorHolder holder) {
        ItemTemplateService service = plugin.getItemTemplateService();
        String key = holder.templateKey();
        return switch (holder.kind()) {
            case RESIST -> service.typeModifiers(key).stream()
                    .filter(m -> m.damageTypeKey().equals(holder.entryId())).findFirst()
                    .map(m -> new CurrentState(m.percent(), m.visible(), DamageMode.FLAT)).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case ARMOR_PENETRATION -> service.armorPenetration(key).stream()
                    .filter(p -> p.armorClass() == ArmorClass.valueOf(holder.entryId())).findFirst()
                    .map(p -> new CurrentState(p.amount(), p.visible(), DamageMode.FLAT)).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case ATTRIBUTE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                Attribute attribute = Attribute.valueOf(parts[0]);
                String attrSlot = parts[1];
                yield service.attributeModifiers(key).stream()
                        .filter(a -> a.attribute() == attribute && a.slot().equals(attrSlot)).findFirst()
                        .map(a -> new CurrentState(a.amount(), a.visible(), DamageMode.FLAT)).orElse(new CurrentState(0, true, DamageMode.FLAT));
            }
            case BLEED_CHANCE -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.chancePercent(), b.visible(), b.mode())).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case BLEED_DURATION -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.durationSeconds(), b.visible(), b.mode())).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case BLEED_DAMAGE -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.damageAmount(), b.visible(), b.mode())).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case CRIT_CHANCE -> service.criticalEffect(key)
                    .map(c -> new CurrentState(c.chancePercent(), c.visible(), DamageMode.FLAT)).orElse(new CurrentState(0, true, DamageMode.FLAT));
            case CRIT_BONUS -> service.criticalEffect(key)
                    .map(c -> new CurrentState(c.bonusDamagePercent(), c.visible(), DamageMode.FLAT)).orElse(new CurrentState(0, true, DamageMode.FLAT));
        };
    }

    /** {@code mode} only actually matters for {@link ValueEditorKind#BLEED_DAMAGE} - every other kind's {@code setXxx} call just ignores/doesn't take one. */
    private static void applyValue(PurrtechPVE plugin, ValueEditorHolder holder, double newValue, boolean visible, DamageMode mode) {
        ItemTemplateService service = plugin.getItemTemplateService();
        String key = holder.templateKey();
        switch (holder.kind()) {
            case RESIST -> service.setTypeModifier(key, holder.entryId(), newValue, visible);
            case ARMOR_PENETRATION -> service.setArmorPenetration(key, ArmorClass.valueOf(holder.entryId()), newValue, visible);
            case ATTRIBUTE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                Attribute attribute = Attribute.valueOf(parts[0]);
                String attrSlot = parts[1];
                AttributeModifierEntry current = service.attributeModifiers(key).stream()
                        .filter(a -> a.attribute() == attribute && a.slot().equals(attrSlot)).findFirst().orElseThrow();
                service.setAttributeModifier(key, attribute, newValue, current.operation(), attrSlot, visible);
            }
            case BLEED_CHANCE -> {
                BleedEffect current = service.bleedEffect(key).orElse(new BleedEffect(0, 0, 0, DamageMode.FLAT, true));
                service.setBleedEffect(key, newValue, current.durationSeconds(), current.damageAmount(), current.mode(), visible);
            }
            case BLEED_DURATION -> {
                BleedEffect current = service.bleedEffect(key).orElse(new BleedEffect(0, 0, 0, DamageMode.FLAT, true));
                service.setBleedEffect(key, current.chancePercent(), newValue, current.damageAmount(), current.mode(), visible);
            }
            case BLEED_DAMAGE -> {
                BleedEffect current = service.bleedEffect(key).orElse(new BleedEffect(0, 0, 0, DamageMode.FLAT, true));
                service.setBleedEffect(key, current.chancePercent(), current.durationSeconds(), newValue, mode, visible);
            }
            case CRIT_CHANCE -> {
                CriticalEffect current = service.criticalEffect(key).orElse(new CriticalEffect(0, 0, true));
                service.setCriticalEffect(key, newValue, current.bonusDamagePercent(), visible);
            }
            case CRIT_BONUS -> {
                CriticalEffect current = service.criticalEffect(key).orElse(new CriticalEffect(0, 0, true));
                service.setCriticalEffect(key, current.chancePercent(), newValue, visible);
            }
        }
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        // Rounds away binary-fraction noise from repeated +/-0.1 clicks (e.g. 0.1+0.1+0.1
        // landing on 0.30000000000000004) without needing BigDecimal - 1 decimal place is the
        // finest step this editor offers, so nothing meaningful is ever lost.
        return String.valueOf(Math.round(amount * 10.0) / 10.0);
    }
}
