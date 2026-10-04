package eu.purrtech.purrtechPVE.mythicmobs;

import eu.purrtech.purrtechPVE.combat.SkillDamageContext;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.MobAttackDamageRepository;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * {@code pvedamage{id=<attack>;a=<fallback>}} - a MythicMobs damage mechanic whose amounts come
 * from this plugin instead of the skill file: the typed damage configured for (mob type, attack id)
 * in {@code /pve mobs} is dealt to the target through the normal combat pipeline (the target's
 * resistances, armor, crit resistance and so on all apply). If nothing is configured yet for that
 * attack, {@code a} (default 0) is dealt as the mob fallback type, so a freshly converted skill
 * still hurts until somebody tunes it in the menu.
 */
public final class PveDamageMechanic implements ITargetedEntitySkill {

    private final Plugin plugin;
    private final MythicMobsBridge bridge;
    private final MobAttackDamageRepository repository;
    private final String attackId;
    private final double fallbackAmount;

    public PveDamageMechanic(Plugin plugin, MythicMobsBridge bridge, MobAttackDamageRepository repository,
                             String attackId, double fallbackAmount) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.repository = repository;
        this.attackId = attackId;
        this.fallbackAmount = fallbackAmount;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity casterEntity = data.getCaster().getEntity().getBukkitEntity();
        if (!(target.getBukkitEntity() instanceof LivingEntity victim) || !(casterEntity instanceof LivingEntity caster)) {
            return SkillResult.INVALID_TARGET;
        }
        // Skills can run off the main thread, but both the database lookup's consumer (the combat
        // listener) and damage() itself must run on it.
        if (Bukkit.isPrimaryThread()) {
            deal(caster, victim);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> deal(caster, victim));
        }
        return SkillResult.SUCCESS;
    }

    private void deal(LivingEntity caster, LivingEntity victim) {
        if (victim.isDead()) {
            return;
        }
        String mobType = bridge.mythicMobInternalName(caster).orElse("?");
        Map<String, Double> typed = repository.findByAttack(mobType, attackId);
        if (typed.isEmpty() && fallbackAmount > 0) {
            typed = Map.of(DamageTypeRegistry.MOB_FALLBACK, fallbackAmount);
        }
        double total = typed.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return;
        }
        Map<String, Double> damage = typed;
        SkillDamageContext.run(damage, () -> victim.damage(total, caster));
    }
}
