package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.ModifierContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DamageContributionRepository {

    private final Database database;

    public DamageContributionRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, DamageContribution contribution) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_damage_contribution
                         (template_id, damage_type_key, amount, mode, context, visible)
                     VALUES (?,?,?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, contribution.damageTypeKey());
            statement.setDouble(3, contribution.amount());
            statement.setString(4, contribution.mode().name());
            statement.setString(5, contribution.context().name());
            statement.setBoolean(6, contribution.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save damage contribution for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId, String damageTypeKey, ModifierContext context) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM item_damage_contribution
                     WHERE template_id = ? AND damage_type_key = ? AND context = ?
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setString(2, damageTypeKey);
            statement.setString(3, context.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove damage contribution for template " + templateId, e);
        }
    }

    public List<DamageContribution> findByTemplate(UUID templateId) {
        List<DamageContribution> out = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT damage_type_key, amount, mode, context, visible FROM item_damage_contribution WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new DamageContribution(
                            rs.getString("damage_type_key"),
                            rs.getDouble("amount"),
                            DamageMode.valueOf(rs.getString("mode")),
                            ModifierContext.valueOf(rs.getString("context")),
                            rs.getBoolean("visible")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load damage contributions for template " + templateId, e);
        }
        return out;
    }
}
