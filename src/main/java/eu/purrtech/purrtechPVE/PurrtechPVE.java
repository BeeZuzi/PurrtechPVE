package eu.purrtech.purrtechPVE;

import eu.purrtech.purrtechPVE.combat.BleedManager;
import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.command.PveCommand;
import eu.purrtech.purrtechPVE.config.AccessorySettings;
import eu.purrtech.purrtechPVE.config.ConfigLoader;
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
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.MobDamageProfileRepository;
import eu.purrtech.purrtechPVE.db.MobEquipmentRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import eu.purrtech.purrtechPVE.gui.ItemEditorListener;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemSyncService;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.itemset.ItemSetService;
import eu.purrtech.purrtechPVE.lang.Messages;
import eu.purrtech.purrtechPVE.listener.CombatDamageListener;
import eu.purrtech.purrtechPVE.listener.ItemSyncJoinListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobEquipmentListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import eu.purrtech.purrtechPVE.trinket.AccessoryMenuListener;
import eu.purrtech.purrtechPVE.trinket.TrinketAttributeListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

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
    private ArmorClassProfileRepository armorClassProfileRepository;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(getDataFolder());
        database.connect();

        messages = Messages.load(this);
        defaultLocale = Locale.forLanguageTag(ConfigLoader.loadLocale(getConfig()));
        worldToggles = ConfigLoader.loadWorldToggles(getConfig());
        accessorySettings = ConfigLoader.loadAccessorySettings(getConfig());

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
        ItemSetRepository itemSetRepository = new ItemSetRepository(database);
        ItemSetMemberRepository itemSetMemberRepository = new ItemSetMemberRepository(database);
        ItemSetDamageThresholdRepository itemSetDamageThresholdRepository = new ItemSetDamageThresholdRepository(database);
        ItemSetModifierThresholdRepository itemSetModifierThresholdRepository = new ItemSetModifierThresholdRepository(database);
        ItemRenderer itemRenderer = new ItemRenderer(this, messages, defaultLocale, damageTypeRegistry);
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

        boolean mythicMobsPresent = getServer().getPluginManager().isPluginEnabled("MythicMobs");
        // A plugin literally named "MythicMobs" being enabled doesn't guarantee its classes match
        // the API this was built against (older/forked/incompatible builds still pass the name
        // check) - probe() forces that class resolution right now, where a mismatch is loud and
        // diagnosable, rather than crashing every single damage event later. See MythicMobsBridge's javadoc.
        if (mythicMobsPresent) {
            try {
                MythicMobsBridge bridge = new MythicMobsBridge();
                bridge.probe();
                mythicMobsBridge = bridge;
            } catch (Throwable t) {
                getLogger().warning("A plugin named MythicMobs is enabled, but its API doesn't match what "
                        + "PurrtechPVE was built against (" + t.getClass().getSimpleName()
                        + (t.getMessage() != null ? ": " + t.getMessage() : "") + ") - running without MythicMobs integration.");
            }
        }
        if (mythicMobsBridge != null) {
            // Same defensive posture as the probe() above: this listener's @EventHandler method
            // signature references a MythicMobs event class, so registering it also risks
            // NoClassDefFoundError on a name-matches-but-API-differs build.
            try {
                getServer().getPluginManager().registerEvents(new MythicMobEquipmentListener(
                        mobEquipmentRepository, itemTemplateRepository, damageContributionRepository,
                        typeModifierRepository, enchantmentRepository, armorPenetrationRepository,
                        bleedEffectRepository, criticalEffectRepository, attributeModifierRepository, itemRenderer), this);
            } catch (Throwable t) {
                getLogger().warning("Failed to register the MythicMobs mob-equipment listener ("
                        + t.getClass().getSimpleName()
                        + (t.getMessage() != null ? ": " + t.getMessage() : "") + ") - mobs won't spawn with assigned equipment.");
            }
        }
        EquipmentResolver equipmentResolver = new EquipmentResolver(itemTemplateRepository, snapshotRepository,
                mobDamageProfileRepository, armorClassProfileRepository, accessoryRepository, itemSetMemberRepository,
                itemSetDamageThresholdRepository, itemSetModifierThresholdRepository, itemRenderer, mythicMobsBridge);

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

        getServer().getPluginManager().registerEvents(
                new CombatDamageListener(worldToggles, equipmentResolver, damageTypeRegistry, bleedManager), this);
        getServer().getPluginManager().registerEvents(new ItemSyncJoinListener(itemSyncService), this);
        getServer().getPluginManager().registerEvents(new AccessoryMenuListener(accessoryRepository), this);
        getServer().getPluginManager().registerEvents(new TrinketAttributeListener(this, accessoryRepository,
                accessorySettings, itemTemplateRepository, snapshotRepository, itemRenderer), this);
        itemEditorListener = new ItemEditorListener(this);
        getServer().getPluginManager().registerEvents(itemEditorListener, this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(PveCommand.create(this), "PurrtechPVE admin commands"));
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
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

    public ArmorClassProfileRepository getArmorClassProfileRepository() {
        return armorClassProfileRepository;
    }
}
