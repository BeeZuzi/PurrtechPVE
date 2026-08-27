package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.ArmorPenetrationRepository;
import eu.purrtech.purrtechPVE.db.DamageContributionRepository;
import eu.purrtech.purrtechPVE.db.Database;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.TemplateEnchantmentRepository;
import eu.purrtech.purrtechPVE.db.TypeModifierRepository;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises everything except {@link ItemTemplateService#renderGiveable} -
 * that path builds a real Bukkit {@code ItemStack}/{@code ItemMeta}, which
 * needs a live server (no MockBukkit in this project, matching the sibling
 * plugins' "no mocking Bukkit" convention) and is instead verified via a
 * real {@code runServer} boot. The renderer collaborator is unused by every
 * method under test here, so {@code null} is safe.
 */
class ItemTemplateServiceTest {

    private Database database;
    private ItemTemplateService service;
    private ItemTemplateSnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        snapshotRepository = new ItemTemplateSnapshotRepository(database);
        service = new ItemTemplateService(
                new ItemTemplateRepository(database),
                new DamageContributionRepository(database),
                new TypeModifierRepository(database),
                new TemplateEnchantmentRepository(database),
                new ArmorPenetrationRepository(database),
                snapshotRepository,
                new DamageTypeRegistry(),
                null);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void createThenFindByKey() {
        ItemTemplate template = service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        assertEquals(1, template.version());
        assertTrue(service.findByKey("fire-sword").isPresent());
    }

    @Test
    void createRejectsDuplicateKey() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        assertThrows(DuplicateTemplateKeyException.class,
                () -> service.create("fire-sword", Material.WOODEN_SWORD, "Jiný meč", "console"));
    }

    @Test
    void setDamageContributionBumpsTemplateVersion() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);

        ItemTemplate reloaded = service.findByKey("fire-sword").orElseThrow();
        assertEquals(2, reloaded.version());
        assertEquals(1, service.damageContributions("fire-sword").size());
    }

    @Test
    void newTemplateStartsFullySynced() {
        ItemTemplate template = service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        assertTrue(template.isFullySynced());
        assertEquals(template.version(), template.syncedVersion());
    }

    @Test
    void editingATemplateNeverTouchesSyncedVersionOnItsOwn() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);
        service.setTypeModifier("fire-sword", "frozen", -25.0);

        ItemTemplate reloaded = service.findByKey("fire-sword").orElseThrow();
        assertEquals(3, reloaded.version());
        assertEquals(1, reloaded.syncedVersion());
        assertFalse(reloaded.isFullySynced());
    }

    @Test
    void propagateCatchesSyncedVersionUpToLiveVersion() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);

        ItemTemplate synced = service.propagate("fire-sword");
        assertEquals(2, synced.version());
        assertEquals(2, synced.syncedVersion());
        assertTrue(synced.isFullySynced());
    }

    @Test
    void propagateRejectsUnknownTemplate() {
        assertThrows(TemplateNotFoundException.class, () -> service.propagate("does-not-exist"));
    }

    @Test
    void rebaseChangesMaterialAndBumpsVersionLikeAnyOtherStat() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        ItemTemplate rebased = service.rebase("fire-sword", Material.NETHERITE_SWORD, 42);

        assertEquals(Material.NETHERITE_SWORD, rebased.baseMaterial());
        assertEquals(42, rebased.customModelData());
        assertEquals(2, rebased.version());
        assertEquals(1, rebased.syncedVersion());

        TemplateSnapshot snapshot = snapshotRepository.find(rebased.id(), 2).orElseThrow();
        assertEquals(Material.NETHERITE_SWORD, snapshot.baseMaterial());
        assertEquals(42, snapshot.customModelData());
    }

    @Test
    void rebaseRejectsUnknownTemplate() {
        assertThrows(TemplateNotFoundException.class, () -> service.rebase("does-not-exist", Material.STONE, null));
    }

    @Test
    void everyVersionBumpWritesAResolvableSnapshot() {
        ItemTemplate created = service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        ItemTemplate afterEdit = service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);

        TemplateSnapshot v1 = snapshotRepository.find(created.id(), 1).orElseThrow();
        assertTrue(v1.damageContributions().isEmpty());

        TemplateSnapshot v2 = snapshotRepository.find(afterEdit.id(), 2).orElseThrow();
        assertEquals(1, v2.damageContributions().size());
        assertEquals("fire", v2.damageContributions().get(0).damageTypeKey());
    }

    @Test
    void setDamageContributionRejectsUnknownTemplate() {
        assertThrows(TemplateNotFoundException.class,
                () -> service.setDamageContribution("does-not-exist", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED));
    }

    @Test
    void setDamageContributionRejectsUnknownDamageType() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        assertThrows(UnknownDamageTypeException.class,
                () -> service.setDamageContribution("fire-sword", "not-a-real-type", 4.0, DamageMode.FLAT, ModifierContext.WIELDED));
    }

    @Test
    void removeDamageContribution() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);
        service.removeDamageContribution("fire-sword", "fire", ModifierContext.WIELDED);

        assertTrue(service.damageContributions("fire-sword").isEmpty());
    }

    @Test
    void setAndRemoveTypeModifier() {
        service.create("frost-plate", Material.IRON_CHESTPLATE, "Mrazivá zbroj", "console");
        service.setTypeModifier("frost-plate", "frozen", 50.0);

        List<TypeModifier> modifiers = service.typeModifiers("frost-plate");
        assertEquals(1, modifiers.size());
        assertEquals(50.0, modifiers.get(0).percent());

        service.removeTypeModifier("frost-plate", "frozen");
        assertTrue(service.typeModifiers("frost-plate").isEmpty());
    }

    @Test
    void deleteRemovesTemplateAndItsSubRows() {
        service.create("fire-sword", Material.IRON_SWORD, "Plamenný meč", "console");
        service.setDamageContribution("fire-sword", "fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED);

        assertTrue(service.delete("fire-sword"));
        assertFalse(service.findByKey("fire-sword").isPresent());
    }

    @Test
    void setAllowedSlotsSetsTrinketFlagAndDoesNotBumpVersion() {
        service.create("ring-of-fire", Material.GOLD_NUGGET, "Prsten ohně", "console");
        ItemTemplate restricted = service.setAllowedSlots("ring-of-fire", List.of("OFF_HAND", "RING_1"));

        assertTrue(restricted.trinket());
        assertEquals(List.of("OFF_HAND", "RING_1"), restricted.allowedSlots());
        assertEquals(1, restricted.version(), "allowedSlots is a placement rule, not a stat - no version bump");

        ItemTemplate cleared = service.setAllowedSlots("ring-of-fire", List.of());
        assertFalse(cleared.trinket());
        assertTrue(cleared.allowedSlots().isEmpty());
    }

    @Test
    void listAllReturnsCreatedTemplates() {
        service.create("a", Material.STICK, "A", "console");
        service.create("b", Material.STICK, "B", "console");
        assertEquals(2, service.listAll().size());
    }

    @Test
    void setArmorClassIsALiveClassificationAndDoesNotBumpVersion() {
        service.create("iron-vest", Material.IRON_CHESTPLATE, "Iron Vest", "console");
        ItemTemplate tagged = service.setArmorClass("iron-vest", ArmorClass.MEDIUM);

        assertEquals(ArmorClass.MEDIUM, tagged.armorClass());
        assertEquals(1, tagged.version(), "armor class is a live classification, not a stat - no version bump");

        ItemTemplate cleared = service.setArmorClass("iron-vest", null);
        assertNull(cleared.armorClass());
        assertEquals(1, cleared.version());
    }

    @Test
    void newTemplateHasNoArmorClassByDefault() {
        ItemTemplate created = service.create("plain-item", Material.STICK, "Plain", "console");
        assertNull(created.armorClass());
    }

    @Test
    void setArmorPenetrationIsAWeaponStatAndBumpsVersion() {
        service.create("war-axe", Material.IRON_AXE, "War Axe", "console");
        ItemTemplate withPenetration = service.setArmorPenetration("war-axe", ArmorClass.LIGHT, 10.0);

        assertEquals(2, withPenetration.version(), "armor penetration is a stat like damage contributions - bumps version");
        assertEquals(1, service.armorPenetration("war-axe").size());
        assertEquals(ArmorClass.LIGHT, service.armorPenetration("war-axe").get(0).armorClass());
        assertEquals(10.0, service.armorPenetration("war-axe").get(0).amount());
    }

    @Test
    void removeArmorPenetration() {
        service.create("war-axe", Material.IRON_AXE, "War Axe", "console");
        service.setArmorPenetration("war-axe", ArmorClass.LIGHT, 10.0);
        ItemTemplate afterRemove = service.removeArmorPenetration("war-axe", ArmorClass.LIGHT);

        assertEquals(3, afterRemove.version());
        assertTrue(service.armorPenetration("war-axe").isEmpty());
    }

    @Test
    void newTemplateHasNoArmorPenetrationByDefault() {
        service.create("plain-weapon", Material.STICK, "Plain", "console");
        assertTrue(service.armorPenetration("plain-weapon").isEmpty());
    }
}
