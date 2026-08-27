package eu.purrtech.purrtechPVE.valhalla;

import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads ValhallaMMO's own item-stat encoding straight off an {@link
 * ItemStack}'s PersistentDataContainer - no dependency on ValhallaMMO's
 * plugin/API at all (it isn't even installed on this server), just the
 * plain PDC string format its {@code ItemAttributesRegistry} writes, read
 * with vanilla Bukkit API. Verified against ValhallaMMO's own source
 * (github.com/Athlaeos/ValhallaMMO, {@code item/ItemAttributesRegistry.java})
 * rather than guessed: the plugin's own {@code NamespacedKey(plugin, "...")}
 * lowercases its name to {@code valhallammo}, and every custom stat on an
 * item lives in ONE string PDC entry ({@code default_stats}, falling back to
 * {@code actual_stats} if that's the only one present) shaped like
 * {@code ATTRIBUTE:value:OPERATION:hidden;ATTRIBUTE2:value2:...}.
 *
 * <p>Only ValhallaMMO's elemental "extra damage" and "resistance" attributes
 * have a clean 1:1 mapping onto our {@link DamageContribution}/{@link
 * TypeModifier} model - their damage-type *multiplier* stats (e.g. {@code
 * DAMAGE_FIRE}) and non-elemental mechanics (crit chance, life steal, bleed
 * chance...) don't have an equivalent here yet (no generic attribute system,
 * see Fáze 6's "Attributes tab" gap), so those come back in {@code
 * ImportResult.skipped} instead of being silently dropped.
 */
public final class ValhallaMmoImporter {

    private static final NamespacedKey DEFAULT_STATS = new NamespacedKey("valhallammo", "default_stats");
    private static final NamespacedKey ACTUAL_STATS = new NamespacedKey("valhallammo", "actual_stats");

    /** ValhallaMMO "extra X damage" attribute -> our damage type key. Flat, dealt on every hit. */
    private static final Map<String, String> EXTRA_DAMAGE_TO_TYPE = Map.ofEntries(
            Map.entry("EXTRA_FIRE_DAMAGE", "fire"),
            Map.entry("EXTRA_EXPLOSION_DAMAGE", "explosive"),
            Map.entry("EXTRA_POISON_DAMAGE", "poison"),
            Map.entry("EXTRA_MAGIC_DAMAGE", "magic"),
            Map.entry("EXTRA_BLUDGEONING_DAMAGE", "blunt"),
            Map.entry("EXTRA_LIGHTNING_DAMAGE", "lightning"),
            Map.entry("EXTRA_FREEZING_DAMAGE", "frozen"),
            Map.entry("EXTRA_RADIANT_DAMAGE", "radiant"),
            Map.entry("EXTRA_NECROTIC_DAMAGE", "necrotic")
    );

    /** ValhallaMMO "X resistance" attribute -> our damage type key. Stored as a fraction (e.g. 0.2 = 20%), we store percent. */
    private static final Map<String, String> RESISTANCE_TO_TYPE = Map.ofEntries(
            Map.entry("FIRE_RESISTANCE", "fire"),
            Map.entry("EXPLOSION_RESISTANCE", "explosive"),
            Map.entry("POISON_RESISTANCE", "poison"),
            Map.entry("MAGIC_RESISTANCE", "magic"),
            Map.entry("LIGHTNING_RESISTANCE", "lightning"),
            Map.entry("FREEZING_RESISTANCE", "frozen"),
            Map.entry("RADIANT_RESISTANCE", "radiant"),
            Map.entry("NECROTIC_RESISTANCE", "necrotic"),
            Map.entry("MELEE_RESISTANCE", "physical")
    );

    private ValhallaMmoImporter() {
    }

    public static Optional<String> readRawStats(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        String defaultStats = pdc.get(DEFAULT_STATS, PersistentDataType.STRING);
        if (defaultStats != null && !defaultStats.isBlank()) {
            return Optional.of(defaultStats);
        }
        return Optional.ofNullable(pdc.get(ACTUAL_STATS, PersistentDataType.STRING));
    }

    public record ImportResult(List<DamageContribution> contributions, List<TypeModifier> modifiers, List<String> skipped) {
    }

    public static ImportResult parse(String raw) {
        Map<String, Double> attributes = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String entry : raw.split(";")) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] fields = entry.split(":");
                if (fields.length < 2) {
                    continue;
                }
                Double value = parseDouble(fields[1]);
                if (value == null) {
                    continue;
                }
                attributes.put(fields[0], value);
            }
        }
        return fromAttributes(attributes);
    }

    /**
     * Same attribute -> damage type/resistance mapping as {@link #parse(String)}, but starting
     * from an already-decoded attribute/value map instead of the raw {@code "ATTR:value:OP:hidden;..."}
     * PDC string - shared with {@link ValhallaMmoBulkImporter}, which reads its attribute/value
     * pairs out of ValhallaMMO's {@code items.json} instead of a held item's PDC.
     */
    public static ImportResult fromAttributes(Map<String, Double> attributes) {
        List<DamageContribution> contributions = new ArrayList<>();
        List<TypeModifier> modifiers = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            String attribute = entry.getKey();
            double value = entry.getValue();

            String damageType = EXTRA_DAMAGE_TO_TYPE.get(attribute);
            if (damageType != null) {
                contributions.add(new DamageContribution(damageType, value, DamageMode.FLAT, ModifierContext.WIELDED));
                continue;
            }
            String resistType = RESISTANCE_TO_TYPE.get(attribute);
            if (resistType != null) {
                modifiers.add(new TypeModifier(resistType, value * 100.0));
                continue;
            }
            skipped.add(attribute);
        }
        return new ImportResult(contributions, modifiers, skipped);
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
