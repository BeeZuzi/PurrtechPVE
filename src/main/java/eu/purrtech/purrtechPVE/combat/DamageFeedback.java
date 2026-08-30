package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;
import java.util.Map;

/** Renders a per-damage-type breakdown (icon + amount, e.g. "❄ 3  ♨ 1.5") for action bar combat feedback. */
public final class DamageFeedback {

    private DamageFeedback() {
    }

    /** Same as {@link #render(Map, DamageTypeRegistry, NamedTextColor, boolean)}, without a critical-hit marker. */
    public static Component render(Map<String, Double> perType, DamageTypeRegistry registry, NamedTextColor color) {
        return render(perType, registry, color, false);
    }

    public static Component render(Map<String, Double> perType, DamageTypeRegistry registry, NamedTextColor color, boolean critical) {
        return render(perType, registry, color, critical, Map.of(), false);
    }

    /**
     * Same as {@link #render(Map, DamageTypeRegistry, NamedTextColor, boolean)}, but when {@code
     * effectivenessColors} is on, each number is colored by {@code resistance}'s percent for that
     * type instead of the flat {@code color} - yellow (target is weak to it, negative percent),
     * white (normal, zero/missing), or gray (target resists it, positive percent), matching
     * {@code CombatFeedbackSettings}/{@code EquipmentResolver.resolveResistance}'s sign
     * convention. {@code color} is still used as-is when the flag is off, so an admin who hasn't
     * opted in sees exactly the same feedback as before this existed.
     */
    public static Component render(Map<String, Double> perType, DamageTypeRegistry registry, NamedTextColor color,
                                    boolean critical, Map<String, Double> resistance, boolean effectivenessColors) {
        // Deliberately built via Component.append(), not a TextComponent.Builder - a production
        // server crashed with NoSuchMethodError on TextComponent$Builder.build() because its
        // bundled Adventure jar (Leaf 1.21.11) resolves that covariant-return bridge method
        // differently than the one this plugin was compiled against (a newer Paper's Adventure).
        // Component.append(Component) is a much older, stable method on Component itself, so
        // there's no builder/bridge-method resolution to go wrong here.
        Component result = critical
                ? Component.text("CRIT! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                : Component.empty();
        boolean first = true;
        for (Map.Entry<String, Double> entry : perType.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!first) {
                result = result.append(Component.text("  "));
            }
            first = false;
            String icon = registry.find(entry.getKey()).map(DamageType::icon).orElse("?");
            NamedTextColor lineColor = effectivenessColors ? effectivenessColor(resistance.getOrDefault(entry.getKey(), 0.0)) : color;
            result = result.append(Component.text(icon + " " + formatAmount(entry.getValue()), lineColor));
        }
        return result;
    }

    /** Positive percent = target resists this type (gray), negative = target is weak to it (yellow), zero = normal (white). */
    private static NamedTextColor effectivenessColor(double resistPercent) {
        if (resistPercent > 0) {
            return NamedTextColor.GRAY;
        }
        if (resistPercent < 0) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.WHITE;
    }

    public static String formatAmount(double amount) {
        double rounded = Math.round(amount * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
