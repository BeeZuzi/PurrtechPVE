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
import java.util.Arrays;
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
                         (template_id, version, template_key, display_name, custom_lore, hidden_headers, lore_order, base_material, custom_model_data,
                          damage_contributions, type_modifiers, enchantments, armor_penetration, bleed_effect,
                          critical_effect, attribute_modifiers, base_item_snapshot, created_at)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                     """)) {
            statement.setString(1, snapshot.templateId().toString());
            statement.setInt(2, snapshot.version());
            statement.setString(3, snapshot.templateKey());
            statement.setString(4, snapshot.displayName());
            statement.setString(5, ItemTemplateRepository.encodeLore(snapshot.customLore()));
            statement.setString(6, String.join(",", snapshot.hiddenHeaders()));
            statement.setString(7, String.join(",", snapshot.loreOrder()));
            statement.setString(8, snapshot.baseMaterial().name());
            if (snapshot.customModelData() != null) {
                statement.setInt(9, snapshot.customModelData());
            } else {
                statement.setNull(9, Types.INTEGER);
            }
            statement.setString(10, encodeContributions(snapshot.damageContributions()));
            statement.setString(11, encodeModifiers(snapshot.typeModifiers()));
            statement.setString(12, encodeEnchantments(snapshot.enchantments()));
            statement.setString(13, encodeArmorPenetration(snapshot.armorPenetration()));
            statement.setString(14, encodeBleedEffect(snapshot.bleedEffect()));
            statement.setString(15, encodeCriticalEffect(snapshot.criticalEffect()));
            statement.setString(16, encodeAttributeModifiers(snapshot.attributeModifiers()));
            if (snapshot.baseItemSnapshot() != null) {
                statement.setBytes(17, snapshot.baseItemSnapshot());
            } else {
                statement.setNull(17, Types.BLOB);
            }
            statement.setLong(18, snapshot.createdAt());
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

        String hiddenHeadersRaw = rs.getString("hidden_headers");
        List<String> hiddenHeaders = hiddenHeadersRaw == null || hiddenHeadersRaw.isBlank()
                ? List.of()
                : Arrays.asList(hiddenHeadersRaw.split(","));

        String loreOrderRaw = rs.getString("lore_order");
        List<String> loreOrder = loreOrderRaw == null || loreOrderRaw.isBlank()
                ? List.of()
                : Arrays.asList(loreOrderRaw.split(","));

        return new TemplateSnapshot(
                UUID.fromString(rs.getString("template_id")),
                rs.getString("template_key"),
                rs.getInt("version"),
                rs.getString("display_name"),
                ItemTemplateRepository.decodeLore(rs.getString("custom_lore")),
                hiddenHeaders,
                loreOrder,
                Material.valueOf(rs.getString("base_material")),
                rs.getBytes("base_item_snapshot"),
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
                .map(c -> String.join("|", c.damageTypeKey(), String.valueOf(c.amount()), c.mode().name(), c.context().name(),
                        String.valueOf(c.visible())))
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
                    DamageMode.valueOf(fields[2]), ModifierContext.valueOf(fields[3]), parseVisible(fields, 4)));
        }
        return out;
    }

    private static String encodeModifiers(List<TypeModifier> modifiers) {
        return modifiers.stream()
                .map(m -> m.damageTypeKey() + "|" + m.percent() + "|" + m.visible())
                .collect(Collectors.joining(";"));
    }

    private static List<TypeModifier> decodeModifiers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<TypeModifier> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new TypeModifier(fields[0], Double.parseDouble(fields[1]), parseVisible(fields, 2)));
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
                .map(p -> p.armorClass().name() + "|" + p.amount() + "|" + p.visible())
                .collect(Collectors.joining(";"));
    }

    private static List<ArmorPenetration> decodeArmorPenetration(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<ArmorPenetration> out = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] fields = entry.split("\\|");
            out.add(new ArmorPenetration(ArmorClass.valueOf(fields[0]), Double.parseDouble(fields[1]), parseVisible(fields, 2)));
        }
        return out;
    }

    private static String encodeBleedEffect(BleedEffect effect) {
        return effect == null ? null : effect.chancePercent() + "|" + effect.durationSeconds() + "|" + effect.damageAmount()
                + "|" + effect.mode() + "|" + effect.visible();
    }

    private static BleedEffect decodeBleedEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] fields = raw.split("\\|");
        double chancePercent = Double.parseDouble(fields[0]);
        double durationSeconds = Double.parseDouble(fields[1]);
        // A snapshot encoded before damageAmount/mode existed only has chance/duration(/visible) -
        // default to 0/FLAT, same "not complete yet" state BleedEffect.isComplete() gives any
        // bleed effect that simply hasn't had its damage set.
        boolean hasDamageFields = fields.length >= 5;
        double damageAmount = hasDamageFields ? Double.parseDouble(fields[2]) : 0;
        DamageMode mode = hasDamageFields ? DamageMode.valueOf(fields[3]) : DamageMode.FLAT;
        boolean visible = hasDamageFields ? parseVisible(fields, 4) : parseVisible(fields, 2);
        return new BleedEffect(chancePercent, durationSeconds, damageAmount, mode, visible);
    }

    private static String encodeCriticalEffect(CriticalEffect effect) {
        return effect == null ? null : effect.chancePercent() + "|" + effect.bonusDamagePercent() + "|" + effect.visible();
    }

    private static CriticalEffect decodeCriticalEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] fields = raw.split("\\|");
        return new CriticalEffect(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]), parseVisible(fields, 2));
    }

    private static String encodeAttributeModifiers(List<AttributeModifierEntry> attributeModifiers) {
        return attributeModifiers.stream()
                .map(a -> String.join("|", a.attribute().name(), String.valueOf(a.amount()), a.operation().name(), a.slot(),
                        String.valueOf(a.visible())))
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
                    AttributeModifier.Operation.valueOf(fields[2]), fields[3], parseVisible(fields, 4)));
        }
        return out;
    }

    /** {@code true} (the pre-visible-flag default) on a snapshot encoded before this field existed - see the visible-in-lore feature. */
    private static boolean parseVisible(String[] fields, int index) {
        return fields.length <= index || Boolean.parseBoolean(fields[index]);
    }
}
