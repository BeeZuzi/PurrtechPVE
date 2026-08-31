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
import java.util.Set;

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
 * <p>Three attribute families have a mapping onto our {@link
 * DamageContribution}/{@link TypeModifier} model, all confirmed against
 * {@code ItemAttributesRegistry}'s {@code StatFormat} for each attribute
 * rather than guessed:
 * <ul>
 *     <li>{@code EXTRA_<TYPE>_DAMAGE} and the standalone {@code ARROW_DAMAGE}/
 *     {@code DAMAGE_ALL} (all {@code StatFormat.FLOAT_P2} or, for {@code
 *     DAMAGE_ALL}, deliberately treated as flat too - see its own note below)
 *     are flat, dealt on every hit -> {@link DamageContribution}. {@code
 *     BLEED_DAMAGE} used to be folded in here too, but {@code "bleed"} isn't
 *     a valid contribution type any more (see {@code BleedEffect}'s javadoc)
 *     - it comes back as {@link ImportResult#bleedDamageAmount()} instead,
 *     for the caller to set as the imported template's own bleed damage.
 *     <li>{@code <TYPE>_RESISTANCE} (including {@code DAMAGE_RESISTANCE},
 *     {@code BLEED_RESISTANCE}, {@code BLUDGEONING_RESISTANCE}, {@code
 *     PROJECTILE_RESISTANCE}) are all {@code StatFormat.PERCENTILE_BASE_1_*} -
 *     a 0-1 fraction, stored as percent here (fraction * 100) -> {@link
 *     TypeModifier}. {@code DAMAGE_RESISTANCE} has no single type of its own
 *     in ValhallaMMO (it reduces ALL incoming damage) so it fans out into one
 *     {@link TypeModifier} per damage type we know about.
 *     <li>{@code DAMAGE_<TYPE>} (fire/magic/poison/radiant/freezing/explosion/
 *     lightning/necrotic/bludgeoning) are also {@code PERCENTILE_BASE_1_*} but
 *     mean something different: "increase whatever damage of this type the
 *     item already deals by N%", not a new contribution of its own. If the
 *     item HAS an {@code EXTRA_<TYPE>_DAMAGE} (or other flat contribution of
 *     that type) to multiply, that flat amount is scaled by {@code 1+N} -
 *     same as before. If it doesn't, there's nothing to multiply, so per
 *     updated instruction the raw ValhallaMMO percentage is imported
 *     directly as our own {@link DamageMode#PERCENT_OF_TOTAL} {@link
 *     DamageContribution} for that type instead of being dropped.
 * </ul>
 *
 * <p>{@code DAMAGE_ALL} is the one exception carved out of the percentage
 * family above despite sharing its {@code StatFormat}: it has no {@code
 * EXTRA_ALL_DAMAGE} flat counterpart to multiply (there's no generic
 * "physical" flat stat in ValhallaMMO at all), so treating it as a
 * percentage would always resolve to zero and be pointless. Per explicit
 * instruction, it's imported as a flat contribution instead, into our
 * {@code physical} type (this project's generic/fallback damage bucket -
 * see {@code DamageTypeRegistry.FALLBACK_PHYSICAL}).
 *
 * <p>Everything else (crit chance/damage, stun, reflect, armor class/
 * penetration, absorption, directional protection, movement speed, and
 * target/attack-conditional bonuses like {@code DAMAGE_MELEE}/{@code
 * DAMAGE_PLAYER}/{@code VELOCITY_DAMAGE}) has no equivalent mechanic in this
 * plugin at all - those aren't damage-type-keyed stats the way everything
 * above is, they're entirely different systems this plugin doesn't
 * implement, so they come back in {@code ImportResult.skipped} rather than
 * being force-fit or silently dropped.
 */
public final class ValhallaMmoImporter {

    private static final NamespacedKey DEFAULT_STATS = new NamespacedKey("valhallammo", "default_stats");
    private static final NamespacedKey ACTUAL_STATS = new NamespacedKey("valhallammo", "actual_stats");

    private static final String DAMAGE_RESISTANCE_ATTRIBUTE = "DAMAGE_RESISTANCE";
    /** No longer routed through {@link #FLAT_DAMAGE_TO_TYPE} - {@code "bleed"} isn't a valid {@code DamageContribution} type any more, see {@code BleedEffect}'s javadoc. Surfaced as {@link ImportResult#bleedDamageAmount()} instead. */
    private static final String BLEED_DAMAGE_ATTRIBUTE = "BLEED_DAMAGE";

    /** Flat, dealt-on-every-hit ValhallaMMO attribute -> our damage type key ({@code StatFormat.FLOAT_P2}, plus DAMAGE_ALL - see class javadoc). */
    private static final Map<String, String> FLAT_DAMAGE_TO_TYPE = Map.ofEntries(
            Map.entry("EXTRA_FIRE_DAMAGE", "fire"),
            Map.entry("EXTRA_EXPLOSION_DAMAGE", "explosive"),
            Map.entry("EXTRA_POISON_DAMAGE", "poison"),
            Map.entry("EXTRA_MAGIC_DAMAGE", "magic"),
            Map.entry("EXTRA_BLUDGEONING_DAMAGE", "blunt"),
            Map.entry("EXTRA_LIGHTNING_DAMAGE", "lightning"),
            Map.entry("EXTRA_FREEZING_DAMAGE", "frozen"),
            Map.entry("EXTRA_RADIANT_DAMAGE", "radiant"),
            Map.entry("EXTRA_NECROTIC_DAMAGE", "necrotic"),
            Map.entry("ARROW_DAMAGE", "piercing"),
            Map.entry("DAMAGE_ALL", "physical")
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
            Map.entry("MELEE_RESISTANCE", "physical"),
            Map.entry("BLEED_RESISTANCE", "bleed"),
            Map.entry("BLUDGEONING_RESISTANCE", "blunt"),
            Map.entry("PROJECTILE_RESISTANCE", "piercing")
    );

    /** "Increase whatever damage of this type the item already deals by N%" - see class javadoc; multiplies FLAT_DAMAGE_TO_TYPE's result, doesn't add its own. */
    private static final Map<String, String> DAMAGE_MULTIPLIER_TO_TYPE = Map.ofEntries(
            Map.entry("DAMAGE_FIRE", "fire"),
            Map.entry("DAMAGE_BLUDGEONING", "blunt"),
            Map.entry("DAMAGE_MAGIC", "magic"),
            Map.entry("DAMAGE_POISON", "poison"),
            Map.entry("DAMAGE_RADIANT", "radiant"),
            Map.entry("DAMAGE_FREEZING", "frozen"),
            Map.entry("DAMAGE_EXPLOSION", "explosive"),
            Map.entry("DAMAGE_LIGHTNING", "lightning"),
            Map.entry("DAMAGE_NECROTIC", "necrotic")
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

    /** {@code bleedDamageAmount} is {@code null} when the source item had no {@code BLEED_DAMAGE} stat - the caller sets it as a flat {@code BleedEffect.damageAmount} (chance/duration are ValhallaMMO concepts this plugin has no equivalent import source for, so they're left at 0/incomplete until an admin fills them in - see {@code BleedEffect.isComplete()}). */
    public record ImportResult(List<DamageContribution> contributions, List<TypeModifier> modifiers, List<String> skipped, Double bleedDamageAmount) {
    }

    public static ImportResult parse(String raw, Set<String> allDamageTypeKeys) {
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
        return fromAttributes(attributes, allDamageTypeKeys);
    }

    /**
     * Same attribute -> damage type/resistance mapping as {@link #parse}, but starting from an
     * already-decoded attribute/value map instead of the raw {@code "ATTR:value:OP:hidden;..."}
     * PDC string - shared with {@link ValhallaMmoBulkImporter}, which reads its attribute/value
     * pairs out of ValhallaMMO's {@code items.json} instead of a held item's PDC.
     *
     * @param allDamageTypeKeys every damage type this plugin currently knows about (see {@code
     *                          DamageTypeRegistry.all().keySet()}) - only needed to fan {@code
     *                          DAMAGE_RESISTANCE} out into one {@link TypeModifier} per type.
     */
    public static ImportResult fromAttributes(Map<String, Double> attributes, Set<String> allDamageTypeKeys) {
        List<TypeModifier> modifiers = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, Double> flatByType = new LinkedHashMap<>();
        // DAMAGE_<TYPE> multipliers that had no matching flat contribution to scale end up here
        // instead, as a PERCENT_OF_TOTAL contribution of their own - see class javadoc.
        Map<String, Double> percentByType = new LinkedHashMap<>();
        Double bleedDamageAmount = null;

        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            String attribute = entry.getKey();
            double value = entry.getValue();

            if (BLEED_DAMAGE_ATTRIBUTE.equals(attribute)) {
                bleedDamageAmount = value;
                continue;
            }
            String flatType = FLAT_DAMAGE_TO_TYPE.get(attribute);
            if (flatType != null) {
                flatByType.merge(flatType, value, Double::sum);
                continue;
            }
            String resistType = RESISTANCE_TO_TYPE.get(attribute);
            if (resistType != null) {
                modifiers.add(new TypeModifier(resistType, value * 100.0, true));
                continue;
            }
            if (DAMAGE_RESISTANCE_ATTRIBUTE.equals(attribute)) {
                for (String typeKey : allDamageTypeKeys) {
                    modifiers.add(new TypeModifier(typeKey, value * 100.0, true));
                }
                continue;
            }
            if (DAMAGE_MULTIPLIER_TO_TYPE.containsKey(attribute)) {
                continue; // applied in the second pass below, once every flat contribution is known
            }
            skipped.add(attribute);
        }

        for (Map.Entry<String, String> entry : DAMAGE_MULTIPLIER_TO_TYPE.entrySet()) {
            Double percent = attributes.get(entry.getKey());
            if (percent == null) {
                continue;
            }
            Double existing = flatByType.get(entry.getValue());
            if (existing == null) {
                // item deals none of this type as a flat contribution - nothing to multiply, so
                // import the raw ValhallaMMO percentage directly as our own PERCENT_OF_TOTAL
                // contribution instead of dropping it (per updated instruction).
                percentByType.merge(entry.getValue(), percent * 100.0, Double::sum);
                continue;
            }
            flatByType.put(entry.getValue(), existing * (1 + percent));
        }

        List<DamageContribution> contributions = new ArrayList<>();
        for (Map.Entry<String, Double> entry : flatByType.entrySet()) {
            contributions.add(new DamageContribution(entry.getKey(), entry.getValue(), DamageMode.FLAT, ModifierContext.WIELDED, true));
        }
        for (Map.Entry<String, Double> entry : percentByType.entrySet()) {
            contributions.add(new DamageContribution(entry.getKey(), entry.getValue(), DamageMode.PERCENT_OF_TOTAL, ModifierContext.WIELDED, true));
        }
        return new ImportResult(contributions, modifiers, skipped, bleedDamageAmount);
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
