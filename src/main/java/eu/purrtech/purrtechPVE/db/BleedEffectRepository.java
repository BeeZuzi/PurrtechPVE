package eu.purrtech.purrtechPVE.db;

import eu.purrtech.purrtechPVE.item.BleedEffect;
import eu.purrtech.purrtechPVE.item.DamageMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** A template's {@link BleedEffect}, at most one row per template (a weapon either has bleed-on-hit configured or doesn't). */
public final class BleedEffectRepository {

    private final Database database;

    public BleedEffectRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID templateId, BleedEffect effect) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO item_bleed_effect (template_id, chance_percent, duration_seconds, damage_amount, mode, visible)
                     VALUES (?,?,?,?,?,?)
                     """)) {
            statement.setString(1, templateId.toString());
            statement.setDouble(2, effect.chancePercent());
            statement.setDouble(3, effect.durationSeconds());
            statement.setDouble(4, effect.damageAmount());
            statement.setString(5, effect.mode().name());
            statement.setBoolean(6, effect.visible());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save bleed effect for template " + templateId, e);
        }
    }

    public boolean remove(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM item_bleed_effect WHERE template_id = ?")) {
            statement.setString(1, templateId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove bleed effect for template " + templateId, e);
        }
    }

    public Optional<BleedEffect> findByTemplate(UUID templateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT chance_percent, duration_seconds, damage_amount, mode, visible FROM item_bleed_effect WHERE template_id = ?
                     """)) {
            statement.setString(1, templateId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BleedEffect(rs.getDouble("chance_percent"), rs.getDouble("duration_seconds"),
                        rs.getDouble("damage_amount"), DamageMode.valueOf(rs.getString("mode")), rs.getBoolean("visible")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load bleed effect for template " + templateId, e);
        }
    }
}
