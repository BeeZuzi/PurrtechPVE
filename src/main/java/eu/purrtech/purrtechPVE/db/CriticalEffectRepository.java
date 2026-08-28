package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.CriticalEffect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** A template's {@link CriticalEffect}, at most one row per template (a weapon either has crit configured or doesn't). */
public final class CriticalEffectRepository {

    private final Database database;

    public CriticalEffectRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, CriticalEffect effect) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_critical_effect (template_id, chance_percent, bonus_damage_percent, visible)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setDouble(2, effect.chancePercent());
            statement.setDouble(3, effect.bonusDamagePercent());
            statement.setBoolean(4, effect.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save critical effect for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_critical_effect WHERE template_id = ?")) {
            statement.setString(1, templateId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove critical effect for template " + templateId, e);
        }
    }

    public Optional<CriticalEffect> findByTemplate(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT chance_percent, bonus_damage_percent, visible FROM item_critical_effect WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CriticalEffect(rs.getDouble("chance_percent"), rs.getDouble("bonus_damage_percent"), rs.getBoolean("visible")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load critical effect for template " + templateId, e);
        }
    }
}
