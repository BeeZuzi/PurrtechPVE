package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.itemset.ItemSet;
import eu.purrtech.purrtechPVE.itemset.SetThresholdModifier;
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

class ItemSetModifierThresholdRepositoryTest {

    private Database database;
    private ItemSetModifierThresholdRepository repository;
    private UUID setId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemSetModifierThresholdRepository(database);

        ItemSet set = new ItemSet(UUID.randomUUID(), "dragon-set", "Sada draka", 0L, 0L);
        new ItemSetRepository(database).insert(set);
        setId = set.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void upsertThenFindOrderedByPieceCount() {
        repository.upsert(setId, new SetThresholdModifier(4, "fire", 50.0));
        repository.upsert(setId, new SetThresholdModifier(2, "fire", 20.0));

        List<SetThresholdModifier> found = repository.findBySet(setId);
        assertEquals(2, found.size());
        assertEquals(2, found.get(0).pieceCount());
        assertEquals(4, found.get(1).pieceCount());
    }

    @Test
    void negativePercentIsStoredAsWeakness() {
        repository.upsert(setId, new SetThresholdModifier(2, "frozen", -15.0));
        assertEquals(-15.0, repository.findBySet(setId).get(0).percent());
    }

    @Test
    void upsertOnSamePieceCountAndTypeReplaces() {
        repository.upsert(setId, new SetThresholdModifier(2, "fire", 10.0));
        repository.upsert(setId, new SetThresholdModifier(2, "fire", 60.0));

        List<SetThresholdModifier> found = repository.findBySet(setId);
        assertEquals(1, found.size());
        assertEquals(60.0, found.get(0).percent());
    }

    @Test
    void removeDeletesJustThatEntry() {
        repository.upsert(setId, new SetThresholdModifier(2, "acid", 15.0));
        assertTrue(repository.remove(setId, 2, "acid"));
        assertFalse(repository.remove(setId, 2, "acid"));
        assertTrue(repository.findBySet(setId).isEmpty());
    }
}
