package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTemplateRepositoryTest {

    private Database database;
    private ItemTemplateRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemTemplateRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private ItemTemplate sample(String key) {
        long now = 1_700_000_000_000L;
        return new ItemTemplate(UUID.randomUUID(), key, "Plamenný meč", Material.IRON_SWORD, null, null,
                false, List.of("HAND"), null, 1, 1, now, now, "console");
    }

    @Test
    void insertThenFindByKeyRoundTrips() {
        ItemTemplate template = sample("fire-sword");
        repository.insert(template);

        Optional<ItemTemplate> found = repository.findByKey("fire-sword");
        assertTrue(found.isPresent());
        assertEquals(template.id(), found.get().id());
        assertEquals(template.displayName(), found.get().displayName());
        assertEquals(Material.IRON_SWORD, found.get().baseMaterial());
        assertEquals(List.of("HAND"), found.get().allowedSlots());
        assertEquals(1, found.get().version());
    }

    @Test
    void findByIdRoundTrips() {
        ItemTemplate template = sample("frost-axe");
        repository.insert(template);

        Optional<ItemTemplate> found = repository.findById(template.id());
        assertTrue(found.isPresent());
        assertEquals("frost-axe", found.get().key());
    }

    @Test
    void updateBumpsPersistedVersionAndTimestamp() {
        ItemTemplate template = sample("bleed-dagger");
        repository.insert(template);

        ItemTemplate bumped = template.withBumpedVersion(1_700_000_500_000L);
        repository.update(bumped);

        ItemTemplate reloaded = repository.findByKey("bleed-dagger").orElseThrow();
        assertEquals(2, reloaded.version());
        assertEquals(1_700_000_500_000L, reloaded.updatedAt());
    }

    @Test
    void findAllReturnsEverythingOrderedByKey() {
        repository.insert(sample("z-item"));
        repository.insert(sample("a-item"));

        List<ItemTemplate> all = repository.findAll();
        assertEquals(2, all.size());
        assertEquals("a-item", all.get(0).key());
        assertEquals("z-item", all.get(1).key());
    }

    @Test
    void deleteRemovesTheRow() {
        repository.insert(sample("temp-item"));
        assertTrue(repository.delete("temp-item"));
        assertFalse(repository.findByKey("temp-item").isPresent());
    }

    @Test
    void deleteOfUnknownKeyReturnsFalse() {
        assertFalse(repository.delete("does-not-exist"));
    }

    @Test
    void armorClassRoundTripsAndDefaultsToNull() {
        ItemTemplate plain = sample("plain-item");
        repository.insert(plain);
        assertNull(repository.findByKey("plain-item").orElseThrow().armorClass());

        ItemTemplate withClass = new ItemTemplate(UUID.randomUUID(), "heavy-plate", "Heavy Plate", Material.NETHERITE_CHESTPLATE,
                null, null, false, List.of(), ArmorClass.HEAVY, 1, 1, 0L, 0L, "console");
        repository.insert(withClass);
        assertEquals(ArmorClass.HEAVY, repository.findByKey("heavy-plate").orElseThrow().armorClass());

        repository.update(withClass.withBumpedVersion(1L));
        assertEquals(ArmorClass.HEAVY, repository.findByKey("heavy-plate").orElseThrow().armorClass());
    }
}
