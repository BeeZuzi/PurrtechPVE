package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import org.bukkit.Material;

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

public final class ItemTemplateRepository {

    private final Database database;

    public ItemTemplateRepository(Database database) {
        this.database = database;
    }

    public void insert(ItemTemplate template) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO item_templates
                         (id, key, display_name, custom_lore, hidden_headers, lore_order, base_material, base_item_snapshot, custom_model_data,
                          is_trinket, allowed_slots, armor_class, version, synced_version, created_at, updated_at, created_by)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                     """)) {
            bind(statement, template);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert item template " + template.key(), e);
        }
    }

    public void update(ItemTemplate template) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE item_templates
                     SET key = ?, display_name = ?, custom_lore = ?, hidden_headers = ?, lore_order = ?, base_material = ?, base_item_snapshot = ?,
                         custom_model_data = ?, is_trinket = ?, allowed_slots = ?, armor_class = ?, version = ?, synced_version = ?, updated_at = ?
                     WHERE id = ?
                     """)) {
            statement.setString(1, template.key());
            statement.setString(2, template.displayName());
            statement.setString(3, encodeLore(template.customLore()));
            statement.setString(4, String.join(",", template.hiddenHeaders()));
            statement.setString(5, String.join(",", template.loreOrder()));
            statement.setString(6, template.baseMaterial().name());
            if (template.baseItemSnapshot() != null) {
                statement.setBytes(7, template.baseItemSnapshot());
            } else {
                statement.setNull(7, Types.BLOB);
            }
            if (template.customModelData() != null) {
                statement.setInt(8, template.customModelData());
            } else {
                statement.setNull(8, Types.INTEGER);
            }
            statement.setInt(9, template.trinket() ? 1 : 0);
            statement.setString(10, String.join(",", template.allowedSlots()));
            statement.setString(11, template.armorClass() != null ? template.armorClass().name() : null);
            statement.setInt(12, template.version());
            statement.setInt(13, template.syncedVersion());
            statement.setLong(14, template.updatedAt());
            statement.setString(15, template.id().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update item template " + template.key(), e);
        }
    }

    public Optional<ItemTemplate> findByKey(String key) {
        return findOne("SELECT * FROM item_templates WHERE key = ?", key);
    }

    public Optional<ItemTemplate> findById(UUID id) {
        return findOne("SELECT * FROM item_templates WHERE id = ?", id.toString());
    }

    private Optional<ItemTemplate> findOne(String sql, String param) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to look up item template", e);
        }
    }

    public List<ItemTemplate> findAll() {
        List<ItemTemplate> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM item_templates ORDER BY key");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list item templates", e);
        }
        return out;
    }

    /** Cascades to item_damage_contribution/item_type_modifier/item_attribute_modifier via FK ON DELETE CASCADE. */
    public boolean delete(String key) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_templates WHERE key = ?")) {
            statement.setString(1, key);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete item template " + key, e);
        }
    }

    private void bind(PreparedStatement statement, ItemTemplate template) throws SQLException {
        statement.setString(1, template.id().toString());
        statement.setString(2, template.key());
        statement.setString(3, template.displayName());
        statement.setString(4, encodeLore(template.customLore()));
        statement.setString(5, String.join(",", template.hiddenHeaders()));
        statement.setString(6, String.join(",", template.loreOrder()));
        statement.setString(7, template.baseMaterial().name());
        if (template.baseItemSnapshot() != null) {
            statement.setBytes(8, template.baseItemSnapshot());
        } else {
            statement.setNull(8, Types.BLOB);
        }
        if (template.customModelData() != null) {
            statement.setInt(9, template.customModelData());
        } else {
            statement.setNull(9, Types.INTEGER);
        }
        statement.setInt(10, template.trinket() ? 1 : 0);
        statement.setString(11, String.join(",", template.allowedSlots()));
        statement.setString(12, template.armorClass() != null ? template.armorClass().name() : null);
        statement.setInt(13, template.version());
        statement.setInt(14, template.syncedVersion());
        statement.setLong(15, template.createdAt());
        statement.setLong(16, template.updatedAt());
        statement.setString(17, template.createdBy());
    }

    private ItemTemplate map(ResultSet rs) throws SQLException {
        String allowedSlotsRaw = rs.getString("allowed_slots");
        List<String> allowedSlots = allowedSlotsRaw == null || allowedSlotsRaw.isBlank()
                ? List.of()
                : Arrays.asList(allowedSlotsRaw.split(","));

        int customModelData = rs.getInt("custom_model_data");
        Integer customModelDataBoxed = rs.wasNull() ? null : customModelData;

        String armorClassRaw = rs.getString("armor_class");
        ArmorClass armorClass = armorClassRaw == null ? null : ArmorClass.valueOf(armorClassRaw);

        String hiddenHeadersRaw = rs.getString("hidden_headers");
        List<String> hiddenHeaders = hiddenHeadersRaw == null || hiddenHeadersRaw.isBlank()
                ? List.of()
                : Arrays.asList(hiddenHeadersRaw.split(","));

        String loreOrderRaw = rs.getString("lore_order");
        List<String> loreOrder = loreOrderRaw == null || loreOrderRaw.isBlank()
                ? List.of()
                : Arrays.asList(loreOrderRaw.split(","));

        return new ItemTemplate(
                UUID.fromString(rs.getString("id")),
                rs.getString("key"),
                rs.getString("display_name"),
                decodeLore(rs.getString("custom_lore")),
                hiddenHeaders,
                loreOrder,
                Material.valueOf(rs.getString("base_material")),
                rs.getBytes("base_item_snapshot"),
                customModelDataBoxed,
                rs.getInt("is_trinket") != 0,
                allowedSlots,
                armorClass,
                rs.getInt("version"),
                rs.getInt("synced_version"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getString("created_by")
        );
    }

    /** Newline-joined, not comma-joined like allowedSlots - lore lines are free MiniMessage text that could legitimately contain commas. */
    static String encodeLore(List<String> customLore) {
        return customLore.isEmpty() ? null : String.join("\n", customLore);
    }

    static List<String> decodeLore(String raw) {
        return raw == null || raw.isEmpty() ? List.of() : Arrays.asList(raw.split("\n", -1));
    }
}
