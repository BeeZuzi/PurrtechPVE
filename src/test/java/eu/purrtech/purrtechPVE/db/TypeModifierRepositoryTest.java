package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.TypeModifier;
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

class TypeModifierRepositoryTest {

    private Database database;
    private TypeModifierRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new TypeModifierRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "test-armor", "Test Armor", Material.IRON_CHESTPLATE,
                null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert(templateId, new TypeModifier("fire", 40.0));

        List<TypeModifier> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals("fire", found.get(0).damageTypeKey());
        assertEquals(40.0, found.get(0).percent());
    }

    @Test
    void negativePercentIsStoredAsWeakness() {
        repository.upsert(templateId, new TypeModifier("frozen", -25.0));
        assertEquals(-25.0, repository.findByTemplate(templateId).get(0).percent());
    }

    @Test
    void upsertOnSameTypeReplaces() {
        repository.upsert(templateId, new TypeModifier("fire", 10.0));
        repository.upsert(templateId, new TypeModifier("fire", 60.0));

        List<TypeModifier> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals(60.0, found.get(0).percent());
    }

    @Test
    void removeDeletesTheRow() {
        repository.upsert(templateId, new TypeModifier("acid", 15.0));
        assertTrue(repository.remove(templateId, "acid"));
        assertFalse(repository.remove(templateId, "acid"));
        assertEquals(0, repository.findByTemplate(templateId).size());
    }
}
