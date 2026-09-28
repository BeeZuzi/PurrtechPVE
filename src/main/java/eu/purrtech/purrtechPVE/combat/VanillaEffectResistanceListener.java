package eu.purrtech.purrtechPVE.combat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes this plugin's own damage-type resistance/weakness percentages ("armor class"/mob
 * resistance profiles, the same numbers {@link EquipmentResolver#resolveResistance} feeds into
 * weapon-hit math) also apply to the handful of vanilla status mechanics that already have a
 * direct counterpart in the {@code damage} registry - poison, wither, fire and freezing - so a
 * mob/player marked weak or resistant to, say, "poison" behaves consistently whether that damage
 * comes from a custom item's DoT or from an ordinary splash potion. Every lookup goes through
 * {@code resolveResistance(null, entity)} (no attacker weapon in context here, so armor
 * penetration is simply skipped - same convention {@code BleedManager} already uses for its own
 * DoT ticks) and applies regardless of source (self-drunk potion, dispenser, mob attack, ...).
 *
 * <p>Poison/wither ({@link #onPotionEffect}) scale the effect's AMPLIFIER (its strength level) -
 * cancelling the original event and reapplying a re-leveled copy, which is safe from infinite
 * recursion because that reapplication fires its own event tagged {@link
 * EntityPotionEffectEvent.Cause#PLUGIN}, explicitly skipped at the top.
 *
 * <p>Fire ({@link #onCombust}) scales {@link EntityCombustEvent}'s duration directly - simpler,
 * since Bukkit only ever widens an already-longer burn via {@code setDuration} (see its javadoc),
 * so a positive "fire" resist shortens a single fresh ignition but can't cut short an already-
 * longer burn already in progress (e.g. continuously standing in fire) - a real limitation of that
 * API, not a bug here.
 *
 * <p>Freezing ({@link #tickFreeze}) has no equivalent vanilla event at all (no {@code
 * EntityFreezeEvent} exists), so it's driven by a periodic poll instead (see {@code
 * PurrtechPVE#onEnable}): each pass compares every loaded {@link LivingEntity}'s current {@code
 * getFreezeTicks()} against its value at the last poll and re-scales that delta by the entity's
 * "frozen" resist before writing it back - close enough to real-time at a few-tick poll interval,
 * not frame-perfect. A side effect of scaling the raw delta symmetrically: a positive "frozen"
 * resist also slightly slows how fast an entity thaws back out once it leaves powder snow, since
 * vanilla's own natural cooldown is a negative delta too - accepted as a minor simplification
 * rather than tracking freezing/thawing as two separate rates.
 */
public final class VanillaEffectResistanceListener implements Listener {

    private static final Map<PotionEffectType, String> POTION_DAMAGE_TYPES = Map.of(
            PotionEffectType.POISON, "poison",
            PotionEffectType.WITHER, "necrotic"
    );

    private final EquipmentResolver equipmentResolver;
    private final Map<UUID, Integer> lastFreezeTicks = new HashMap<>();

    public VanillaEffectResistanceListener(EquipmentResolver equipmentResolver) {
        this.equipmentResolver = equipmentResolver;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
            return;
        }
        String damageTypeKey = POTION_DAMAGE_TYPES.get(event.getModifiedType());
        PotionEffect newEffect = event.getNewEffect();
        if (damageTypeKey == null || newEffect == null || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        double resistPercent = equipmentResolver.resolveResistance(null, living).getOrDefault(damageTypeKey, 0.0);
        if (resistPercent == 0) {
            return;
        }
        Integer scaledAmplifier = scaledAmplifier(newEffect.getAmplifier(), resistPercent);
        event.setCancelled(true);
        if (scaledAmplifier != null) {
            living.addPotionEffect(new PotionEffect(newEffect.getType(), newEffect.getDuration(), scaledAmplifier,
                    newEffect.isAmbient(), newEffect.hasParticles(), newEffect.hasIcon()));
        }
    }

    /** {@code null} means fully resisted (negative resulting level) - the effect is dropped entirely rather than applied at level 0. */
    private Integer scaledAmplifier(int amplifier, double resistPercent) {
        double power = (amplifier + 1) * (1 - resistPercent / 100.0);
        int scaled = (int) Math.round(power) - 1;
        return scaled < 0 ? null : scaled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        double resistPercent = equipmentResolver.resolveResistance(null, living).getOrDefault("fire", 0.0);
        if (resistPercent == 0) {
            return;
        }
        float scaled = (float) (event.getDuration() * (1 - resistPercent / 100.0));
        if (scaled <= 0) {
            event.setCancelled(true);
            return;
        }
        event.setDuration(scaled);
    }

    /** Called on a repeating scheduler task - see the class javadoc's freezing paragraph. */
    public void tickFreeze() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                tickFreeze(living);
            }
        }
    }

    private void tickFreeze(LivingEntity living) {
        int current = living.getFreezeTicks();
        UUID id = living.getUniqueId();
        if (current <= 0) {
            lastFreezeTicks.remove(id);
            return;
        }
        Integer previous = lastFreezeTicks.get(id);
        if (previous == null) {
            lastFreezeTicks.put(id, current);
            return;
        }
        int delta = current - previous;
        if (delta == 0) {
            return;
        }
        double resistPercent = equipmentResolver.resolveResistance(null, living).getOrDefault("frozen", 0.0);
        if (resistPercent == 0) {
            lastFreezeTicks.put(id, current);
            return;
        }
        int scaledDelta = (int) Math.round(delta * (1 - resistPercent / 100.0));
        int adjusted = Math.max(0, Math.min(living.getMaxFreezeTicks(), previous + scaledDelta));
        living.setFreezeTicks(adjusted);
        lastFreezeTicks.put(id, adjusted);
    }
}
