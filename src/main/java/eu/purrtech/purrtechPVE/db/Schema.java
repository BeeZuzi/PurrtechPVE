package eu.purrtech.purrtechPVE.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class Schema {

    private Schema() {
    }

    static void initialize(Database database) {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS damage_type_definitions (
                        key TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        icon_material TEXT NOT NULL,
                        color TEXT,
                        is_dot INTEGER NOT NULL DEFAULT 0,
                        dot_period_ticks INTEGER,
                        dot_tick_percent REAL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        description TEXT
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_templates (
                        id TEXT PRIMARY KEY,
                        key TEXT NOT NULL UNIQUE,
                        display_name TEXT NOT NULL,
                        base_material TEXT NOT NULL,
                        base_item_snapshot BLOB,
                        custom_model_data INTEGER,
                        is_trinket INTEGER NOT NULL DEFAULT 0,
                        allowed_slots TEXT,
                        version INTEGER NOT NULL DEFAULT 1,
                        synced_version INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        created_by TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_templates_key ON item_templates(key)");

            // Full computed state at every version a template has ever had - not just the
            // current one. Needed so a stack that was deliberately NOT pushed to circulation
            // (item.version bumped but item.synced_version left behind) can still be caught
            // up to exactly the last version that WAS pushed, rather than jumping straight to
            // whatever the live item_damage_contribution/item_type_modifier rows say now.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_template_snapshot (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        version INTEGER NOT NULL,
                        template_key TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        base_material TEXT NOT NULL,
                        custom_model_data INTEGER,
                        damage_contributions TEXT NOT NULL,
                        type_modifiers TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (template_id, version)
                    )
                    """);

            // damage_type_key is NOT a FK to damage_type_definitions: DamageTypeRegistry is
            // still in-memory-only (Fáze 1), that table stays unpopulated until a later
            // phase makes the registry DB-backed. Validity is enforced at the service layer
            // (ItemTemplateService.requireDamageType) against the in-memory registry instead.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_damage_contribution (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        damage_type_key TEXT NOT NULL,
                        amount REAL NOT NULL,
                        mode TEXT NOT NULL,
                        context TEXT NOT NULL,
                        PRIMARY KEY (template_id, damage_type_key, context)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_damage_contribution_template "
                    + "ON item_damage_contribution(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_type_modifier (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (template_id, damage_type_key)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_type_modifier_template "
                    + "ON item_type_modifier(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_attribute_modifier (
                        template_id TEXT NOT NULL REFERENCES item_templates(id) ON DELETE CASCADE,
                        attribute_key TEXT NOT NULL,
                        amount REAL NOT NULL,
                        operation TEXT NOT NULL,
                        context TEXT NOT NULL,
                        PRIMARY KEY (template_id, attribute_key, context)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_item_attribute_modifier_template "
                    + "ON item_attribute_modifier(template_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mob_damage_profile (
                        mythic_mob_internal_name TEXT NOT NULL,
                        damage_type_key TEXT NOT NULL,
                        percent REAL NOT NULL,
                        PRIMARY KEY (mythic_mob_internal_name, damage_type_key)
                    )
                    """);

            // item_data is a full serialized ItemStack (ItemStack#serializeAsBytes), same
            // approach as vanilla playerdata - a virtual per-player accessory slot isn't tied
            // to any one of our own templates, so it needs to store an arbitrary item, not
            // just a template reference.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_accessory_slots (
                        player_uuid TEXT NOT NULL,
                        slot_name TEXT NOT NULL,
                        item_data BLOB NOT NULL,
                        PRIMARY KEY (player_uuid, slot_name)
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database schema", e);
        }
    }
}
