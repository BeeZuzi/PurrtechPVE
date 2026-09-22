package eu.purrtech.purrtechPVE.gui;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.db.MobDropEntry;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.ReflectEffect;
import eu.purrtech.purrtechPVE.item.StunEffect;
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
import java.util.Optional;
import java.util.UUID;

/**
 * A generic "+/- buttons to nudge a number, plus a lore-visibility toggle" screen, opened from
 * clicking a configured entry in DAMAGE/RESIST/ARMOR_PENETRATION/SPECIAL_EFFECTS, or an existing
 * ATTRIBUTES entry - one shared layout for every purely-numeric field this GUI edits, so an
 * admin doesn't have to drop into chat just to bump one number or hide one stat line. See {@link
 * ValueEditorKind} for what each kind reads/writes and which tab "Back" returns to.
 * {@link ValueEditorKind#BLEED_DAMAGE}/{@link ValueEditorKind#DAMAGE}/{@link
 * ValueEditorKind#ARMOR_PENETRATION} additionally get a flat/percent mode toggle (see {@code
 * BleedEffect}'s javadoc for the first, {@code ArmorPenetration}'s for the last); {@link
 * ValueEditorKind#DAMAGE} alone also gets a wielded/worn context toggle - flipping it moves the
 * contribution to the other context (a "move", not a plain field edit, since context is part of
 * a contribution's identity - see {@link #handleClick}), silently refusing if that would collide
 * with an already-existing separate contribution for the same damage type.
 *
 * <p>Deliberately NOT used for creating a brand-new ATTRIBUTES entry: that still needs a
 * non-numeric choice up front (slot/operation), which stays on the existing chat-prompt flow -
 * this screen only ever adjusts a value that's already been given its non-numeric shape. A new
 * DAMAGE entry doesn't have that problem - context defaults to whichever of wielded/worn isn't
 * already taken (see {@code ItemEditorMenu}'s DAMAGE tab picker) and can be flipped right here.
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
    private static final int CONTEXT_TOGGLE_SLOT = 17;
    private static final int TYPE_VALUE_SLOT = 10;
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

        inventory.setItem(DEC_10, stepButton(messages, locale, false, "10", state.value()));
        inventory.setItem(DEC_5, stepButton(messages, locale, false, "5", state.value()));
        inventory.setItem(DEC_1, stepButton(messages, locale, false, "1", state.value()));
        inventory.setItem(DEC_POINT_1, stepButton(messages, locale, false, "0.1", state.value()));
        inventory.setItem(INC_POINT_1, stepButton(messages, locale, true, "0.1", state.value()));
        inventory.setItem(INC_1, stepButton(messages, locale, true, "1", state.value()));
        inventory.setItem(INC_5, stepButton(messages, locale, true, "5", state.value()));
        inventory.setItem(INC_10, stepButton(messages, locale, true, "10", state.value()));

        ItemStack valueIcon = named(Material.BOOK, messages.render(locale, "gui.value-editor.current-value",
                Placeholder.unparsed("value", formatAmount(state.value()))));
        ItemMeta valueMeta = valueIcon.getItemMeta();
        valueMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-adjust")));
        valueIcon.setItemMeta(valueMeta);
        inventory.setItem(VALUE_SLOT, valueIcon);

        if (holder.kind().hasVisibility()) {
            Material toggleMaterial = state.visible() ? Material.LIME_DYE : Material.GRAY_DYE;
            String toggleKey = state.visible() ? "gui.value-editor.visible-on" : "gui.value-editor.visible-off";
            ItemStack toggle = named(toggleMaterial, messages.render(locale, toggleKey));
            ItemMeta toggleMeta = toggle.getItemMeta();
            toggleMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-toggle-visible")));
            toggle.setItemMeta(toggleMeta);
            inventory.setItem(VISIBLE_TOGGLE_SLOT, toggle);
        }

        if (holder.kind().hasMode()) {
            boolean percent = state.mode() == DamageMode.PERCENT_OF_TOTAL;
            String modeKey = percent ? "gui.value-editor.mode-percent" : "gui.value-editor.mode-flat";
            ItemStack modeButton = named(Material.HOPPER, messages.render(locale, modeKey));
            ItemMeta modeMeta = modeButton.getItemMeta();
            modeMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-toggle-mode")));
            modeButton.setItemMeta(modeMeta);
            inventory.setItem(MODE_TOGGLE_SLOT, modeButton);
        }

        if (holder.kind().hasContext()) {
            boolean wielded = state.context() == ModifierContext.WIELDED;
            Material contextMaterial = wielded ? Material.IRON_SWORD : Material.LEATHER_CHESTPLATE;
            String contextKey = wielded ? "gui.value-editor.context-wielded" : "gui.value-editor.context-worn";
            ItemStack contextButton = named(contextMaterial, messages.render(locale, contextKey));
            ItemMeta contextMeta = contextButton.getItemMeta();
            contextMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-toggle-context")));
            contextButton.setItemMeta(contextMeta);
            inventory.setItem(CONTEXT_TOGGLE_SLOT, contextButton);
        }

        ItemStack typeValue = named(Material.WRITABLE_BOOK, messages.render(locale, "gui.value-editor.type-value"));
        ItemMeta typeValueMeta = typeValue.getItemMeta();
        typeValueMeta.lore(List.of(messages.render(locale, "gui.value-editor.hint-type-value")));
        typeValue.setItemMeta(typeValueMeta);
        inventory.setItem(TYPE_VALUE_SLOT, typeValue);

        inventory.setItem(BACK_SLOT, named(Material.ARROW, messages.render(locale, "gui.back")));
        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, messages.render(locale, "gui.close")));
    }

    /** {@code currentValue} goes in the lore (not just the center VALUE_SLOT icon) so a player can see where they're starting from without moving their cursor off whichever +/- button they're about to click. */
    private static ItemStack stepButton(Messages messages, Locale locale, boolean increase, String amount, double currentValue) {
        String key = increase ? "gui.value-editor.increase" : "gui.value-editor.decrease";
        ItemStack button = named(increase ? Material.LIME_DYE : Material.RED_DYE,
                messages.render(locale, key, Placeholder.unparsed("amount", amount)));
        ItemMeta meta = button.getItemMeta();
        meta.lore(List.of(messages.render(locale, "gui.value-editor.current-value",
                Placeholder.unparsed("value", formatAmount(currentValue)))));
        button.setItemMeta(meta);
        return button;
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
                if (!holder.kind().hasVisibility()) {
                    return;
                }
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
            case CONTEXT_TOGGLE_SLOT -> {
                if (!holder.kind().hasContext()) {
                    return;
                }
                String[] parts = holder.entryId().split("\\|", 2);
                String damageTypeKey = parts[0];
                ModifierContext current = ModifierContext.valueOf(parts[1]);
                ModifierContext flipped = current == ModifierContext.WIELDED ? ModifierContext.WORN : ModifierContext.WIELDED;
                boolean collision = plugin.getItemTemplateService().damageContributions(holder.templateKey()).stream()
                        .anyMatch(c -> c.damageTypeKey().equals(damageTypeKey) && c.context() == flipped);
                if (collision) {
                    // The other context is already a separate contribution for this type - no
                    // silent overwrite, same "no-op at the boundary" convention as everywhere else
                    // in this GUI (e.g. LoreOrderMenu's no-wraparound edges).
                    return;
                }
                CurrentState state = currentState(plugin, holder);
                plugin.getItemTemplateService().moveDamageContributionContext(holder.templateKey(), damageTypeKey, current, flipped,
                        state.value(), state.mode(), state.visible());
                ValueEditorMenu.open(plugin, player, holder.templateKey(), holder.kind(), damageTypeKey + "|" + flipped.name());
            }
            case TYPE_VALUE_SLOT -> promptForValue(plugin, player, holder);
            case BACK_SLOT -> ItemEditorMenu.open(plugin, player, holder.templateKey(), holder.kind().returnTab());
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
        }
    }

    /**
     * Lets the admin type an exact value in chat instead of clicking +/- buttons - reuses {@link
     * ItemEditorListener}'s existing {@code AsyncChatEvent}-based prompt flow (chosen there
     * specifically because it's the one chat hook the server's CMI plugin doesn't intercept),
     * same convention as {@link ArmorClassMenu}'s percent prompt. Only the numeric value changes;
     * {@code visible}/{@code mode} stay whatever they already were.
     */
    private static void promptForValue(PurrtechPVE plugin, Player player, ValueEditorHolder holder) {
        Locale locale = player.locale();
        Messages messages = plugin.getMessages();
        CurrentState state = currentState(plugin, holder);
        player.closeInventory();
        player.sendMessage(messages.render(locale, "gui.value-editor.prompt-set"));
        player.sendMessage(messages.render(locale, "gui.prompt.cancel-hint"));
        plugin.getItemEditorListener().awaitInput(player, (p, rawInput) -> {
            if (isCancel(rawInput)) {
                p.sendMessage(messages.render(locale, "gui.prompt.cancelled"));
                open(plugin, p, holder.templateKey(), holder.kind(), holder.entryId());
                return;
            }
            Double newValue = parseDouble(rawInput.trim());
            if (newValue == null) {
                p.sendMessage(messages.render(locale, "gui.prompt.invalid-number"));
                open(plugin, p, holder.templateKey(), holder.kind(), holder.entryId());
                return;
            }
            applyValue(plugin, holder, newValue, state.visible(), state.mode());
            p.sendMessage(messages.render(locale, "gui.prompt.done"));
            open(plugin, p, holder.templateKey(), holder.kind(), holder.entryId());
        });
    }

    private static boolean isCancel(String rawInput) {
        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("zrusit") || normalized.equals("zrušit") || normalized.equals("cancel");
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
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

    /**
     * {@code mode} is meaningless outside {@link ValueEditorKind#BLEED_DAMAGE}/{@link
     * ValueEditorKind#DAMAGE} - always {@code DamageMode.FLAT} elsewhere. {@code context} is
     * meaningless outside {@link ValueEditorKind#DAMAGE} - always {@code ModifierContext.WIELDED}
     * elsewhere, ignored by every other kind.
     */
    private record CurrentState(double value, boolean visible, DamageMode mode, ModifierContext context) {
    }

    private static CurrentState currentState(PurrtechPVE plugin, ValueEditorHolder holder) {
        ItemTemplateService service = plugin.getItemTemplateService();
        String key = holder.templateKey();
        return switch (holder.kind()) {
            case RESIST -> service.typeModifiers(key).stream()
                    .filter(m -> m.damageTypeKey().equals(holder.entryId())).findFirst()
                    .map(m -> new CurrentState(m.percent(), m.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case ARMOR_PENETRATION -> service.armorPenetration(key).stream()
                    .filter(p -> p.armorClass() == ArmorClass.valueOf(holder.entryId())).findFirst()
                    .map(p -> new CurrentState(p.amount(), p.visible(), p.mode(), ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case ATTRIBUTE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                Attribute attribute = Attribute.valueOf(parts[0]);
                String attrSlot = parts[1];
                yield service.attributeModifiers(key).stream()
                        .filter(a -> a.attribute() == attribute && a.slot().equals(attrSlot)).findFirst()
                        .map(a -> new CurrentState(a.amount(), a.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                        .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            }
            case DAMAGE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                String damageTypeKey = parts[0];
                ModifierContext context = ModifierContext.valueOf(parts[1]);
                yield service.damageContributions(key).stream()
                        .filter(c -> c.damageTypeKey().equals(damageTypeKey) && c.context() == context).findFirst()
                        .map(c -> new CurrentState(c.amount(), c.visible(), c.mode(), context))
                        .orElse(new CurrentState(0, true, DamageMode.FLAT, context));
            }
            case BLEED_CHANCE -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.chancePercent(), b.visible(), b.mode(), ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case BLEED_DURATION -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.durationSeconds(), b.visible(), b.mode(), ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case BLEED_DAMAGE -> service.bleedEffect(key)
                    .map(b -> new CurrentState(b.damageAmount(), b.visible(), b.mode(), ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case CRIT_CHANCE -> service.criticalEffect(key)
                    .map(c -> new CurrentState(c.chancePercent(), c.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case CRIT_BONUS -> service.criticalEffect(key)
                    .map(c -> new CurrentState(c.bonusDamagePercent(), c.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case STUN_CHANCE -> service.stunEffect(key)
                    .map(s -> new CurrentState(s.chancePercent(), s.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case STUN_DURATION -> service.stunEffect(key)
                    .map(s -> new CurrentState(s.durationSeconds(), s.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case REFLECT_CHANCE -> service.reflectEffect(key)
                    .map(r -> new CurrentState(r.chancePercent(), r.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case REFLECT_PERCENT -> service.reflectEffect(key)
                    .map(r -> new CurrentState(r.reflectPercent(), r.visible(), DamageMode.FLAT, ModifierContext.WIELDED))
                    .orElse(new CurrentState(0, true, DamageMode.FLAT, ModifierContext.WIELDED));
            case ARMOR_CLASS_AMOUNT -> new CurrentState(
                    service.findByKey(key).orElseThrow().armorAmount(), true, DamageMode.FLAT, ModifierContext.WIELDED);
            case STUN_RESIST_PERCENT -> new CurrentState(
                    service.findByKey(key).orElseThrow().stunResistPercent(), true, DamageMode.FLAT, ModifierContext.WIELDED);
            case CRIT_RESIST_PERCENT -> new CurrentState(
                    service.findByKey(key).orElseThrow().critResistPercent(), true, DamageMode.FLAT, ModifierContext.WIELDED);
            case PASSIVE_REFLECT_PERCENT -> new CurrentState(
                    service.findByKey(key).orElseThrow().passiveReflectPercent(), true, DamageMode.FLAT, ModifierContext.WIELDED);
            case MOB_DROP_AMOUNT -> new CurrentState(
                    mobDrop(plugin, key, holder.entryId()).map(MobDropEntry::amount).orElse(1),
                    true, DamageMode.FLAT, ModifierContext.WIELDED);
            case MOB_DROP_CHANCE -> new CurrentState(
                    mobDrop(plugin, key, holder.entryId()).map(MobDropEntry::chancePercent).orElse(100.0),
                    true, DamageMode.FLAT, ModifierContext.WIELDED);
        };
    }

    private static Optional<MobDropEntry> mobDrop(PurrtechPVE plugin, String templateKey, String mobType) {
        UUID templateId = plugin.getItemTemplateService().findByKey(templateKey).orElseThrow().id();
        return plugin.getMobDropRepository().find(mobType, templateId);
    }

    /** {@code mode} only actually matters for {@link ValueEditorKind#BLEED_DAMAGE}/{@link ValueEditorKind#DAMAGE} - every other kind's {@code setXxx} call just ignores/doesn't take one. */
    private static void applyValue(PurrtechPVE plugin, ValueEditorHolder holder, double newValue, boolean visible, DamageMode mode) {
        ItemTemplateService service = plugin.getItemTemplateService();
        String key = holder.templateKey();
        switch (holder.kind()) {
            case RESIST -> service.setTypeModifier(key, holder.entryId(), newValue, visible);
            case ARMOR_PENETRATION -> service.setArmorPenetration(key, ArmorClass.valueOf(holder.entryId()), newValue, mode, visible);
            case ATTRIBUTE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                Attribute attribute = Attribute.valueOf(parts[0]);
                String attrSlot = parts[1];
                AttributeModifierEntry current = service.attributeModifiers(key).stream()
                        .filter(a -> a.attribute() == attribute && a.slot().equals(attrSlot)).findFirst().orElseThrow();
                service.setAttributeModifier(key, attribute, newValue, current.operation(), attrSlot, visible);
            }
            case DAMAGE -> {
                String[] parts = holder.entryId().split("\\|", 2);
                String damageTypeKey = parts[0];
                ModifierContext context = ModifierContext.valueOf(parts[1]);
                service.setDamageContribution(key, damageTypeKey, newValue, mode, context, visible);
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
            case STUN_CHANCE -> {
                StunEffect current = service.stunEffect(key).orElse(new StunEffect(0, 0, true));
                service.setStunEffect(key, newValue, current.durationSeconds(), visible);
            }
            case STUN_DURATION -> {
                StunEffect current = service.stunEffect(key).orElse(new StunEffect(0, 0, true));
                service.setStunEffect(key, current.chancePercent(), newValue, visible);
            }
            case REFLECT_CHANCE -> {
                ReflectEffect current = service.reflectEffect(key).orElse(new ReflectEffect(0, 0, true));
                service.setReflectEffect(key, newValue, current.reflectPercent(), visible);
            }
            case REFLECT_PERCENT -> {
                ReflectEffect current = service.reflectEffect(key).orElse(new ReflectEffect(0, 0, true));
                service.setReflectEffect(key, current.chancePercent(), newValue, visible);
            }
            case ARMOR_CLASS_AMOUNT -> service.setArmorAmount(key, newValue);
            case STUN_RESIST_PERCENT -> service.setStunResistPercent(key, newValue);
            case CRIT_RESIST_PERCENT -> service.setCritResistPercent(key, newValue);
            case PASSIVE_REFLECT_PERCENT -> service.setPassiveReflectPercent(key, newValue);
            case MOB_DROP_AMOUNT -> {
                UUID templateId = service.findByKey(key).orElseThrow().id();
                double chance = plugin.getMobDropRepository().find(holder.entryId(), templateId)
                        .map(MobDropEntry::chancePercent).orElse(100.0);
                // A drop with nothing left to drop is meaningless - floor at 1 rather than letting
                // the DEC buttons walk it down to 0 (or negative) and silently stop dropping anything.
                plugin.getMobDropRepository().set(holder.entryId(), templateId, Math.max(1, (int) Math.round(newValue)), chance);
            }
            case MOB_DROP_CHANCE -> {
                UUID templateId = service.findByKey(key).orElseThrow().id();
                int amount = plugin.getMobDropRepository().find(holder.entryId(), templateId)
                        .map(MobDropEntry::amount).orElse(1);
                // Clamped to a real percentage - the +/-10 buttons would otherwise walk this past
                // 100% (a no-op chance-wise but confusing to display) or below 0%.
                double clamped = Math.max(0.0, Math.min(100.0, newValue));
                plugin.getMobDropRepository().set(holder.entryId(), templateId, amount, clamped);
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
