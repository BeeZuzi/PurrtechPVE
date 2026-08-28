package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.TypeModifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TypeModifierRepository {

    private final Database database;

    public TypeModifierRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, TypeModifier modifier) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_type_modifier (template_id, damage_type_key, percent, visible)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, modifier.damageTypeKey());
            statement.setDouble(3, modifier.percent());
            statement.setBoolean(4, modifier.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save type modifier for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_type_modifier WHERE template_id = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, damageTypeKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove type modifier for template " + templateId, e);
        }
    }

    public List<TypeModifier> findByTemplate(UUID templateId) {
        List<TypeModifier> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT damage_type_key, percent, visible FROM item_type_modifier WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new TypeModifier(rs.getString("damage_type_key"), rs.getDouble("percent"), rs.getBoolean("visible")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load type modifiers for template " + templateId, e);
        }
        return out;
    }
}
