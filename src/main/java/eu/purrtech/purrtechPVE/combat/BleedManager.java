package eu.purrtech.purrtechPVE.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks entities currently bleeding (see {@code BleedEffect} - rolled on a
 * successful hit in {@code CombatDamageListener}) and ticks their damage
 * over time. One repeating task (registered in {@code PurrtechPVE.onEnable}
 * at the "bleed" {@code DamageType}'s own {@code dotPeriodTicks} cadence)
 * drives every active bleed at once, rather than one timer per instance.
 *
 * <p>A fresh application on an already-bleeding target REPLACES the
 * previous one outright (not stacked, not extended) - the simplest,
 * least balance-surprising choice given no stacking behavior was
 * specified; ask if refreshing/stacking multiple bleeds is wanted instead.
 *
 * <p>Purely a runtime/in-memory concern - nothing here is persisted, so a
 * server restart simply clears every active bleed, the same as vanilla
 * potion effects would.
 *
 * <p>Ticks apply damage via {@link LivingEntity#damage(double)} (no source
 * entity), which fires a plain {@code EntityDamageEvent}, not {@code
 * EntityDamageByEntityEvent} - so {@code CombatDamageListener} never
 * re-intercepts a bleed tick as a fresh "hit" (no infinite loop, no
 * double-dipping through the normal combat pipeline for it).
 */
public final class BleedManager {

    private final Map<UUID, BleedState> active = new ConcurrentHashMap<>();

    public void apply(LivingEntity target, double tickDamage, int totalTicks) {
        if (tickDamage <= 0 || totalTicks <= 0) {
            return;
        }
        active.put(target.getUniqueId(), new BleedState(tickDamage, totalTicks));
    }

    /** Called once per "bleed" {@code DamageType}'s {@code dotPeriodTicks} by a repeating task - see {@code PurrtechPVE.onEnable}. */
    public void tick(EquipmentResolver equipmentResolver) {
        Iterator<Map.Entry<UUID, BleedState>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BleedState> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                iterator.remove();
                continue;
            }

            BleedState state = entry.getValue();
            // No specific attacker for a passive DOT tick - armor penetration doesn't apply here,
            // just the target's own current "bleed" resistance (armor/type modifiers, live at tick
            // time, not frozen at the moment the bleed was applied).
            double resistPercent = equipmentResolver.resolveResistance(null, living).getOrDefault("bleed", 0.0);
            double effective = Math.max(0, state.tickDamage * (1 - resistPercent / 100.0));
            if (effective > 0) {
                living.damage(effective);
            }

            state.remainingTicks--;
            if (state.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private static final class BleedState {
        private final double tickDamage;
        private int remainingTicks;

        BleedState(double tickDamage, int remainingTicks) {
            this.tickDamage = tickDamage;
            this.remainingTicks = remainingTicks;
        }
    }
}
