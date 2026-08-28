package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalEffectRepositoryTest {

    private Database database;
    private CriticalEffectRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new CriticalEffectRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "test-rapier", "Test Rapier", Material.IRON_SWORD,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownTemplateHasNoCriticalEffect() {
        assertTrue(repository.findByTemplate(UUID.randomUUID()).isEmpty());
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert(templateId, new CriticalEffect(15.0, 50.0));

        Optional<CriticalEffect> found = repository.findByTemplate(templateId);
        assertTrue(found.isPresent());
        assertEquals(15.0, found.get().chancePercent());
        assertEquals(50.0, found.get().bonusDamagePercent());
    }

    @Test
    void upsertReplacesTheExistingRow() {
        repository.upsert(templateId, new CriticalEffect(10.0, 20.0));
        repository.upsert(templateId, new CriticalEffect(30.0, 100.0));

        Optional<CriticalEffect> found = repository.findByTemplate(templateId);
        assertEquals(30.0, found.get().chancePercent());
        assertEquals(100.0, found.get().bonusDamagePercent());
    }

    @Test
    void removeDeletesTheRow() {
        repository.upsert(templateId, new CriticalEffect(15.0, 50.0));
        assertTrue(repository.remove(templateId));
        assertFalse(repository.remove(templateId));
        assertTrue(repository.findByTemplate(templateId).isEmpty());
    }
}
