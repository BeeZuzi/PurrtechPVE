package eu.purrtech.purrtechPVE;

import eu.purrtech.purrtechPVE.combat.BleedManager;
import eu.purrtech.purrtechPVE.combat.DpsTracker;
import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.command.PveCommand;
import eu.purrtech.purrtechPVE.config.AccessorySettings;
import eu.purrtech.purrtechPVE.config.ArmorPenetrationConversionSettings;
import eu.purrtech.purrtechPVE.config.CombatFeedbackSettings;
import eu.purrtech.purrtechPVE.config.ConfigLoader;
import eu.purrtech.purrtechPVE.config.DropHologramSettings;
import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import eu.purrtech.purrtechPVE.damage.DamageType;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.ArmorClassProfileRepository;
import eu.purrtech.purrtechPVE.db.ArmorPenetrationRepository;
import eu.purrtech.purrtechPVE.db.AttributeModifierRepository;
import eu.purrtech.purrtechPVE.db.BleedEffectRepository;
import eu.purrtech.purrtechPVE.db.CriticalEffectRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.Database;
import eu.purrtech.purrtechPVE.db.ItemHologramRepository;
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.MobDamageProfileRepository;
import eu.purrtech.purrtechPVE.db.MobDropRepository;
import eu.purrtech.purrtechPVE.db.MobEquipmentRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import eu.purrtech.purrtechPVE.gui.ItemEditorListener;
import eu.purrtech.purrtechPVE.hologram.DropHologramListener;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemSyncService;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.itemset.ItemSetService;
import eu.purrtech.purrtechPVE.lang.Messages;
import eu.purrtech.purrtechPVE.listener.CombatDamageListener;
import eu.purrtech.purrtechPVE.listener.ItemSyncJoinListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobDropListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobEquipmentListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import eu.purrtech.purrtechPVE.trinket.AccessoryMenuListener;
import eu.purrtech.purrtechPVE.trinket.TrinketAttributeListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

public final class PurrtechPVE extends JavaPlugin {

    private Database database;
    private Messages messages;
    private Locale defaultLocale;
    private WorldToggleSettings worldToggles;
    private AccessorySettings accessorySettings;
    private DamageTypeRegistry damageTypeRegistry;
    private ItemTemplateService itemTemplateService;
    private ItemSyncService itemSyncService;
    private MobDamageProfileRepository mobDamageProfileRepository;
    private AccessoryRepository accessoryRepository;
    private ItemSetService itemSetService;
    private ItemEditorListener itemEditorListener;
    private MythicMobsBridge mythicMobsBridge;
    private MobEquipmentRepository mobEquipmentRepository;
    private MobDropRepository mobDropRepository;
    private ArmorClassProfileRepository armorClassProfileRepository;
    private CombatFeedbackSettings combatFeedbackSettings;
    private ArmorPenetrationConversionSettings armorPenetrationConversionSettings;
    private DpsTracker dpsTracker;
    private ItemRenderer itemRenderer;
    private EquipmentResolver equipmentResolver;
    private CombatDamageListener combatDamageListener;
    private TrinketAttributeListener trinketAttributeListener;
    private DropHologramSettings dropHologramSettings;
    private ItemHologramRepository itemHologramRepository;
    private DropHologramListener dropHologramListener;
    // Captures every repository MythicMobsBridge/MythicMobEquipmentListener setup needs - built
    // once in onEnable (where those repositories are local variables) and re-run by reload(), so
    // an admin who installs/updates MythicMobs or fixes whatever broke its API detection can pick
    // that up with /pve reload instead of a full server restart. A no-op if the bridge is already
    // up (see trySetupMythicMobs) or MythicMobs still isn't enabled.
    private Runnable mythicMobsSetup;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(getDataFolder());
        database.connect();

        messages = Messages.load(this);
        defaultLocale = Locale.forLanguageTag(ConfigLoader.loadLocale(getConfig()));
        worldToggles = ConfigLoader.loadWorldToggles(getConfig());
        accessorySettings = ConfigLoader.loadAccessorySettings(getConfig());
        combatFeedbackSettings = ConfigLoader.loadCombatFeedbackSettings(getConfig());
        armorPenetrationConversionSettings = ConfigLoader.loadArmorPenetrationConversion(getConfig());
        dropHologramSettings = ConfigLoader.loadDropHologramSettings(getConfig());
        dpsTracker = new DpsTracker();

        damageTypeRegistry = new DamageTypeRegistry();

        ItemTemplateRepository itemTemplateRepository = new ItemTemplateRepository(database);
        ItemTemplateSnapshotRepository snapshotRepository = new ItemTemplateSnapshotRepository(database);
        DamageContributionRepository damageContributionRepository = new DamageContributionRepository(database);
        TypeModifierRepository typeModifierRepository = new TypeModifierRepository(database);
        TemplateEnchantmentRepository enchantmentRepository = new TemplateEnchantmentRepository(database);
        ArmorPenetrationRepository armorPenetrationRepository = new ArmorPenetrationRepository(database);
        BleedEffectRepository bleedEffectRepository = new BleedEffectRepository(database);
        CriticalEffectRepository criticalEffectRepository = new CriticalEffectRepository(database);
        AttributeModifierRepository attributeModifierRepository = new AttributeModifierRepository(database);
        mobDamageProfileRepository = new MobDamageProfileRepository(database);
        armorClassProfileRepository = new ArmorClassProfileRepository(database);
        accessoryRepository = new AccessoryRepository(database);
        mobEquipmentRepository = new MobEquipmentRepository(database);
        mobDropRepository = new MobDropRepository(database);
        itemHologramRepository = new ItemHologramRepository(database);
        ItemSetRepository itemSetRepository = new ItemSetRepository(database);
        ItemSetMemberRepository itemSetMemberRepository = new ItemSetMemberRepository(database);
        ItemSetDamageThresholdRepository itemSetDamageThresholdRepository = new ItemSetDamageThresholdRepository(database);
        ItemSetModifierThresholdRepository itemSetModifierThresholdRepository = new ItemSetModifierThresholdRepository(database);
        itemRenderer = new ItemRenderer(this, messages, defaultLocale);
        itemTemplateService = new ItemTemplateService(
                itemTemplateRepository,
                damageContributionRepository,
                typeModifierRepository,
                enchantmentRepository,
                armorPenetrationRepository,
                bleedEffectRepository,
                criticalEffectRepository,
                attributeModifierRepository,
                snapshotRepository,
                damageTypeRegistry,
                itemRenderer);
        itemSyncService = new ItemSyncService(itemTemplateRepository, snapshotRepository, itemRenderer);
        itemSetService = new ItemSetService(
                itemSetRepository,
                itemSetMemberRepository,
                itemSetDamageThresholdRepository,
                itemSetModifierThresholdRepository,
                itemTemplateRepository,
                damageTypeRegistry);

        mythicMobsSetup = () -> trySetupMythicMobs(mobEquipmentRepository, mobDropRepository, itemTemplateRepository, damageContributionRepository,
                typeModifierRepository, enchantmentRepository, armorPenetrationRepository, bleedEffectRepository,
                criticalEffectRepository, attributeModifierRepository, itemRenderer);
        mythicMobsSetup.run();
        equipmentResolver = new EquipmentResolver(itemTemplateRepository, snapshotRepository,
                mobDamageProfileRepository, armorClassProfileRepository, accessoryRepository, itemSetMemberRepository,
                itemSetDamageThresholdRepository, itemSetModifierThresholdRepository, itemRenderer, mythicMobsBridge,
                armorPenetrationConversionSettings);

        getLogger().info("MythicMobs integration: " + (mythicMobsBridge != null ? "enabled" : "not found, running standalone"));
        getLogger().info("World toggles: " + worldToggles.disabledWorlds().size() + " disabled world(s), "
                + "PvP " + (worldToggles.pvpEnabled() ? "on" : "off") + ", PvE " + (worldToggles.pveEnabled() ? "on" : "off"));
        getLogger().info("Accessory slots: " + accessorySettings.slots());
        getLogger().info("Damage types registered: " + damageTypeRegistry.all().keySet());

        BleedManager bleedManager = new BleedManager();
        // Cadence comes from the "bleed" DamageType's own dotPeriodTicks, not a fixed constant -
        // one repeating task drives every active bleed at once (see BleedManager's javadoc).
        int bleedPeriodTicks = damageTypeRegistry.find("bleed").map(DamageType::dotPeriodTicks).orElse(20);
        getServer().getScheduler().runTaskTimer(this, () -> bleedManager.tick(equipmentResolver), bleedPeriodTicks, bleedPeriodTicks);

        combatDamageListener = new CombatDamageListener(worldToggles, equipmentResolver, damageTypeRegistry, bleedManager,
                combatFeedbackSettings, dpsTracker);
        getServer().getPluginManager().registerEvents(combatDamageListener, this);
        getServer().getPluginManager().registerEvents(new ItemSyncJoinListener(itemSyncService), this);
        getServer().getPluginManager().registerEvents(new AccessoryMenuListener(accessoryRepository), this);
        trinketAttributeListener = new TrinketAttributeListener(this, accessoryRepository,
                accessorySettings, itemTemplateRepository, snapshotRepository, itemRenderer);
        getServer().getPluginManager().registerEvents(trinketAttributeListener, this);
        itemEditorListener = new ItemEditorListener(this);
        getServer().getPluginManager().registerEvents(itemEditorListener, this);
        dropHologramListener = new DropHologramListener(this, dropHologramSettings.enabled());
        getServer().getPluginManager().registerEvents(dropHologramListener, this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(PveCommand.create(this), "PurrtechPVE admin commands"));
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    /**
     * Re-reads {@code config.yml} and every {@code lang/*.yml} from disk, without a server
     * restart - {@code /pve reload}. Updates everything derived from them ({@code messages},
     * {@code defaultLocale}, {@code worldToggles}, {@code accessorySettings}, {@code
     * combatFeedbackSettings}) and pushes the fresh values into the few long-lived objects that
     * captured a copy at {@code onEnable} time ({@link #itemRenderer}, {@link
     * #combatDamageListener}, {@link #trinketAttributeListener}) - everything else (GUI menus,
     * commands) already reads these fresh via this class's own getters on every use, so nothing
     * else needs touching. Also retries {@link #trySetupMythicMobs}, so fixing whatever made
     * MythicMobs detection fail (installing it, updating it, restarting it) can be picked up here
     * too instead of needing a restart - see that method's javadoc for why detection can fail even
     * when the installed version looks right. Finally force-resyncs every online player's stamped
     * items (see {@link ItemSyncService#resyncAllOnlinePlayersFull()}) so a lang/design wording
     * change shows up on items already handed out, not just ones given after this reload.
     *
     * <p>Deliberately NOT reconstructed here: {@code EquipmentResolver}'s own captured {@code
     * MythicMobsBridge} reference (combat resolution keeps whatever MythicMobs state it started
     * with until a restart) - only the admin-facing MythicMobs menus/commands (which always read
     * {@link #getMythicMobsBridge()} live) pick up a reload-time fix.
     */
    public void reload() {
        reloadConfig();
        messages = Messages.load(this);
        defaultLocale = Locale.forLanguageTag(ConfigLoader.loadLocale(getConfig()));
        worldToggles = ConfigLoader.loadWorldToggles(getConfig());
        accessorySettings = ConfigLoader.loadAccessorySettings(getConfig());
        combatFeedbackSettings = ConfigLoader.loadCombatFeedbackSettings(getConfig());
        armorPenetrationConversionSettings = ConfigLoader.loadArmorPenetrationConversion(getConfig());
        dropHologramSettings = ConfigLoader.loadDropHologramSettings(getConfig());

        itemRenderer.refresh(messages, defaultLocale);
        combatDamageListener.refresh(worldToggles, combatFeedbackSettings);
        trinketAttributeListener.refresh(accessorySettings);
        equipmentResolver.refresh(armorPenetrationConversionSettings);
        dropHologramListener.refresh(dropHologramSettings.enabled());
        mythicMobsSetup.run();
        // Lang text is global, not per-template, so it never bumps any template's version - the
        // normal syncedVersion-gated resync would never pick this up. Force every stamped stack
        // already in circulation to re-render right now instead of waiting on an unrelated
        // template edit to trigger a push.
        itemSyncService.resyncAllOnlinePlayersFull();

        getLogger().info("Reloaded config.yml + lang/*.yml. World toggles: " + worldToggles.disabledWorlds().size()
                + " disabled world(s), PvP " + (worldToggles.pvpEnabled() ? "on" : "off")
                + ", PvE " + (worldToggles.pveEnabled() ? "on" : "off") + ". MythicMobs integration: "
                + (mythicMobsBridge != null ? "enabled" : "not found, running standalone"));
    }

    /**
     * A plugin literally named "MythicMobs" being enabled doesn't guarantee its classes match the
     * API this was built against - an older/forked/incompatible build, or a "MythicMobs" that's
     * technically enabled but hasn't finished its own (async) startup loading yet, can still throw
     * {@link NoClassDefFoundError}/{@link NoSuchMethodError} the first time its classes are
     * actually touched. {@code probe()} forces that resolution right now, where a failure is loud
     * (full stack trace logged) and diagnosable, rather than crashing every single damage event or
     * mob spawn later. A no-op if the bridge is already established (nothing to redo) or MythicMobs
     * isn't enabled at all - safe to call repeatedly from both {@code onEnable} and {@link
     * #reload()}.
     */
    private void trySetupMythicMobs(MobEquipmentRepository mobEquipmentRepository, MobDropRepository mobDropRepository,
                                     ItemTemplateRepository itemTemplateRepository,
                                     DamageContributionRepository damageContributionRepository, TypeModifierRepository typeModifierRepository,
                                     TemplateEnchantmentRepository enchantmentRepository, ArmorPenetrationRepository armorPenetrationRepository,
                                     BleedEffectRepository bleedEffectRepository, CriticalEffectRepository criticalEffectRepository,
                                     AttributeModifierRepository attributeModifierRepository, ItemRenderer itemRenderer) {
        if (mythicMobsBridge != null || !getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        try {
            MythicMobsBridge bridge = new MythicMobsBridge();
            bridge.probe();
            mythicMobsBridge = bridge;
        } catch (Throwable t) {
            getLogger().log(Level.WARNING,
                    "A plugin named MythicMobs is enabled, but its API doesn't match what PurrtechPVE was built "
                            + "against - running without MythicMobs integration until the next /pve reload. If the "
                            + "installed version looks correct, this is most likely MythicMobs not having finished "
                            + "its own startup yet when this ran - try /pve reload once the server has fully started.",
                    t);
            return;
        }
        // Same defensive posture as probe() above: this listener's @EventHandler method signature
        // references a MythicMobs event class, so registering it also risks NoClassDefFoundError
        // on a name-matches-but-API-differs build. Only ever reached once per successful bridge
        // establishment (the early return above skips this whole method once mythicMobsBridge is
        // set), so this can't double-register across repeated reload() calls.
        try {
            getServer().getPluginManager().registerEvents(new MythicMobEquipmentListener(
                    mobEquipmentRepository, itemTemplateRepository, damageContributionRepository,
                    typeModifierRepository, enchantmentRepository, armorPenetrationRepository,
                    bleedEffectRepository, criticalEffectRepository, attributeModifierRepository, itemRenderer), this);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING,
                    "Failed to register the MythicMobs mob-equipment listener - mobs won't spawn with assigned equipment.", t);
        }
        try {
            getServer().getPluginManager().registerEvents(
                    new MythicMobDropListener(mobDropRepository, itemTemplateRepository, itemTemplateService), this);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING,
                    "Failed to register the MythicMobs mob-drop listener - mobs won't drop assigned loot.", t);
        }
    }

    public Database getDatabase() {
        return database;
    }

    public Messages getMessages() {
        return messages;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public WorldToggleSettings getWorldToggles() {
        return worldToggles;
    }

    public AccessorySettings getAccessorySettings() {
        return accessorySettings;
    }

    public DamageTypeRegistry getDamageTypeRegistry() {
        return damageTypeRegistry;
    }

    public ItemTemplateService getItemTemplateService() {
        return itemTemplateService;
    }

    public ItemSyncService getItemSyncService() {
        return itemSyncService;
    }

    public MobDamageProfileRepository getMobDamageProfileRepository() {
        return mobDamageProfileRepository;
    }

    public AccessoryRepository getAccessoryRepository() {
        return accessoryRepository;
    }

    public ItemEditorListener getItemEditorListener() {
        return itemEditorListener;
    }

    public ItemSetService getItemSetService() {
        return itemSetService;
    }

    /** Null when MythicMobs isn't installed, or is but its API doesn't match what this plugin was built against. */
    public MythicMobsBridge getMythicMobsBridge() {
        return mythicMobsBridge;
    }

    public MobEquipmentRepository getMobEquipmentRepository() {
        return mobEquipmentRepository;
    }

    public MobDropRepository getMobDropRepository() {
        return mobDropRepository;
    }

    public ArmorClassProfileRepository getArmorClassProfileRepository() {
        return armorClassProfileRepository;
    }

    public CombatFeedbackSettings getCombatFeedbackSettings() {
        return combatFeedbackSettings;
    }

    public ArmorPenetrationConversionSettings getArmorPenetrationConversionSettings() {
        return armorPenetrationConversionSettings;
    }

    public DpsTracker getDpsTracker() {
        return dpsTracker;
    }

    public ItemRenderer getItemRenderer() {
        return itemRenderer;
    }

    public DropHologramSettings getDropHologramSettings() {
        return dropHologramSettings;
    }

    public ItemHologramRepository getItemHologramRepository() {
        return itemHologramRepository;
    }
}
