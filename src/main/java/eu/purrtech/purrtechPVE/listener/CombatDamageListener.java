package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.combat.BleedManager;
import eu.purrtech.purrtechPVE.combat.CombatKind;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.combat.DpsTracker;
import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.combat.WorldToggleEvaluator;
import eu.purrtech.purrtechPVE.config.CombatFeedbackSettings;
import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import net.kyori.adventure.text.Component;
import eu.purrtech.purrtechPVE.damage.DamagePipeline;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageMode;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wires the custom damage pipeline into vanilla combat between players and
 * players/vanilla mobs: world/PvP/PvE toggle gating, then {@link
 * EquipmentResolver} reads the attacker's/defender's actual equipped item
 * templates (both are just {@link LivingEntity} here, so this already works
 * for vanilla mobs - no MythicMobs-specific code needed for the split/resist
 * math itself) and feeds the result through {@link DamagePipeline}. Whichever
 * side is a {@link Player} gets an action bar breakdown of the hit via
 * {@link DamageFeedback} - attacker sees what they dealt, defender sees what
 * they took, same numbers either way. MythicMobs-specific hooking (its own
 * damage event for skill-based damage, mob damage profiles, detecting
 * MythicMobs-equipped items) is Fáze 4.
 *
 * <p>Critical hits and bleed are both rolled off the attacker's wielded
 * weapon's {@link CriticalEffect}/{@link BleedEffect}, independently of
 * each other, and only once every field either needs is actually set (see
 * their {@code isComplete()}). A crit multiplies the fully-resolved total
 * (and the action bar breakdown shown, scaled the same way, so the numbers
 * add up) - same convention as vanilla's own sword crit. A successful bleed
 * roll hands off to {@link BleedManager}, which owns the actual over-time
 * ticking; this class only computes the per-tick damage (the weapon's own
 * {@code damageAmount}/{@code mode}, same shape as a normal {@code
 * DamageContribution}, split evenly across however many ticks fit the
 * weapon's configured duration).
 *
 * <p>{@code combatFeedbackSettings.effectivenessColors()} (see {@code config.yml}) switches the
 * per-type numbers from a flat attacker/defender color to yellow/white/gray by how effective the
 * hit was against the target - same {@code resistance} map already computed above, just also
 * handed to {@link DamageFeedback} instead of only feeding {@link DamagePipeline}. {@link
 * DpsTracker} rides the attacker's own action-bar message: every hit records its final dealt
 * damage regardless, and a player who's toggled {@code /pve dps} on gets their rolling DPS
 * appended to that same message.
 */
public final class CombatDamageListener implements Listener {

    private final WorldToggleSettings worldToggles;
    private final EquipmentResolver equipmentResolver;
    private final DamageTypeRegistry damageTypeRegistry;
    private final BleedManager bleedManager;
    private final CombatFeedbackSettings combatFeedbackSettings;
    private final DpsTracker dpsTracker;

    public CombatDamageListener(WorldToggleSettings worldToggles, EquipmentResolver equipmentResolver,
                                 DamageTypeRegistry damageTypeRegistry, BleedManager bleedManager,
                                 CombatFeedbackSettings combatFeedbackSettings, DpsTracker dpsTracker) {
        this.worldToggles = worldToggles;
        this.equipmentResolver = equipmentResolver;
        this.damageTypeRegistry = damageTypeRegistry;
        this.bleedManager = bleedManager;
        this.combatFeedbackSettings = combatFeedbackSettings;
        this.dpsTracker = dpsTracker;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }
        LivingEntity attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        CombatKind kind = (attacker instanceof Player && defender instanceof Player) ? CombatKind.PVP : CombatKind.PVE;
        if (!(attacker instanceof Player) && !(defender instanceof Player)) {
            // neither side is a player - out of scope for this plugin's toggles
            return;
        }

        String worldName = defender.getWorld().getName();
        if (!WorldToggleEvaluator.isActive(worldToggles, worldName, kind)) {
            return;
        }

        double rawDamage = event.getDamage();
        Map<String, Double> typedDamage = equipmentResolver.resolveOutgoingTypedDamage(attacker, rawDamage);
        Map<String, Double> resistance = equipmentResolver.resolveResistance(attacker, defender);
        DamagePipeline.Result result = DamagePipeline.applyDetailed(rawDamage, typedDamage, resistance);

        // Critical hits multiply the fully-resolved total - same convention as vanilla's own sword
        // crit - not any one typed bucket, so the per-type action bar breakdown below is scaled by
        // the same factor to keep the numbers shown adding up to what's actually dealt.
        Optional<CriticalEffect> critical = equipmentResolver.resolveCriticalEffect(attacker);
        boolean isCritical = critical.isPresent() && critical.get().isComplete()
                && ThreadLocalRandom.current().nextDouble(100) < critical.get().chancePercent();
        double total = result.total();
        Map<String, Double> perTypeForDisplay = result.perType();
        if (isCritical) {
            double critFactor = 1 + critical.get().bonusDamagePercent() / 100.0;
            total *= critFactor;
            Map<String, Double> scaled = new HashMap<>();
            result.perType().forEach((type, amount) -> scaled.put(type, amount * critFactor));
            perTypeForDisplay = scaled;
        }
        event.setDamage(total);

        // Bleed: rolled independently of crit, off the same wielded weapon, only once chance/
        // duration/damage are ALL set (see BleedEffect.isComplete()) - a half-configured bleed
        // (e.g. only chance set so far while an admin is still dialing in duration/damage via
        // ValueEditorMenu's one-field-at-a-time +/- buttons) simply never rolls. damageAmount/
        // mode work exactly like a normal DamageContribution's amount/mode (flat number, or a
        // percent of the raw hit that triggered it) - the total is then spread evenly across
        // however many ticks fit the duration, same cadence as before ("bleed" DamageType's own
        // dotPeriodTicks). Ticks apply later via BleedManager, resolved against the target's
        // CURRENT bleed resistance at each tick, not frozen at this moment - see that class's javadoc.
        Optional<BleedEffect> bleed = equipmentResolver.resolveBleedEffect(attacker);
        if (bleed.isPresent() && bleed.get().isComplete() && ThreadLocalRandom.current().nextDouble(100) < bleed.get().chancePercent()) {
            damageTypeRegistry.find("bleed").ifPresent(bleedType -> {
                BleedEffect effect = bleed.get();
                double totalBleedDamage = effect.mode() == DamageMode.PERCENT_OF_TOTAL
                        ? rawDamage * effect.damageAmount() / 100.0
                        : effect.damageAmount();
                int totalTicks = (int) Math.ceil(effect.durationSeconds() * 20.0 / bleedType.dotPeriodTicks());
                double tickDamage = totalTicks > 0 ? totalBleedDamage / totalTicks : 0;
                bleedManager.apply(defender, tickDamage, totalTicks);
            });
        }

        boolean effectivenessColors = combatFeedbackSettings.effectivenessColors();
        if (defender instanceof Player defenderPlayer) {
            defenderPlayer.sendActionBar(DamageFeedback.render(perTypeForDisplay, damageTypeRegistry, NamedTextColor.RED,
                    isCritical, resistance, effectivenessColors));
        }
        if (attacker instanceof Player attackerPlayer) {
            Component feedback = DamageFeedback.render(perTypeForDisplay, damageTypeRegistry, NamedTextColor.YELLOW,
                    isCritical, resistance, effectivenessColors);
            dpsTracker.record(attackerPlayer.getUniqueId(), total);
            if (dpsTracker.isEnabled(attackerPlayer.getUniqueId())) {
                double dps = dpsTracker.currentDps(attackerPlayer.getUniqueId());
                feedback = feedback.append(Component.text("  DPS: " + DamageFeedback.formatAmount(dps), NamedTextColor.AQUA));
            }
            attackerPlayer.sendActionBar(feedback);
        }
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity livingDamager) {
            return livingDamager;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity livingShooter) {
                return livingShooter;
            }
        }
        return null;
    }
}
