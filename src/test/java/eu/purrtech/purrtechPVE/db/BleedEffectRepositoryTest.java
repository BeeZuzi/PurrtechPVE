package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.BleedEffect;
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

class BleedEffectRepositoryTest {

    private Database database;
    private BleedEffectRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new BleedEffectRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "test-dagger", "Test Dagger", Material.IRON_SWORD,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownTemplateHasNoBleedEffect() {
        assertTrue(repository.findByTemplate(UUID.randomUUID()).isEmpty());
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert(templateId, new BleedEffect(25.0, 5.0));

        Optional<BleedEffect> found = repository.findByTemplate(templateId);
        assertTrue(found.isPresent());
        assertEquals(25.0, found.get().chancePercent());
        assertEquals(5.0, found.get().durationSeconds());
    }

    @Test
    void upsertReplacesTheExistingRow() {
        repository.upsert(templateId, new BleedEffect(10.0, 2.0));
        repository.upsert(templateId, new BleedEffect(50.0, 8.0));

        Optional<BleedEffect> found = repository.findByTemplate(templateId);
        assertEquals(50.0, found.get().chancePercent());
        assertEquals(8.0, found.get().durationSeconds());
    }

    @Test
    void removeDeletesTheRow() {
        repository.upsert(templateId, new BleedEffect(25.0, 5.0));
        assertTrue(repository.remove(templateId));
        assertFalse(repository.remove(templateId));
        assertTrue(repository.findByTemplate(templateId).isEmpty());
    }
}
