package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.TemplateEnchantment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TemplateEnchantmentRepository {

    private final Database database;

    public TemplateEnchantmentRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, TemplateEnchantment enchantment) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_template_enchantment (template_id, enchantment_key, level)
                     VALUES (?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, enchantment.enchantmentKey());
            statement.setInt(3, enchantment.level());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save enchantment for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId, String enchantmentKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_template_enchantment WHERE template_id = ? AND enchantment_key = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, enchantmentKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove enchantment for template " + templateId, e);
        }
    }

    public List<TemplateEnchantment> findByTemplate(UUID templateId) {
        List<TemplateEnchantment> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT enchantment_key, level FROM item_template_enchantment WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new TemplateEnchantment(rs.getString("enchantment_key"), rs.getInt("level")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load enchantments for template " + templateId, e);
        }
        return out;
    }
}
