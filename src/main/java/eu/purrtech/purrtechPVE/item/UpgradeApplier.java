package eu.purrtech.purrtechPVE.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a template version's {@link TemplateSnapshot} into the snapshot one particular item
 * effectively has: the same, plus that item's {@link ItemUpgrades}. Pure - nothing is stored - so
 * combat ({@code EquipmentResolver}) and rendering ({@code ItemSyncService}) both go through it and
 * agree on the numbers.
 */
public final class UpgradeApplier {

    private UpgradeApplier() {
    }

    public static TemplateSnapshot apply(TemplateSnapshot snapshot, ItemUpgrades upgrades) {
        if (upgrades == null || upgrades.isEmpty()) {
            return snapshot;
        }
        Map<String, Double> fx = upgrades.effects();
        return new TemplateSnapshot(snapshot.templateId(), snapshot.templateKey(), snapshot.version(), snapshot.displayName(),
                snapshot.customLore(), snapshot.hiddenHeaders(), snapshot.loreOrder(), snapshot.baseMaterial(),
                snapshot.baseItemSnapshot(), snapshot.customModelData(),
                applyDamage(snapshot.damageContributions(), upgrades.damage()),
                applyResist(snapshot.typeModifiers(), upgrades.resist()),
                snapshot.enchantments(), snapshot.armorPenetration(),
                applyBleed(snapshot.bleedEffect(), fx),
                applyCrit(snapshot.criticalEffect(), fx),
                applyStun(snapshot.stunEffect(), fx),
                applyReflect(snapshot.reflectEffect(), fx),
                snapshot.attributeModifiers(), snapshot.createdAt());
    }

    private static List<DamageContribution> applyDamage(List<DamageContribution> base, Map<String, Double> deltas) {
        List<DamageContribution> out = new ArrayList<>(base);
        deltas.forEach((key, delta) -> {
            int split = key.lastIndexOf('|');
            if (split < 0 || delta <= 0) {
                return;
            }
            String type = key.substring(0, split);
            ModifierContext context = ModifierContext.valueOf(key.substring(split + 1));
            for (int i = 0; i < out.size(); i++) {
                DamageContribution c = out.get(i);
                if (c.damageTypeKey().equals(type) && c.context() == context) {
                    out.set(i, new DamageContribution(type, c.amount() + delta, c.mode(), context, c.visible()));
                    return;
                }
            }
            out.add(new DamageContribution(type, delta, DamageMode.FLAT, context, true));
        });
        return out;
    }

    private static List<TypeModifier> applyResist(List<TypeModifier> base, Map<String, Double> deltas) {
        List<TypeModifier> out = new ArrayList<>(base);
        deltas.forEach((type, delta) -> {
            if (delta <= 0) {
                return;
            }
            for (int i = 0; i < out.size(); i++) {
                TypeModifier m = out.get(i);
                if (m.damageTypeKey().equals(type)) {
                    out.set(i, new TypeModifier(type, m.percent() + delta, m.visible()));
                    return;
                }
            }
            out.add(new TypeModifier(type, delta, true));
        });
        return out;
    }

    private static double d(Map<String, Double> fx, UpgradeEffect effect) {
        return fx.getOrDefault(effect.name(), 0.0);
    }

    private static boolean touched(Map<String, Double> fx, UpgradeEffect... effects) {
        for (UpgradeEffect effect : effects) {
            if (d(fx, effect) > 0) {
                return true;
            }
        }
        return false;
    }

    private static CriticalEffect applyCrit(CriticalEffect base, Map<String, Double> fx) {
        if (!touched(fx, UpgradeEffect.CRIT_CHANCE, UpgradeEffect.CRIT_BONUS)) {
            return base;
        }
        return new CriticalEffect((base == null ? 0 : base.chancePercent()) + d(fx, UpgradeEffect.CRIT_CHANCE),
                (base == null ? 0 : base.bonusDamagePercent()) + d(fx, UpgradeEffect.CRIT_BONUS), base == null || base.visible());
    }

    private static BleedEffect applyBleed(BleedEffect base, Map<String, Double> fx) {
        if (!touched(fx, UpgradeEffect.BLEED_CHANCE, UpgradeEffect.BLEED_DURATION, UpgradeEffect.BLEED_DAMAGE)) {
            return base;
        }
        return new BleedEffect((base == null ? 0 : base.chancePercent()) + d(fx, UpgradeEffect.BLEED_CHANCE),
                (base == null ? 0 : base.durationSeconds()) + d(fx, UpgradeEffect.BLEED_DURATION),
                (base == null ? 0 : base.damageAmount()) + d(fx, UpgradeEffect.BLEED_DAMAGE),
                base == null ? DamageMode.FLAT : base.mode(), base == null || base.visible());
    }

    private static StunEffect applyStun(StunEffect base, Map<String, Double> fx) {
        if (!touched(fx, UpgradeEffect.STUN_CHANCE, UpgradeEffect.STUN_DURATION)) {
            return base;
        }
        return new StunEffect((base == null ? 0 : base.chancePercent()) + d(fx, UpgradeEffect.STUN_CHANCE),
                (base == null ? 0 : base.durationSeconds()) + d(fx, UpgradeEffect.STUN_DURATION), base == null || base.visible());
    }

    private static ReflectEffect applyReflect(ReflectEffect base, Map<String, Double> fx) {
        if (!touched(fx, UpgradeEffect.REFLECT_CHANCE, UpgradeEffect.REFLECT_PERCENT)) {
            return base;
        }
        return new ReflectEffect((base == null ? 0 : base.chancePercent()) + d(fx, UpgradeEffect.REFLECT_CHANCE),
                (base == null ? 0 : base.reflectPercent()) + d(fx, UpgradeEffect.REFLECT_PERCENT), base == null || base.visible());
    }
}
