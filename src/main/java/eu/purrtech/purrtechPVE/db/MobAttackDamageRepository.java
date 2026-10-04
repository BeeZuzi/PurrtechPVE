package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Typed damage a MythicMobs mob type deals with one named attack (the {@code id} of a
 * {@code pvedamage{id=...}} skill mechanic), as flat amounts per damage type key.
 */
public final class MobAttackDamageRepository {

    private final Database database;

    public MobAttackDamageRepository(Database database) {
        this.database = database;
    }

    /** Sets one type's amount; an amount of 0 or less removes the row instead of storing a useless zero. */
    public void set(String mythicMobInternalName, String attackId, String damageTypeKey, double amount) {
        if (amount <= 0) {
            remove(mythicMobInternalName, attackId, damageTypeKey);
            return;
        }
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR REPLACE INTO mob_attack_damage (mythic_mob_internal_name, attack_id, damage_type_key, amount)
                     VALUES (?,?,?,?)
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, attackId);
            statement.setString(3, damageTypeKey);
            statement.setDouble(4, amount);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save attack damage for " + mythicMobInternalName + "/" + attackId, e);
        }
    }

    public boolean remove(String mythicMobInternalName, String attackId, String damageTypeKey) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM mob_attack_damage
                     WHERE mythic_mob_internal_name = ? AND attack_id = ? AND damage_type_key = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, attackId);
            statement.setString(3, damageTypeKey);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove attack damage for " + mythicMobInternalName + "/" + attackId, e);
        }
    }

    /** damage type key -> flat amount, empty if the attack has nothing configured for this mob. */
    public Map<String, Double> findByAttack(String mythicMobInternalName, String attackId) {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT damage_type_key, amount FROM mob_attack_damage
                     WHERE mythic_mob_internal_name = ? AND attack_id = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            statement.setString(2, attackId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("damage_type_key"), rs.getDouble("amount"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load attack damage for " + mythicMobInternalName + "/" + attackId, e);
        }
        return out;
    }

    /** Every attack id this mob type has at least one configured damage type for, sorted. */
    public Set<String> findAttackIds(String mythicMobInternalName) {
        Set<String> out = new TreeSet<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT DISTINCT attack_id FROM mob_attack_damage WHERE mythic_mob_internal_name = ?
                     """)) {
            statement.setString(1, mythicMobInternalName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("attack_id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load attack ids for " + mythicMobInternalName, e);
        }
        return out;
    }
}
