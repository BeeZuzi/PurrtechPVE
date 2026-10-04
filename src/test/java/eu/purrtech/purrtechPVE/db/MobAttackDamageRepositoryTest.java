package eu.purrtech.purrtechPVE.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobAttackDamageRepositoryTest {

    private Database database;
    private MobAttackDamageRepository repository;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new MobAttackDamageRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void unknownAttackHasNoDamage() {
        assertTrue(repository.findByAttack("ethereal_reaver", "sword").isEmpty());
        assertTrue(repository.findAttackIds("ethereal_reaver").isEmpty());
    }

    @Test
    void setThenFindRoundTripsPerType() {
        repository.set("ethereal_reaver", "sword", "slashing", 40);
        repository.set("ethereal_reaver", "sword", "shadow", 15.5);

        Map<String, Double> damage = repository.findByAttack("ethereal_reaver", "sword");
        assertEquals(2, damage.size());
        assertEquals(40.0, damage.get("slashing"));
        assertEquals(15.5, damage.get("shadow"));
    }

    @Test
    void setOnSameTypeReplaces() {
        repository.set("ethereal_reaver", "sword", "slashing", 40);
        repository.set("ethereal_reaver", "sword", "slashing", 25);

        assertEquals(Map.of("slashing", 25.0), repository.findByAttack("ethereal_reaver", "sword"));
    }

    @Test
    void zeroOrNegativeAmountRemovesTheRow() {
        repository.set("ethereal_reaver", "sword", "slashing", 40);
        repository.set("ethereal_reaver", "sword", "slashing", 0);

        assertTrue(repository.findByAttack("ethereal_reaver", "sword").isEmpty());
        assertTrue(repository.findAttackIds("ethereal_reaver").isEmpty());
    }

    @Test
    void attacksAndMobsAreIndependent() {
        repository.set("ethereal_reaver", "sword", "slashing", 40);
        repository.set("ethereal_reaver", "beam", "magic", 60);
        repository.set("fire_imp", "sword", "fire", 5);

        assertEquals(Map.of("slashing", 40.0), repository.findByAttack("ethereal_reaver", "sword"));
        assertEquals(Map.of("magic", 60.0), repository.findByAttack("ethereal_reaver", "beam"));
        assertEquals(Map.of("fire", 5.0), repository.findByAttack("fire_imp", "sword"));
        assertEquals(Set.of("beam", "sword"), repository.findAttackIds("ethereal_reaver"));
    }

    @Test
    void removeDeletesJustThatType() {
        repository.set("ethereal_reaver", "sword", "slashing", 40);
        repository.set("ethereal_reaver", "sword", "shadow", 10);

        assertTrue(repository.remove("ethereal_reaver", "sword", "slashing"));
        assertFalse(repository.remove("ethereal_reaver", "sword", "slashing"));
        assertEquals(Map.of("shadow", 10.0), repository.findByAttack("ethereal_reaver", "sword"));
    }
}
