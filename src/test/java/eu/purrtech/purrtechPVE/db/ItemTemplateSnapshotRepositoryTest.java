package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateEnchantment;
import eu.purrtech.purrtechPVE.item.TemplateSnapshot;
import eu.purrtech.purrtechPVE.item.TypeModifier;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTemplateSnapshotRepositoryTest {

    private Database database;
    private ItemTemplateSnapshotRepository repository;
    private UUID templateId;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        database = new Database(tempDir);
        database.connect();
        repository = new ItemTemplateSnapshotRepository(database);

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "fire-sword", "Plamenný meč", Material.IRON_SWORD,
                null, false, List.of(), 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void insertThenFindRoundTripsEverything() {
        TemplateSnapshot snapshot = new TemplateSnapshot(templateId, "fire-sword", 2, "Plamenný meč",
                Material.IRON_SWORD, 42,
                List.of(new DamageContribution("fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED)),
                List.of(new TypeModifier("frozen", -25.0)),
                List.of(new TemplateEnchantment("minecraft:sharpness", 5)),
                123L);
        repository.insert(snapshot);

        TemplateSnapshot found = repository.find(templateId, 2).orElseThrow();
        assertEquals("fire-sword", found.templateKey());
        assertEquals(2, found.version());
        assertEquals(42, found.customModelData());
        assertEquals(1, found.damageContributions().size());
        assertEquals("fire", found.damageContributions().get(0).damageTypeKey());
        assertEquals(4.0, found.damageContributions().get(0).amount());
        assertEquals(1, found.typeModifiers().size());
        assertEquals(-25.0, found.typeModifiers().get(0).percent());
        assertEquals(1, found.enchantments().size());
        assertEquals("minecraft:sharpness", found.enchantments().get(0).enchantmentKey());
        assertEquals(5, found.enchantments().get(0).level());
    }

    @Test
    void emptyContributionsAndModifiersRoundTripAsEmptyLists() {
        TemplateSnapshot snapshot = new TemplateSnapshot(templateId, "fire-sword", 1, "Plamenný meč",
                Material.IRON_SWORD, null, List.of(), List.of(), List.of(), 0L);
        repository.insert(snapshot);

        TemplateSnapshot found = repository.find(templateId, 1).orElseThrow();
        assertTrue(found.damageContributions().isEmpty());
        assertTrue(found.typeModifiers().isEmpty());
        assertTrue(found.enchantments().isEmpty());
    }

    @Test
    void findOfMissingVersionIsEmpty() {
        assertEquals(Optional.empty(), repository.find(templateId, 99));
    }

    @Test
    void insertOnSameVersionReplaces() {
        repository.insert(new TemplateSnapshot(templateId, "fire-sword", 1, "A", Material.IRON_SWORD, null,
                List.of(), List.of(), List.of(), 0L));
        repository.insert(new TemplateSnapshot(templateId, "fire-sword", 1, "B", Material.IRON_SWORD, null,
                List.of(), List.of(), List.of(), 1L));

        assertEquals("B", repository.find(templateId, 1).orElseThrow().displayName());
    }
}
