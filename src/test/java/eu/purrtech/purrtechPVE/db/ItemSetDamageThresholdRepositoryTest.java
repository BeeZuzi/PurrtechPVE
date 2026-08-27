package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.itemset.ItemSet;
import eu.purrtech.purrtechPVE.itemset.SetThresholdDamage;
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

class ItemSetDamageThresholdRepositoryTest {

    private Database database;
    private ItemSetDamageThresholdRepository repository;
    private UUID setId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemSetDamageThresholdRepository(database);

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
        repository.upsert(setId, new SetThresholdDamage(4, "fire", 10.0, DamageMode.FLAT));
        repository.upsert(setId, new SetThresholdDamage(2, "fire", 4.0, DamageMode.FLAT));

        List<SetThresholdDamage> found = repository.findBySet(setId);
        assertEquals(2, found.size());
        assertEquals(2, found.get(0).pieceCount());
        assertEquals(4, found.get(1).pieceCount());
    }

    @Test
    void upsertOnSamePieceCountAndTypeReplaces() {
        repository.upsert(setId, new SetThresholdDamage(2, "fire", 4.0, DamageMode.FLAT));
        repository.upsert(setId, new SetThresholdDamage(2, "fire", 9.0, DamageMode.FLAT));

        List<SetThresholdDamage> found = repository.findBySet(setId);
        assertEquals(1, found.size());
        assertEquals(9.0, found.get(0).amount());
    }

    @Test
    void samePieceCountDifferentTypeIsIndependent() {
        repository.upsert(setId, new SetThresholdDamage(2, "fire", 4.0, DamageMode.FLAT));
        repository.upsert(setId, new SetThresholdDamage(2, "frozen", 3.0, DamageMode.FLAT));
        assertEquals(2, repository.findBySet(setId).size());
    }

    @Test
    void removeDeletesJustThatEntry() {
        repository.upsert(setId, new SetThresholdDamage(2, "fire", 4.0, DamageMode.FLAT));
        assertTrue(repository.remove(setId, 2, "fire"));
        assertFalse(repository.remove(setId, 2, "fire"));
        assertTrue(repository.findBySet(setId).isEmpty());
    }
}
