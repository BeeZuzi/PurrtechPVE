package eu.purrtech.purrtechPVE.combat;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.damage.DamagePipeline;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code /pve debug} stats readout: what the plugin currently resolves for one player - the
 * damage split their held item + worn gear would deal, their per-type resistances/weaknesses, and
 * how much their flat armor cuts physical damage. Read off the exact same {@link EquipmentResolver}
 * calls combat uses, so what it prints is what a hit would actually use (see {@code
 * CombatDamageListener}). Damage is shown for a reference hit equal to the player's current
 * {@code ATTACK_DAMAGE} attribute, since PERCENT_OF_TOTAL contributions depend on the hit size.
 */
public final class DebugStatsReporter {

    private final PurrtechPVE plugin;
    private final EquipmentResolver equipmentResolver;

    public DebugStatsReporter(PurrtechPVE plugin, EquipmentResolver equipmentResolver) {
        this.plugin = plugin;
        this.equipmentResolver = equipmentResolver;
    }

    public void send(Player player) {
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        double baseHit = attack != null ? attack.getValue() : 1.0;

        Map<String, Double> damage = new TreeMap<>(equipmentResolver.resolveOutgoingTypedDamage(player, baseHit));
        Map<String, Double> resist = new TreeMap<>(equipmentResolver.resolveResistance(null, player));
        double armorPoints = equipmentResolver.resolveArmorPoints(null, player);
        double armorCut = (1 - DamagePipeline.armorMultiplier(armorPoints)) * 100.0;

        var messages = plugin.getMessages();
        var locale = player.locale();
        player.sendMessage(messages.render(locale, "debug.stats-damage",
                Placeholder.unparsed("base", DamageFeedback.formatAmount(baseHit)),
                Placeholder.unparsed("values", join(damage, false))));
        player.sendMessage(messages.render(locale, "debug.stats-resist",
                Placeholder.unparsed("values", join(resist, true))));
        player.sendMessage(messages.render(locale, "debug.stats-armor",
                Placeholder.unparsed("points", DamageFeedback.formatAmount(armorPoints)),
                Placeholder.unparsed("cut", DamageFeedback.formatAmount(armorCut))));
    }

    /** {@code "fire 3, slashing 6.5"} or, for percents, {@code "fire +40%, frozen -25%"} - zero entries left out, {@code "-"} when nothing. */
    public static String join(Map<String, Double> values, boolean percent) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            double value = entry.getValue();
            if (value == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(entry.getKey().toLowerCase(Locale.ROOT)).append(' ');
            if (percent) {
                out.append(value > 0 ? "+" : "").append(DamageFeedback.formatAmount(value)).append('%');
            } else {
                out.append(DamageFeedback.formatAmount(value));
            }
        }
        return out.length() == 0 ? "-" : out.toString();
    }
}
