package eu.purrtech.purrtechPVE.db;

import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player virtual accessory (trinket) slots - stored independently of
 * vanilla equipment/inventory, since these slots don't physically exist on
 * the player. Each slot holds a full serialized {@link ItemStack} (not a
 * template reference), since a player can put any item in there, not just
 * one of our own templates.
 */
public final class AccessoryRepository {

    private final Database database;

    public AccessoryRepository(Database database) {
        this.database = database;
    }

    /** slotName -> item, only for slots that actually hold something. */
    public Map<String, ItemStack> findAll(UUID playerUuid) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT slot_name, item_data FROM player_accessory_slots WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("slot_name"), ItemStack.deserializeBytes(rs.getBytes("item_data")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load accessory slots for " + playerUuid, e);
        }
        return out;
    }

    /** Replaces every slot for this player with exactly what's in {@code slots} - a slot missing from the map, or mapped to null/air, is cleared. */
    public void saveAll(UUID playerUuid, Map<String, ItemStack> slots) {
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM player_accessory_slots WHERE player_uuid = ?")) {
                delete.setString(1, playerUuid.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO player_accessory_slots (player_uuid, slot_name, item_data) VALUES (?,?,?)
                    """)) {
                for (Map.Entry<String, ItemStack> entry : slots.entrySet()) {
                    ItemStack stack = entry.getValue();
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    insert.setString(1, playerUuid.toString());
                    insert.setString(2, entry.getKey());
                    insert.setBytes(3, stack.serializeAsBytes());
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save accessory slots for " + playerUuid, e);
        }
    }
}
