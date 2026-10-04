package eu.purrtech.purrtechPVE.listener;

import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.combat.BleedManager;
import eu.purrtech.purrtechPVE.combat.CombatKind;
import eu.purrtech.purrtechPVE.combat.DamageFeedback;
import eu.purrtech.purrtechPVE.combat.DebugModeService;
import eu.purrtech.purrtechPVE.combat.DebugStatsReporter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import eu.purrtech.purrtechPVE.combat.DpsTracker;
import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.combat.SkillDamageContext;
import eu.purrtech.purrtechPVE.combat.WorldToggleEvaluator;
import eu.purrtech.purrtechPVE.config.CombatFeedbackSettings;
import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import net.kyori.adventure.text.Component;
import eu.purrtech.purrtechPVE.damage.DamagePipeline;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ReflectEffect;
import eu.purrtech.purrtechPVE.item.StunEffect;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
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
 * <p>Critical hits and bleed are both rolled off the attacker's whole equipped set's pooled
 * {@link CriticalEffect}/{@link BleedEffect} (weapon in hand, worn armor, trinkets alike, each
 * contributing piece's own fields summed - see {@link EquipmentResolver#resolveCriticalEffect}),
 * independently of each other, and only once every field either needs is actually set (see
 * their {@code isComplete()}). A crit's chance is first scaled by the
 * defender's {@code EquipmentResolver.resolveCritResistPercent} (worn
 * armor's/held item's {@code critResistPercent}, live/unversioned, positive
 * resists/negative is weakness - same convention as stun resist below), then
 * multiplies the fully-resolved total (and the action bar breakdown shown,
 * scaled the same way, so the numbers add up) - same convention as vanilla's
 * own sword crit. A successful bleed
 * roll hands off to {@link BleedManager}, which owns the actual over-time
 * ticking; this class only computes the per-tick damage (the weapon's own
 * {@code damageAmount}/{@code mode}, same shape as a normal {@code
 * DamageContribution}, split evenly across however many ticks fit the
 * weapon's configured duration).
 *
 * <p>Stun is rolled the same way, off the attacker's whole-equipped-set-pooled {@link StunEffect},
 * except its chance is first reduced by the defender's {@code EquipmentResolver.resolveStunResistPercent}
 * (worn armor's {@code stunResistPercent}, live/unversioned unlike the weapon-side chance/duration
 * themselves). A successful roll marks the defender's expiry timestamp in {@code
 * stunnedUntilMillis} and applies {@code SLOWNESS}/{@code BLINDNESS} for the same duration
 * ("zpomalená a nebude nic vidět"); the "can't attack" part ("nemůže útočit") isn't a potion
 * effect at all - it's enforced by cancelling this very event up front whenever the ATTACKER is
 * found still stunned, regardless of which weapon/side stunned them.
 *
 * <p>Reflect is resolved off the DEFENDER's entire equipped set ({@link
 * EquipmentResolver#resolveReflectEffects}, weapon in hand + worn armor + trinkets alike) - each
 * equipped piece's {@link eu.purrtech.purrtechPVE.item.ReflectEffect} rolls independently once this
 * hit's final total is known, and successful rolls' reflected amounts are summed together with the
 * defender's {@code EquipmentResolver.resolvePassiveReflectPercent} (a live/unversioned percent,
 * pooled the same way as {@code stunResistPercent}/{@code critResistPercent}, that reflects that
 * share of the total on EVERY hit with no chance roll) and applied straight back to the attacker
 * via {@code LivingEntity.damage(double)} (no damager argument), which fires a plain {@code
 * EntityDamageEvent} rather than another {@code EntityDamageByEntityEvent} - so a reflect can't
 * recursively trigger this very listener even if both combatants have it configured.
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

    /** Vanilla's critical-hit damage multiplier (jump/falling melee hit). */
    private static final double VANILLA_CRIT_MULTIPLIER = 1.5;

    // Not final - see refresh(), called by PurrtechPVE.reload() so an admin flipping pvp.enabled/
    // pve.enabled/combat.show-effectiveness-colors in config.yml and running /pve reload takes
    // effect immediately, instead of needing a server restart (this listener is registered once
    // at onEnable and would otherwise keep whatever it was constructed with forever).
    private WorldToggleSettings worldToggles;
    private final EquipmentResolver equipmentResolver;
    private final DamageTypeRegistry damageTypeRegistry;
    private final BleedManager bleedManager;
    private CombatFeedbackSettings combatFeedbackSettings;
    private final DpsTracker dpsTracker;
    // Hits waiting for onDamageResult. Weak so a hit another plugin cancels in between (MONITOR skips
    // cancelled events) can't leak; the events are only ever touched on the main thread.
    private final Map<EntityDamageByEntityEvent, PendingFeedback> pendingFeedback = new WeakHashMap<>();
    private final PurrtechPVE plugin;
    private final DebugModeService debugModeService;
    // System.currentTimeMillis() an entity's stun expires at - see the class javadoc's stun
    // paragraph. Only ever touched from this listener's own event handler, always on the main
    // thread, so a plain HashMap is fine.
    private final Map<UUID, Long> stunnedUntilMillis = new HashMap<>();

    public CombatDamageListener(WorldToggleSettings worldToggles, EquipmentResolver equipmentResolver,
                                 DamageTypeRegistry damageTypeRegistry, BleedManager bleedManager,
                                 CombatFeedbackSettings combatFeedbackSettings, DpsTracker dpsTracker,
                                 PurrtechPVE plugin, DebugModeService debugModeService) {
        this.worldToggles = worldToggles;
        this.equipmentResolver = equipmentResolver;
        this.damageTypeRegistry = damageTypeRegistry;
        this.bleedManager = bleedManager;
        this.combatFeedbackSettings = combatFeedbackSettings;
        this.dpsTracker = dpsTracker;
        this.plugin = plugin;
        this.debugModeService = debugModeService;
    }

    /**
     * {@code /pve debug} readout for one side of a hit, so a missing action bar can be told apart:
     * a "skipped" line means this listener never got as far as sending it (world/PvP/PvE toggle),
     * a "hit" line means it did send it - so if the bar still isn't visible, something else
     * (another plugin's HUD) overwrote it. No-op for non-players and players without debug on.
     */
    private void debugSkipped(LivingEntity who, String worldName, CombatKind kind) {
        if (who instanceof Player player && debugModeService.isEnabled(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "debug.combat-skipped",
                    Placeholder.unparsed("world", worldName), Placeholder.unparsed("kind", kind.name())));
        }
    }

    private void debugHit(LivingEntity who, LivingEntity target, double rawDamage, double total, Map<String, Double> perType,
                          Map<String, Double> targetResistance, double armorMultiplier) {
        if (who instanceof Player player && debugModeService.isEnabled(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "debug.combat-hit",
                    Placeholder.unparsed("mode", player.getGameMode().name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("raw", DamageFeedback.formatAmount(rawDamage)),
                    Placeholder.unparsed("total", DamageFeedback.formatAmount(total)),
                    Placeholder.unparsed("types", DebugStatsReporter.join(new TreeMap<>(perType), false)),
                    Placeholder.unparsed("resist", DebugStatsReporter.join(new TreeMap<>(targetResistance), true)),
                    Placeholder.unparsed("armor", DamageFeedback.formatAmount((1 - armorMultiplier) * 100.0)),
                    Placeholder.unparsed("target", equipmentResolver.describeTarget(target))));
        }
    }

    /** See the {@code worldToggles}/{@code combatFeedbackSettings} field comment. */
    public void refresh(WorldToggleSettings worldToggles, CombatFeedbackSettings combatFeedbackSettings) {
        this.worldToggles = worldToggles;
        this.combatFeedbackSettings = combatFeedbackSettings;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity defender)) {
            return;
        }
        LivingEntity attacker = resolveAttacker(event);
        if (attacker != null && isStunned(attacker)) {
            event.setCancelled(true);
            return;
        }
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
            debugSkipped(attacker, worldName, kind);
            debugSkipped(defender, worldName, kind);
            return;
        }

        double rawDamage = event.getDamage();
        // Vanilla's own critical (a jump hit) multiplies the event's damage by 1.5, but a FLAT damage
        // contribution ignores rawDamage entirely - so for a weapon with fixed damage that multiplier
        // simply vanished and a crit hit barely differed from a normal one. Resolve against the
        // non-crit base instead and apply the 1.5 to the finished result below, which also keeps
        // percent contributions (they scale with rawDamage) from being boosted twice.
        double vanillaCritFactor = event.isCritical() && event.getDamager() instanceof Player ? VANILLA_CRIT_MULTIPLIER : 1.0;
        double baseDamage = rawDamage / vanillaCritFactor;
        // A MythicMobs pvedamage{id=...} skill hands over the typed damage configured for that attack
        // (see SkillDamageContext) - used as-is instead of a split derived from the held weapon.
        Map<String, Double> skillDamage = SkillDamageContext.current();
        Map<String, Double> typedDamage = skillDamage != null
                ? new HashMap<>(skillDamage)
                : equipmentResolver.resolveOutgoingTypedDamage(attacker, baseDamage);
        Map<String, Double> resistance = equipmentResolver.resolveResistance(attacker, defender);
        DamagePipeline.Result result = DamagePipeline.applyDetailed(baseDamage, typedDamage, resistance);

        // Flat, vanilla-style armor points (ItemTemplate.armorAmount) - a separate multiplicative
        // layer mirroring vanilla's own armor DamageModifier, but scoped to DamageTypeRegistry
        // .PHYSICAL_TYPES only (slashing/blunt/piercing, plus the legacy "physical" bucket) - a
        // sword blocks armor, fire/poison/magic/bite etc. don't, same as any ARPG's separate
        // physical/magic mitigation.
        double armorMultiplier = DamagePipeline.armorMultiplier(equipmentResolver.resolveArmorPoints(attacker, defender));
        Map<String, Double> perTypeArmored = new HashMap<>();
        double armored = 0;
        for (Map.Entry<String, Double> entry : result.perType().entrySet()) {
            double amount = DamageTypeRegistry.PHYSICAL_TYPES.contains(entry.getKey())
                    ? entry.getValue() * armorMultiplier : entry.getValue();
            perTypeArmored.put(entry.getKey(), amount);
            armored += amount;
        }

        // Critical hits multiply the fully-resolved total - same convention as vanilla's own sword
        // crit - not any one typed bucket, so the per-type action bar breakdown below is scaled by
        // the same factor to keep the numbers shown adding up to what's actually dealt.
        Optional<CriticalEffect> critical = equipmentResolver.resolveCriticalEffect(attacker);
        boolean isCritical = false;
        if (critical.isPresent() && critical.get().isComplete()) {
            double critResistPercent = equipmentResolver.resolveCritResistPercent(defender);
            double effectiveCritChance = critical.get().chancePercent() * (1 - critResistPercent / 100.0);
            isCritical = ThreadLocalRandom.current().nextDouble(100) < effectiveCritChance;
        }
        double total = armored;
        Map<String, Double> perTypeForDisplay = perTypeArmored;
        if (isCritical) {
            double critFactor = 1 + critical.get().bonusDamagePercent() / 100.0;
            total *= critFactor;
            Map<String, Double> scaled = new HashMap<>();
            perTypeArmored.forEach((type, amount) -> scaled.put(type, amount * critFactor));
            perTypeForDisplay = scaled;
        }
        if (vanillaCritFactor != 1.0) {
            total *= vanillaCritFactor;
            Map<String, Double> scaled = new HashMap<>();
            perTypeForDisplay.forEach((type, amount) -> scaled.put(type, amount * vanillaCritFactor));
            perTypeForDisplay = scaled;
        }
        event.setDamage(total);
        ignoreEnchantmentProtection(event);

        // Bleed: rolled independently of crit, off the attacker's whole pooled equipped set, only once chance/
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

        // Stun: rolled independently of crit/bleed, off the attacker's whole pooled equipped set, only once
        // chance/duration are BOTH set (see StunEffect.isComplete()). The defender's worn armor's
        // stunResistPercent scales the chance down multiplicatively before the roll - see
        // EquipmentResolver.resolveStunResistPercent's javadoc.
        Optional<StunEffect> stun = equipmentResolver.resolveStunEffect(attacker);
        if (stun.isPresent() && stun.get().isComplete()) {
            double resistPercent = equipmentResolver.resolveStunResistPercent(defender);
            double effectiveChance = stun.get().chancePercent() * (1 - resistPercent / 100.0);
            if (effectiveChance > 0 && ThreadLocalRandom.current().nextDouble(100) < effectiveChance) {
                applyStun(defender, stun.get().durationSeconds());
            }
        }

        // Reflect: rolled independently of crit/bleed/stun, off the DEFENDER's whole equipped set
        // (weapon in hand, worn armor, trinkets alike - see EquipmentResolver.resolveReflectEffects),
        // since it has to trigger whether the item carrying it is held or worn. Each equipped
        // piece's effect rolls independently; successful rolls' reflected amounts (reflectPercent%
        // of the fully-resolved total this hit dealt, crit/armor included) are summed together
        // with passiveReflectPercent's share of that same total (live/unversioned, pooled the same
        // way as stunResistPercent/critResistPercent, no chance roll - see
        // EquipmentResolver.resolvePassiveReflectPercent) and applied back to the attacker with no
        // damager argument, so this doesn't re-enter this very listener and risk an infinite loop
        // if both combatants have reflect configured.
        double reflected = 0;
        for (ReflectEffect effect : equipmentResolver.resolveReflectEffects(defender)) {
            if (ThreadLocalRandom.current().nextDouble(100) < effect.chancePercent()) {
                reflected += total * effect.reflectPercent() / 100.0;
            }
        }
        double passiveReflectPercent = equipmentResolver.resolvePassiveReflectPercent(defender);
        if (passiveReflectPercent > 0) {
            reflected += total * passiveReflectPercent / 100.0;
        }
        if (reflected > 0) {
            attacker.damage(reflected);
        }

        // The action bar and DPS are shown from onDamageResult (MONITOR), once everything else has had its
        // say, so they report the damage that was actually taken off the target - not just what this
        // listener set on the event, which vanilla armor / MythicMobs modifiers can still reduce.
        pendingFeedback.put(event, new PendingFeedback(attacker, defender, perTypeForDisplay, total, isCritical, resistance));
        debugHit(attacker, defender, rawDamage, total, perTypeForDisplay, resistance, armorMultiplier);
        debugHit(defender, defender, rawDamage, total, perTypeForDisplay, resistance, armorMultiplier);
    }

    /**
     * {@code /pve debug} only: what the hit really did once every other plugin and vanilla had their
     * say. The earlier debug line shows what THIS plugin set on the event; {@code getFinalDamage()} is
     * what is actually taken off the target (vanilla armor/enchantments/resistance, MythicMobs damage
     * modifiers, invulnerability frames) - so a gap between the two is damage lost outside this plugin.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResult(EntityDamageByEntityEvent event) {
        PendingFeedback pending = pendingFeedback.remove(event);
        if (pending == null) {
            return; // this plugin never handled the hit (toggled off, not a player involved, ...)
        }
        List<Player> watching = new ArrayList<>(2);
        if (event.getDamager() instanceof Player damager && debugModeService.isEnabled(damager.getUniqueId())) {
            watching.add(damager);
        }
        if (event.getEntity() instanceof Player victim && victim != event.getDamager() && debugModeService.isEnabled(victim.getUniqueId())) {
            watching.add(victim);
        }
        if (event.isCancelled()) {
            // Runs at MONITOR with cancelled events included on purpose: if this plugin set a damage but
            // nothing was dealt, this is the only place that can tell it was cancelled afterwards.
            for (Player player : watching) {
                player.sendMessage(plugin.getMessages().render(player.locale(), "debug.combat-cancelled",
                        Placeholder.unparsed("plugins", pluginsAfterUs())));
            }
            return;
        }
        double dealt = event.getFinalDamage();
        sendFeedback(pending, dealt);
        if (watching.isEmpty() || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
        AttributeInstance toughness = target.getAttribute(Attribute.ARMOR_TOUGHNESS);
        for (Player player : watching) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "debug.combat-final",
                    Placeholder.unparsed("dealt", DamageFeedback.formatAmount(dealt)),
                    Placeholder.unparsed("crit", event.isCritical() ? "ano" : "ne"),
                    Placeholder.unparsed("health", DamageFeedback.formatAmount(target.getHealth())),
                    Placeholder.unparsed("immune", target.getNoDamageTicks() + "/" + target.getMaximumNoDamageTicks()),
                    Placeholder.unparsed("varmor", DamageFeedback.formatAmount(armor != null ? armor.getValue() : 0)
                            + "/" + DamageFeedback.formatAmount(toughness != null ? toughness.getValue() : 0)),
                    Placeholder.unparsed("mods", vanillaModifiers(event))));
        }
    }

    /**
     * Vanilla's own {@code MAGIC} damage modifier is the armor's Protection-type enchantments
     * (nothing to do with this plugin's {@code magic} damage type). It runs on top of the typed
     * damage and resistances already worked out above and can swallow up to 80% of a hit, so
     * resistances would effectively be decided by what is enchanted on the armor instead of by the
     * item templates. Zeroing it hands that job back to the plugin; vanilla armor points are left
     * alone. Must run right after {@code setDamage(total)}, because that call recomputes the
     * modifiers from the new base damage.
     */
    @SuppressWarnings("deprecation")
    private void ignoreEnchantmentProtection(EntityDamageByEntityEvent event) {
        try {
            if (event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
                event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Couldn't zero the vanilla enchantment-protection modifier: " + e);
        }
    }

    /**
     * {@code /pve debug} only: what vanilla took off this hit after our listener set it, per modifier
     * (armor, resistance effect, enchantment protection, absorption, ...). Shows where a big gap
     * between "hit ... -> X" and "actually dealt" comes from. Uses Bukkit's deprecated DamageModifier
     * API, which Paper still ships and which is the only per-modifier readout the event offers.
     */
    @SuppressWarnings("deprecation")
    private static String vanillaModifiers(EntityDamageByEntityEvent event) {
        StringBuilder out = new StringBuilder();
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier == EntityDamageEvent.DamageModifier.BASE || !event.isApplicable(modifier)) {
                continue;
            }
            double amount = event.getDamage(modifier);
            if (amount == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(modifier.name().toLowerCase(Locale.ROOT)).append(' ').append(DamageFeedback.formatAmount(amount));
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    /**
     * Plugins with a damage listener that runs AFTER this one (HIGH, HIGHEST) - the only ones that could
     * have cancelled or zeroed a hit this plugin had already handled. Bukkit doesn't say who cancelled,
     * so this narrows it down to a short list.
     */
    private static String pluginsAfterUs() {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (RegisteredListener listener : EntityDamageByEntityEvent.getHandlerList().getRegisteredListeners()) {
            EventPriority priority = listener.getPriority();
            if ((priority == EventPriority.HIGH || priority == EventPriority.HIGHEST) && !listener.getPlugin().getName().equals("PurrtechPVE")) {
                names.add(listener.getPlugin().getName());
            }
        }
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    /**
     * Shows the per-type breakdown scaled so it adds up to {@code dealt}: if something after this
     * listener (vanilla armor, another plugin) shaved the hit down, every number is shrunk by the same
     * ratio instead of showing damage that never happened.
     */
    private void sendFeedback(PendingFeedback pending, double dealt) {
        double ratio = pending.total() > 0 ? dealt / pending.total() : 1.0;
        Map<String, Double> shown = new HashMap<>();
        pending.perType().forEach((type, amount) -> shown.put(type, amount * ratio));
        boolean effectivenessColors = combatFeedbackSettings.effectivenessColors();
        if (pending.defender() instanceof Player defenderPlayer) {
            defenderPlayer.sendActionBar(DamageFeedback.render(shown, damageTypeRegistry, NamedTextColor.RED,
                    pending.critical(), pending.resistance(), effectivenessColors));
        }
        if (pending.attacker() instanceof Player attackerPlayer) {
            Component feedback = DamageFeedback.render(shown, damageTypeRegistry, NamedTextColor.YELLOW,
                    pending.critical(), pending.resistance(), effectivenessColors);
            dpsTracker.record(attackerPlayer.getUniqueId(), dealt);
            if (dpsTracker.isEnabled(attackerPlayer.getUniqueId())) {
                double dps = dpsTracker.currentDps(attackerPlayer.getUniqueId());
                feedback = feedback.append(Component.text("  DPS: " + DamageFeedback.formatAmount(dps), NamedTextColor.AQUA));
            }
            attackerPlayer.sendActionBar(feedback);
        }
    }

    private record PendingFeedback(LivingEntity attacker, LivingEntity defender, Map<String, Double> perType, double total,
                                   boolean critical, Map<String, Double> resistance) {
    }

    /** Whether {@code entity} is still within a previously-rolled stun's duration - see the class javadoc's stun paragraph. */
    private boolean isStunned(LivingEntity entity) {
        Long until = stunnedUntilMillis.get(entity.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Marks {@code entity} stunned for {@code durationSeconds} ("nemůže útočit" is enforced
     * separately, by cancelling this listener's own event whenever the ATTACKER is found still
     * stunned) and applies the visible slow/blind part ("zpomalená a nebude nic vidět") as real
     * potion effects so it's obvious to the player too. {@code merge}d rather than overwritten so a
     * second stun landing mid-stun extends rather than shortens the remaining time.
     */
    private void applyStun(LivingEntity entity, double durationSeconds) {
        long expiresAt = System.currentTimeMillis() + (long) (durationSeconds * 1000);
        stunnedUntilMillis.merge(entity.getUniqueId(), expiresAt, Math::max);
        int ticks = (int) Math.ceil(durationSeconds * 20.0);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, false, true));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, true));
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
