package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.combat.CombatKind;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.combat.WorldToggleEvaluator;
import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import eu.purrtech.purrtechPVE.damage.DamagePipeline;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

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
 */
public final class CombatDamageListener implements Listener {

    private final WorldToggleSettings worldToggles;
    private final EquipmentResolver equipmentResolver;
    private final DamageTypeRegistry damageTypeRegistry;

    public CombatDamageListener(WorldToggleSettings worldToggles, EquipmentResolver equipmentResolver,
                                 DamageTypeRegistry damageTypeRegistry) {
        this.worldToggles = worldToggles;
        this.equipmentResolver = equipmentResolver;
        this.damageTypeRegistry = damageTypeRegistry;
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

        Map<String, Double> typedDamage = equipmentResolver.resolveOutgoingTypedDamage(attacker, event.getDamage());
        Map<String, Double> resistance = equipmentResolver.resolveResistance(attacker, defender);
        DamagePipeline.Result result = DamagePipeline.applyDetailed(event.getDamage(), typedDamage, resistance);
        event.setDamage(result.total());

        if (defender instanceof Player defenderPlayer) {
            defenderPlayer.sendActionBar(DamageFeedback.render(result.perType(), damageTypeRegistry, NamedTextColor.RED));
        }
        if (attacker instanceof Player attackerPlayer) {
            attackerPlayer.sendActionBar(DamageFeedback.render(result.perType(), damageTypeRegistry, NamedTextColor.YELLOW));
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
