package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.itemset.ItemSet;
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

class ItemSetRepositoryTest {

    private Database database;
    private ItemSetRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemSetRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private ItemSet sample(String key) {
        long now = 1_700_000_000_000L;
        return new ItemSet(UUID.randomUUID(), key, "Sada draka", now, now);
    }

    @Test
    void insertThenFindByKeyRoundTrips() {
        ItemSet set = sample("dragon-set");
        repository.insert(set);

        Optional<ItemSet> found = repository.findByKey("dragon-set");
        assertTrue(found.isPresent());
        assertEquals(set.id(), found.get().id());
        assertEquals("Sada draka", found.get().displayName());
    }

    @Test
    void findByIdRoundTrips() {
        ItemSet set = sample("frost-set");
        repository.insert(set);
        assertEquals("frost-set", repository.findById(set.id()).orElseThrow().key());
    }

    @Test
    void findAllOrderedByKey() {
        repository.insert(sample("z-set"));
        repository.insert(sample("a-set"));

        List<ItemSet> all = repository.findAll();
        assertEquals(2, all.size());
        assertEquals("a-set", all.get(0).key());
        assertEquals("z-set", all.get(1).key());
    }

    @Test
    void deleteRemovesTheRow() {
        repository.insert(sample("temp-set"));
        assertTrue(repository.delete("temp-set"));
        assertFalse(repository.findByKey("temp-set").isPresent());
    }

    @Test
    void deleteOfUnknownKeyReturnsFalse() {
        assertFalse(repository.delete("does-not-exist"));
    }
}
