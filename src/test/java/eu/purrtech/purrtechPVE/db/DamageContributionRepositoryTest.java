package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ModifierContext;
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

class DamageContributionRepositoryTest {

    private Database database;
    private DamageContributionRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new DamageContributionRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "test-item", "Test Item", List.of(), Material.STICK,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void upsertThenFindRoundTrips() {
        repository.upsert(templateId, new DamageContribution("slashing", 6.0, DamageMode.FLAT, ModifierContext.WIELDED));

        List<DamageContribution> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals("slashing", found.get(0).damageTypeKey());
        assertEquals(6.0, found.get(0).amount());
        assertEquals(DamageMode.FLAT, found.get(0).mode());
        assertEquals(ModifierContext.WIELDED, found.get(0).context());
    }

    @Test
    void upsertOnSameTypeAndContextReplacesInsteadOfDuplicating() {
        repository.upsert(templateId, new DamageContribution("fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED));
        repository.upsert(templateId, new DamageContribution("fire", 9.0, DamageMode.FLAT, ModifierContext.WIELDED));

        List<DamageContribution> found = repository.findByTemplate(templateId);
        assertEquals(1, found.size());
        assertEquals(9.0, found.get(0).amount());
    }

    @Test
    void sameTypeDifferentContextIsIndependent() {
        repository.upsert(templateId, new DamageContribution("fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED));
        repository.upsert(templateId, new DamageContribution("fire", 2.0, DamageMode.PERCENT_OF_TOTAL, ModifierContext.WORN));

        assertEquals(2, repository.findByTemplate(templateId).size());
    }

    @Test
    void removeDeletesJustThatRow() {
        repository.upsert(templateId, new DamageContribution("frozen", 3.0, DamageMode.FLAT, ModifierContext.WIELDED));
        assertTrue(repository.remove(templateId, "frozen", ModifierContext.WIELDED));
        assertFalse(repository.remove(templateId, "frozen", ModifierContext.WIELDED));
        assertEquals(0, repository.findByTemplate(templateId).size());
    }
}
