package eu.purrtech.purrtechPVE.itemset;

import eu.purrtech.purrtechPVE.damage.DamageTypeRegistry;
import eu.purrtech.purrtechPVE.db.Database;
import eu.purrtech.purrtechPVE.db.ItemSetDamageThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetMemberRepository;
import eu.purrtech.purrtechPVE.db.ItemSetModifierThresholdRepository;
import eu.purrtech.purrtechPVE.db.ItemSetRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSetServiceTest {

    private Database database;
    private ItemSetService service;
    private ItemTemplateRepository templateRepository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        templateRepository = new ItemTemplateRepository(database);
        service = new ItemSetService(
                new ItemSetRepository(database),
                new ItemSetMemberRepository(database),
                new ItemSetDamageThresholdRepository(database),
                new ItemSetModifierThresholdRepository(database),
                templateRepository,
                new DamageTypeRegistry());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void createTemplate(String key) {
        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), key, key, List.of(), List.of(), List.of(), Material.IRON_HELMET,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        templateRepository.insert(template);
    }

    @Test
    void createThenFindByKey() {
        ItemSet set = service.create("dragon-set", "Sada draka");
        assertEquals("Sada draka", set.displayName());
        assertTrue(service.findByKey("dragon-set").isPresent());
    }

    @Test
    void createRejectsDuplicateKey() {
        service.create("dragon-set", "Sada draka");
        assertThrows(DuplicateSetKeyException.class, () -> service.create("dragon-set", "Jiná sada"));
    }

    @Test
    void addAndListMembers() {
        service.create("dragon-set", "Sada draka");
        createTemplate("helm");
        createTemplate("chest");

        service.addMember("dragon-set", "helm");
        service.addMember("dragon-set", "chest");

        List<ItemTemplate> members = service.members("dragon-set");
        assertEquals(2, members.size());
    }

    @Test
    void addMemberRejectsUnknownSetOrTemplate() {
        service.create("dragon-set", "Sada draka");
        createTemplate("helm");

        assertThrows(ItemSetNotFoundException.class, () -> service.addMember("does-not-exist", "helm"));
        assertThrows(TemplateNotFoundException.class, () -> service.addMember("dragon-set", "does-not-exist"));
    }

    @Test
    void removeMember() {
        service.create("dragon-set", "Sada draka");
        createTemplate("helm");
        service.addMember("dragon-set", "helm");

        service.removeMember("dragon-set", "helm");
        assertTrue(service.members("dragon-set").isEmpty());
    }

    @Test
    void damageThresholdsAreCumulativeByPieceCountOrdering() {
        service.create("dragon-set", "Sada draka");
        service.setDamageThreshold("dragon-set", 4, "fire", 10.0, DamageMode.FLAT);
        service.setDamageThreshold("dragon-set", 2, "fire", 4.0, DamageMode.FLAT);

        List<SetThresholdDamage> thresholds = service.damageThresholds("dragon-set");
        assertEquals(2, thresholds.size());
        assertEquals(2, thresholds.get(0).pieceCount());
        assertEquals(4, thresholds.get(1).pieceCount());
    }

    @Test
    void removeDamageThreshold() {
        service.create("dragon-set", "Sada draka");
        service.setDamageThreshold("dragon-set", 2, "fire", 4.0, DamageMode.FLAT);

        assertTrue(service.removeDamageThreshold("dragon-set", 2, "fire"));
        assertTrue(service.damageThresholds("dragon-set").isEmpty());
    }

    @Test
    void modifierThresholdSupportsWeakness() {
        service.create("dragon-set", "Sada draka");
        service.setModifierThreshold("dragon-set", 4, "frozen", -25.0);

        List<SetThresholdModifier> thresholds = service.modifierThresholds("dragon-set");
        assertEquals(1, thresholds.size());
        assertEquals(-25.0, thresholds.get(0).percent());
    }

    @Test
    void deleteSetCascadesMembersAndThresholds() {
        service.create("dragon-set", "Sada draka");
        createTemplate("helm");
        service.addMember("dragon-set", "helm");
        service.setDamageThreshold("dragon-set", 2, "fire", 4.0, DamageMode.FLAT);
        service.setModifierThreshold("dragon-set", 2, "frozen", 10.0);

        assertTrue(service.delete("dragon-set"));
        assertFalse(service.findByKey("dragon-set").isPresent());
    }

    @Test
    void listAllReturnsCreatedSets() {
        service.create("a-set", "A");
        service.create("b-set", "B");
        assertEquals(2, service.listAll().size());
    }
}
