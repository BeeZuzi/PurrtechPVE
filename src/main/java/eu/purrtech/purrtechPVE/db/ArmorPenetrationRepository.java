package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.ArmorPenetration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ArmorPenetrationRepository {

    private final Database database;

    public ArmorPenetrationRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, ArmorPenetration penetration) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_armor_penetration (template_id, armor_class, amount)
                     VALUES (?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, penetration.armorClass().name());
            statement.setDouble(3, penetration.amount());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save armor penetration for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId, ArmorClass armorClass) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_armor_penetration WHERE template_id = ? AND armor_class = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, armorClass.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove armor penetration for template " + templateId, e);
        }
    }

    public List<ArmorPenetration> findByTemplate(UUID templateId) {
        List<ArmorPenetration> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT armor_class, amount FROM item_armor_penetration WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new ArmorPenetration(ArmorClass.valueOf(rs.getString("armor_class")), rs.getDouble("amount")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load armor penetration for template " + templateId, e);
        }
        return out;
    }
}
