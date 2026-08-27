package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Map;

/** Renders a per-damage-type breakdown (icon + amount, e.g. "❄ 3  ♨ 1.5") for action bar combat feedback. */
public final class DamageFeedback {

    private DamageFeedback() {
    }

    public static Component render(Map<String, Double> perType, DamageTypeRegistry registry, NamedTextColor color) {
        // Deliberately built via Component.append(), not a TextComponent.Builder - a production
        // server crashed with NoSuchMethodError on TextComponent$Builder.build() because its
        // bundled Adventure jar (Leaf 1.21.11) resolves that covariant-return bridge method
        // differently than the one this plugin was compiled against (a newer Paper's Adventure).
        // Component.append(Component) is a much older, stable method on Component itself, so
        // there's no builder/bridge-method resolution to go wrong here.
        Component result = Component.empty();
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
            result = result.append(Component.text(icon + " " + formatAmount(entry.getValue()), color));
        }
        return result;
    }

    private static String formatAmount(double amount) {
        double rounded = Math.round(amount * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
