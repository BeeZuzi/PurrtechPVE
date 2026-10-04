package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ItemUpgrades;
import eu.purrtech.purrtechPVE.item.UpgradeCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemUpgradeRepositoryTest {

    private Database database;
    private ItemUpgradeRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemUpgradeRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownItemHasNoUpgrades() {
        assertTrue(repository.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void setThenFindRoundTripsEveryCategory() {
        UUID id = UUID.randomUUID();
        repository.set(id, UpgradeCategory.DAMAGE, "slashing|WIELDED", 3);
        repository.set(id, UpgradeCategory.RESIST, "fire", 12.5);
        repository.set(id, UpgradeCategory.EFFECT, "CRIT_CHANCE", 7);

        ItemUpgrades found = repository.find(id);
        assertEquals(3.0, found.damage().get("slashing|WIELDED"));
        assertEquals(12.5, found.resist().get("fire"));
        assertEquals(7.0, found.effects().get("CRIT_CHANCE"));
    }

    @Test
    void aWriteIsVisibleDespiteTheCache() {
        UUID id = UUID.randomUUID();
        assertTrue(repository.find(id).isEmpty()); // caches "nothing"
        repository.set(id, UpgradeCategory.DAMAGE, "fire|WORN", 4);

        assertEquals(4.0, repository.find(id).damage().get("fire|WORN"));
    }

    @Test
    void zeroRemovesTheEntry() {
        UUID id = UUID.randomUUID();
        repository.set(id, UpgradeCategory.RESIST, "fire", 9);
        repository.set(id, UpgradeCategory.RESIST, "fire", 0);

        assertTrue(repository.find(id).isEmpty());
    }

    @Test
    void itemsAreIndependent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repository.set(a, UpgradeCategory.DAMAGE, "fire|WIELDED", 5);

        assertTrue(repository.find(b).isEmpty());
        assertEquals(5.0, repository.find(a).damage().get("fire|WIELDED"));
    }
}
