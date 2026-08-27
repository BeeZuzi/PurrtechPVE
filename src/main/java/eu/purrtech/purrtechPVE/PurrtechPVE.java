package eu.purrtech.purrtechPVE;

import eu.purrtech.purrtechPVE.combat.EquipmentResolver;
import eu.purrtech.purrtechPVE.command.PveCommand;
import eu.purrtech.purrtechPVE.config.AccessorySettings;
import eu.purrtech.purrtechPVE.config.ConfigLoader;
import eu.purrtech.purrtechPVE.config.WorldToggleSettings;
import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.AccessoryRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.Database;
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.MobDamageProfileRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import eu.purrtech.purrtechPVE.gui.ItemEditorListener;
import eu.purrtech.purrtechPVE.item.ItemRenderer;
import eu.purrtech.purrtechPVE.item.ItemSyncService;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.itemset.ItemSetService;
import eu.purrtech.purrtechPVE.lang.Messages;
import eu.purrtech.purrtechPVE.listener.CombatDamageListener;
import eu.purrtech.purrtechPVE.listener.ItemSyncJoinListener;
import eu.purrtech.purrtechPVE.mythicmobs.MythicMobsBridge;
import eu.purrtech.purrtechPVE.trinket.AccessoryMenuListener;
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
        mobDamageProfileRepository = new MobDamageProfileRepository(database);
        accessoryRepository = new AccessoryRepository(database);
        ItemSetRepository itemSetRepository = new ItemSetRepository(database);
        ItemSetMemberRepository itemSetMemberRepository = new ItemSetMemberRepository(database);
        ItemSetDamageThresholdRepository itemSetDamageThresholdRepository = new ItemSetDamageThresholdRepository(database);
        ItemSetModifierThresholdRepository itemSetModifierThresholdRepository = new ItemSetModifierThresholdRepository(database);
        ItemRenderer itemRenderer = new ItemRenderer(this, messages, defaultLocale, damageTypeRegistry);
        itemTemplateService = new ItemTemplateService(
                itemTemplateRepository,
                damageContributionRepository,
                typeModifierRepository,
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
        // only ever constructed when MythicMobs is confirmed enabled - see MythicMobsBridge's own javadoc
        MythicMobsBridge mythicMobsBridge = mythicMobsPresent ? new MythicMobsBridge() : null;
        EquipmentResolver equipmentResolver = new EquipmentResolver(itemTemplateRepository, snapshotRepository,
                mobDamageProfileRepository, accessoryRepository, itemSetMemberRepository,
                itemSetDamageThresholdRepository, itemSetModifierThresholdRepository, itemRenderer, mythicMobsBridge);

        getLogger().info("MythicMobs integration: " + (mythicMobsPresent ? "enabled" : "not found, running standalone"));
        getLogger().info("World toggles: " + worldToggles.disabledWorlds().size() + " disabled world(s), "
                + "PvP " + (worldToggles.pvpEnabled() ? "on" : "off") + ", PvE " + (worldToggles.pveEnabled() ? "on" : "off"));
        getLogger().info("Accessory slots: " + accessorySettings.slots());
        getLogger().info("Damage types registered: " + damageTypeRegistry.all().keySet());

        getServer().getPluginManager().registerEvents(
                new CombatDamageListener(worldToggles, equipmentResolver, damageTypeRegistry), this);
        getServer().getPluginManager().registerEvents(new ItemSyncJoinListener(itemSyncService), this);
        getServer().getPluginManager().registerEvents(new AccessoryMenuListener(accessoryRepository), this);
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
}
