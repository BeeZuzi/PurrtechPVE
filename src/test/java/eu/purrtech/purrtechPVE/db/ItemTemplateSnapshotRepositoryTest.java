package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), "fire-sword", "Plamenný meč", List.of(), List.of(), Material.IRON_SWORD,
                null, null, false, List.of(), null, 1, 1, 0L, 0L, "console");
        new ItemTemplateRepository(database).insert(template);
        templateId = template.id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void insertThenFindRoundTripsEverything() {
        byte[] baseItemSnapshotBytes = {1, 2, 3};
        TemplateSnapshot snapshot = new TemplateSnapshot(templateId, "fire-sword", 2, "Plamenný meč", List.of(), List.of(),
                Material.IRON_SWORD, baseItemSnapshotBytes, 42,
                List.of(new DamageContribution("fire", 4.0, DamageMode.FLAT, ModifierContext.WIELDED, true)),
                List.of(new TypeModifier("frozen", -25.0, true)),
                List.of(new TemplateEnchantment("minecraft:sharpness", 5)),
                List.of(new ArmorPenetration(ArmorClass.HEAVY, 15.0, true)),
                new BleedEffect(25.0, 5.0, true),
                new CriticalEffect(15.0, 50.0, true),
                // NOT covered here: attributeModifiers round-tripping. Unlike every other list on
                // this record, AttributeModifierEntry holds a real org.bukkit.attribute.Attribute,
                // whose constants are backed by a live Bukkit registry (Attribute.<clinit> calls
                // RegistryAccess.registryAccess()) - referencing one at all in a plain JUnit run
                // (no server, no MockBukkit, per this project's convention) throws
                // ExceptionInInitializerError before the test body even starts. Verified live via
                // runServer instead - see PLAN.md.
                List.of(),
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
        assertEquals(1, found.armorPenetration().size());
        assertEquals(ArmorClass.HEAVY, found.armorPenetration().get(0).armorClass());
        assertEquals(15.0, found.armorPenetration().get(0).amount());
        assertEquals(25.0, found.bleedEffect().chancePercent());
        assertEquals(5.0, found.bleedEffect().durationSeconds());
        assertEquals(15.0, found.criticalEffect().chancePercent());
        assertEquals(50.0, found.criticalEffect().bonusDamagePercent());
        assertTrue(found.attributeModifiers().isEmpty());
        assertArrayEquals(baseItemSnapshotBytes, found.baseItemSnapshot());
    }

    @Test
    void emptyContributionsAndModifiersRoundTripAsEmptyLists() {
        TemplateSnapshot snapshot = new TemplateSnapshot(templateId, "fire-sword", 1, "Plamenný meč", List.of(), List.of(),
                Material.IRON_SWORD, null, null, List.of(), List.of(), List.of(), List.of(), null, null, List.of(), 0L);
        repository.insert(snapshot);

        TemplateSnapshot found = repository.find(templateId, 1).orElseThrow();
        assertTrue(found.damageContributions().isEmpty());
        assertTrue(found.typeModifiers().isEmpty());
        assertTrue(found.enchantments().isEmpty());
        assertTrue(found.armorPenetration().isEmpty());
        assertEquals(null, found.bleedEffect());
        assertEquals(null, found.criticalEffect());
        assertTrue(found.attributeModifiers().isEmpty());
        assertEquals(null, found.baseItemSnapshot());
    }

    @Test
    void findOfMissingVersionIsEmpty() {
        assertEquals(Optional.empty(), repository.find(templateId, 99));
    }

    @Test
    void insertOnSameVersionReplaces() {
        repository.insert(new TemplateSnapshot(templateId, "fire-sword", 1, "A", List.of(), List.of(), Material.IRON_SWORD, null, null,
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), 0L));
        repository.insert(new TemplateSnapshot(templateId, "fire-sword", 1, "B", List.of(), List.of(), Material.IRON_SWORD, null, null,
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), 1L));

        assertEquals("B", repository.find(templateId, 1).orElseThrow().displayName());
    }
}
