package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Map;

/** Renders a per-damage-type breakdown (icon + amount, e.g. "❄ 3  ♨ 1.5") for action bar combat feedback. */
public final class DamageFeedback {

    private DamageFeedback() {
    }

    public static Component render(Map<String, Double> perType, DamageTypeRegistry registry, NamedTextColor color) {
        TextComponent.Builder builder = Component.text();
        boolean first = true;
        for (Map.Entry<String, Double> entry : perType.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!first) {
                builder.append(Component.text("  "));
            }
            first = false;
            String icon = registry.find(entry.getKey()).map(DamageType::icon).orElse("?");
            builder.append(Component.text(icon + " " + formatAmount(entry.getValue()), color));
        }
        return builder.build();
    }

    private static String formatAmount(double amount) {
        double rounded = Math.round(amount * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
