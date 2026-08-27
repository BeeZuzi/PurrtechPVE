package eu.purrtech.purrtechPVE.damage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure calculation: apply the defender's resistance/weakness to each
 * already-typed damage bucket independently, sum back into a single final
 * number. No Bukkit dependency - kept testable without a server.
 *
 * <p>Splitting a weapon's raw damage into typed buckets (percent-of-total
 * WIELDED contributions) and layering flat/percent WORN bonuses on top is
 * the caller's job (see {@code combat.EquipmentResolver}) - this class only
 * ever sees the resulting absolute per-type amounts, so it doesn't need to
 * know FLAT from PERCENT_OF_TOTAL.
 */
public final class DamagePipeline {

    /** Clamp bounds so no combination of gear can make a target immortal or amplify damage without limit. */
    public static final double MIN_RESIST_PERCENT = -200.0;
    public static final double MAX_RESIST_PERCENT = 95.0;

    private DamagePipeline() {
    }

    /**
     * @param rawDamage           vanilla/MythicMobs damage after enchants, potion effects etc. - untouched by this pipeline
     * @param typedDamage         absolute damage amount per damage type key (e.g. 6.0 slashing + 4.0 fire for a 10-damage
     *                            weapon split 60/40, or amounts exceeding rawDamage's sum when trinket bonuses add on top);
     *                            empty/null falls back to the full rawDamage as 100% {@link DamageTypeRegistry#FALLBACK_PHYSICAL}
     * @param resistPercentByType defender's summed resistance (positive) or weakness (negative) per damage type key,
     *                            from worn armor + trinkets (+ mob damage profile); missing keys are treated as 0%
     */
    public static double apply(double rawDamage, Map<String, Double> typedDamage, Map<String, Double> resistPercentByType) {
        return applyDetailed(rawDamage, typedDamage, resistPercentByType).total();
    }

    /** Same calculation as {@link #apply}, but also exposes the post-resist amount per type - e.g. for combat feedback UI. */
    public static Result applyDetailed(double rawDamage, Map<String, Double> typedDamage, Map<String, Double> resistPercentByType) {
        if (rawDamage <= 0) {
            return new Result(rawDamage, Map.of());
        }

        Map<String, Double> typed = normalize(typedDamage, rawDamage);
        Map<String, Double> resists = resistPercentByType == null ? Map.of() : resistPercentByType;

        Map<String, Double> perType = new LinkedHashMap<>();
        double total = 0.0;
        for (Map.Entry<String, Double> bucket : typed.entrySet()) {
            double resistPercent = clamp(resists.getOrDefault(bucket.getKey(), 0.0));
            double finalAmount = bucket.getValue() * (1 - resistPercent / 100.0);
            perType.merge(bucket.getKey(), finalAmount, Double::sum);
            total += finalAmount;
        }
        return new Result(Math.max(0.0, total), perType);
    }

    private static Map<String, Double> normalize(Map<String, Double> typedDamage, double rawDamage) {
        if (typedDamage == null || typedDamage.isEmpty()) {
            return Map.of(DamageTypeRegistry.FALLBACK_PHYSICAL, rawDamage);
        }
        return typedDamage;
    }

    public static double clamp(double resistPercent) {
        return Math.max(MIN_RESIST_PERCENT, Math.min(MAX_RESIST_PERCENT, resistPercent));
    }

    /** @param perType post-resist damage amount per damage type key - sums to {@code total} (barring the floor-at-0 on total). */
    public record Result(double total, Map<String, Double> perType) {
    }
}
