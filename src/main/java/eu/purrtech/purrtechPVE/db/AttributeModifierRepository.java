package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.AttributeModifierEntry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AttributeModifierRepository {

    private final Database database;

    public AttributeModifierRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, AttributeModifierEntry entry) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_attribute_modifier
                         (template_id, attribute_key, amount, operation, slot_name)
                     VALUES (?,?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, entry.attribute().name());
            statement.setDouble(3, entry.amount());
            statement.setString(4, entry.operation().name());
            statement.setString(5, entry.slot());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save attribute modifier for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId, Attribute attribute, String slot) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_attribute_modifier
                     WHERE template_id = ? AND attribute_key = ? AND slot_name = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, attribute.name());
            statement.setString(3, slot);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove attribute modifier for template " + templateId, e);
        }
    }

    public List<AttributeModifierEntry> findByTemplate(UUID templateId) {
        List<AttributeModifierEntry> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT attribute_key, amount, operation, slot_name FROM item_attribute_modifier WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new AttributeModifierEntry(
                            Attribute.valueOf(rs.getString("attribute_key")),
                            rs.getDouble("amount"),
                            AttributeModifier.Operation.valueOf(rs.getString("operation")),
                            rs.getString("slot_name")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load attribute modifiers for template " + templateId, e);
        }
        return out;
    }
}
