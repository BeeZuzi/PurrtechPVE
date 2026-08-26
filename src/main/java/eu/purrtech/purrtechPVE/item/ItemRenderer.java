package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.lang.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a stored {@link ItemTemplate} (+ its damage contributions/type
 * modifiers) into an actual {@link ItemStack}: display name, lore, and a
 * PersistentDataContainer tag recording which template + version this stack
 * was rendered from. That tag is the only thing carried in the item itself -
 * everything else is recomputed from the DB, which is what later phases'
 * live-sync (propagating an edited template to items already in circulation)
 * relies on.
 */
public final class ItemRenderer {

    private final Messages messages;
    private final Locale locale;
    private final DamageTypeRegistry damageTypeRegistry;
    private final NamespacedKey templateKeyPdc;
    private final NamespacedKey templateVersionPdc;

    public ItemRenderer(Plugin plugin, Messages messages, Locale locale, DamageTypeRegistry damageTypeRegistry) {
        this.messages = messages;
        this.locale = locale;
        this.damageTypeRegistry = damageTypeRegistry;
        this.templateKeyPdc = new NamespacedKey(plugin, "template_key");
        this.templateVersionPdc = new NamespacedKey(plugin, "template_version");
    }

    public NamespacedKey templateKeyPdc() {
        return templateKeyPdc;
    }

    public NamespacedKey templateVersionPdc() {
        return templateVersionPdc;
    }

    /** Renders from the template's current live data - always the newest version, used for freshly given items. */
    public ItemStack render(ItemTemplate template, List<DamageContribution> contributions, List<TypeModifier> modifiers) {
        return render(template.key(), template.version(), template.displayName(), template.baseMaterial(),
                template.customModelData(), contributions, modifiers);
    }

    /** Renders exactly as a given historical version looked - used to catch up a stack pinned behind the live version. */
    public ItemStack renderSnapshot(TemplateSnapshot snapshot) {
        return render(snapshot.templateKey(), snapshot.version(), snapshot.displayName(), snapshot.baseMaterial(),
                snapshot.customModelData(), snapshot.damageContributions(), snapshot.typeModifiers());
    }

    private ItemStack render(String key, int version, String displayName, Material baseMaterial,
                              Integer customModelData, List<DamageContribution> contributions, List<TypeModifier> modifiers) {
        ItemStack stack = new ItemStack(baseMaterial);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text(displayName).decoration(TextDecoration.ITALIC, false));
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        meta.lore(buildLore(contributions, modifiers));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(templateKeyPdc, PersistentDataType.STRING, key);
        pdc.set(templateVersionPdc, PersistentDataType.INTEGER, version);

        stack.setItemMeta(meta);
        return stack;
    }

    /** {@code templateKey}/{@code templateVersion} as stamped by {@link #render}, if the stack carries our PDC tags at all. */
    public Optional<StampedTemplate> readStamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(templateKeyPdc, PersistentDataType.STRING);
        Integer version = pdc.get(templateVersionPdc, PersistentDataType.INTEGER);
        if (key == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(new StampedTemplate(key, version));
    }

    public record StampedTemplate(String templateKey, int templateVersion) {
    }

    private List<Component> buildLore(List<DamageContribution> contributions, List<TypeModifier> modifiers) {
        List<Component> lore = new ArrayList<>();

        List<DamageContribution> wielded = contributions.stream().filter(c -> c.context() == ModifierContext.WIELDED).toList();
        List<DamageContribution> worn = contributions.stream().filter(c -> c.context() == ModifierContext.WORN).toList();

        if (!wielded.isEmpty()) {
            lore.add(messages.render(locale, "item.header.damage"));
            for (DamageContribution c : wielded) {
                lore.add(damageLine(c));
            }
        }
        if (!worn.isEmpty()) {
            lore.add(messages.render(locale, "item.header.passive"));
            for (DamageContribution c : worn) {
                lore.add(damageLine(c));
            }
        }
        if (!modifiers.isEmpty()) {
            lore.add(messages.render(locale, "item.header.resist"));
            for (TypeModifier m : modifiers) {
                lore.add(resistLine(m));
            }
        }
        return lore;
    }

    private Component damageLine(DamageContribution c) {
        String key = c.mode() == DamageMode.PERCENT_OF_TOTAL ? "item.line.damage-percent" : "item.line.damage-flat";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(c.amount())),
                Placeholder.unparsed("type", displayName(c.damageTypeKey())));
    }

    private Component resistLine(TypeModifier m) {
        String key = m.percent() >= 0 ? "item.line.resist" : "item.line.weakness";
        return messages.render(locale, key,
                Placeholder.unparsed("amount", formatAmount(Math.abs(m.percent()))),
                Placeholder.unparsed("type", displayName(m.damageTypeKey())));
    }

    private String displayName(String damageTypeKey) {
        return damageTypeRegistry.find(damageTypeKey)
                .map(DamageType::displayName)
                .orElse(damageTypeKey);
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
