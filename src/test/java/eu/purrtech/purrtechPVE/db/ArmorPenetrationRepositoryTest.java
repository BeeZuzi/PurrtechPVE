package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorPenetrationRepositoryTest {

    private Database database;
    private ArmorPenetrationRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ArmorPenetrationRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "test-axe", "Test Axe", List.of(), List.of(), Material.IRON_AXE,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownTemplateHasEmptyPenetration() {
        assertTrue(repository.findByTemplate(UUID.randomUUID()).isEmpty());
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.LIGHT, 10.0, true));

        List<ArmorPenetration> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals(ArmorClass.LIGHT, found.get(0).armorClass());
        assertEquals(10.0, found.get(0).amount());
    }

    @Test
    void differentArmorClassesCoexist() {
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.LIGHT, 10.0, true));
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.HEAVY, 25.0, true));

        List<ArmorPenetration> found = repository.findByTemplate(templateId);
        assertEquals(2, found.size());
    }

    @Test
    void upsertOnSameClassReplaces() {
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.MEDIUM, 5.0, true));
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.MEDIUM, 20.0, true));

        List<ArmorPenetration> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals(20.0, found.get(0).amount());
    }

    @Test
    void removeDeletesJustThatClass() {
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.LIGHT, 10.0, true));
        repository.upsert(templateId, new ArmorPenetration(ArmorClass.HEAVY, 25.0, true));

        assertTrue(repository.remove(templateId, ArmorClass.LIGHT));
        assertFalse(repository.remove(templateId, ArmorClass.LIGHT));

        List<ArmorPenetration> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals(ArmorClass.HEAVY, found.get(0).armorClass());
    }
}
