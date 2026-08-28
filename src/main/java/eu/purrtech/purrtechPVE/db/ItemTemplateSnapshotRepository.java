package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;
import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.CriticalEffect;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateEnchantment;
import eu.purrtech.purrtechPVE.item.TemplateSnapshot;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists {@link TemplateSnapshot} rows - one per (template, version) ever
 * saved. Contribution/modifier lists are encoded as a simple {@code
 * key|field|field;key|field|field} string rather than JSON: damage type keys
 * and enum names are always plain words (enforced by the command layer's
 * {@code StringArgumentType.word()}), so a hand-rolled delimiter is enough
 * and avoids pulling in a JSON library for this alone.
 */
public final class ItemTemplateSnapshotRepository {

    private final Database database;

    public ItemTemplateSnapshotRepository(Database database) {
        this.database = database;
    }

    public void insert(TemplateSnapshot snapshot) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_template_snapshot
                         (template_id, version, template_key, display_name, base_material, custom_model_data,
                          damage_contributions, type_modifiers, enchantments, armor_penetration, bleed_effect,
                          critical_effect, attribute_modifiers, created_at)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                     """)) {
            statement.setString(1, snapshot.templateId().toString());
            statement.setInt(2, snapshot.version());
            statement.setString(3, snapshot.templateKey());
            statement.setString(4, snapshot.displayName());
            statement.setString(5, snapshot.baseMaterial().name());
            if (snapshot.customModelData() != null) {
                statement.setInt(6, snapshot.customModelData());
            } else {
                statement.setNull(6, Types.INTEGER);
            }
            statement.setString(7, encodeContributions(snapshot.damageContributions()));
            statement.setString(8, encodeModifiers(snapshot.typeModifiers()));
            statement.setString(9, encodeEnchantments(snapshot.enchantments()));
            statement.setString(10, encodeArmorPenetration(snapshot.armorPenetration()));
            statement.setString(11, encodeBleedEffect(snapshot.bleedEffect()));
            statement.setString(12, encodeCriticalEffect(snapshot.criticalEffect()));
            statement.setString(13, encodeAttributeModifiers(snapshot.attributeModifiers()));
            statement.setLong(14, snapshot.createdAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save snapshot v" + snapshot.version()
                    + " for template " + snapshot.templateId(), e);
        }
    }

    public Optional<TemplateSnapshot> find(UUID templateId, int version) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM item_template_snapshot WHERE template_id = ? AND version = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setInt(2, version);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load snapshot v" + version + " for template " + templateId, e);
        }
    }

    private TemplateSnapshot map(ResultSet rs) throws SQLException {
        int customModelData = rs.getInt("custom_model_data");
        Integer customModelDataBoxed = rs.wasNull() ? null : customModelData;

        return new TemplateSnapshot(
                UUID.fromString(rs.getString("template_id")),
                rs.getString("template_key"),
                rs.getInt("version"),
                rs.getString("display_name"),
                Material.valueOf(rs.getString("base_material")),
                customModelDataBoxed,
                decodeContributions(rs.getString("damage_contributions")),
                decodeModifiers(rs.getString("type_modifiers")),
                decodeEnchantments(rs.getString("enchantments")),
                decodeArmorPenetration(rs.getString("armor_penetration")),
                decodeBleedEffect(rs.getString("bleed_effect")),
                decodeCriticalEffect(rs.getString("critical_effect")),
                decodeAttributeModifiers(rs.getString("attribute_modifiers")),
                rs.getLong("created_at")
        );
    }

    private static String encodeContributions(List<DamageContribution> contributions) {
        return contributions.stream()
                .map(c -> String.join("|", c.damageTypeKey(), String.valueOf(c.amount()), c.mode().name(), c.context().name()))
                .collect(Collectors.joining(";"));
    }

    private static List<DamageContribution> decodeContributions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<DamageContribution> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new DamageContribution(fields[0], Double.parseDouble(fields[1]),
                    DamageMode.valueOf(fields[2]), ModifierContext.valueOf(fields[3])));
        }
        return out;
    }

    private static String encodeModifiers(List<TypeModifier> modifiers) {
        return modifiers.stream()
                .map(m -> m.damageTypeKey() + "|" + m.percent())
                .collect(Collectors.joining(";"));
    }

    private static List<TypeModifier> decodeModifiers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<TypeModifier> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new TypeModifier(fields[0], Double.parseDouble(fields[1])));
        }
        return out;
    }

    private static String encodeEnchantments(List<TemplateEnchantment> enchantments) {
        return enchantments.stream()
                .map(e -> e.enchantmentKey() + "|" + e.level())
                .collect(Collectors.joining(";"));
    }

    private static List<TemplateEnchantment> decodeEnchantments(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<TemplateEnchantment> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new TemplateEnchantment(fields[0], Integer.parseInt(fields[1])));
        }
        return out;
    }

    private static String encodeArmorPenetration(List<ArmorPenetration> armorPenetration) {
        return armorPenetration.stream()
                .map(p -> p.armorClass().name() + "|" + p.amount())
                .collect(Collectors.joining(";"));
    }

    private static List<ArmorPenetration> decodeArmorPenetration(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<ArmorPenetration> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new ArmorPenetration(ArmorClass.valueOf(fields[0]), Double.parseDouble(fields[1])));
        }
        return out;
    }

    private static String encodeBleedEffect(BleedEffect effect) {
        return effect == null ? null : effect.chancePercent() + "|" + effect.durationSeconds();
    }

    private static BleedEffect decodeBleedEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] fields = raw.split("\\|");
        return new BleedEffect(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]));
    }

    private static String encodeCriticalEffect(CriticalEffect effect) {
        return effect == null ? null : effect.chancePercent() + "|" + effect.bonusDamagePercent();
    }

    private static CriticalEffect decodeCriticalEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] fields = raw.split("\\|");
        return new CriticalEffect(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]));
    }

    private static String encodeAttributeModifiers(List<AttributeModifierEntry> attributeModifiers) {
        return attributeModifiers.stream()
                .map(a -> String.join("|", a.attribute().name(), String.valueOf(a.amount()), a.operation().name(), a.slot()))
                .collect(Collectors.joining(";"));
    }

    private static List<AttributeModifierEntry> decodeAttributeModifiers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<AttributeModifierEntry> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new AttributeModifierEntry(Attribute.valueOf(fields[0]), Double.parseDouble(fields[1]),
                    AttributeModifier.Operation.valueOf(fields[2]), fields[3]));
        }
        return out;
    }
}
