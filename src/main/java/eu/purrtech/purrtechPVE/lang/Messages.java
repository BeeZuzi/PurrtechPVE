package eu.purrtech.purrtechPVE.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Two flat key -> MiniMessage-template maps (cs/en), picked per-player by
 * their client locale. English is the fallback for any key missing from
 * cs.yml, and for any client locale that isn't Czech.
 *
 * <p>Extracted to {@code plugins/PurrtechPVE/lang/*.yml} on first run (same
 * {@code saveResource} pattern as {@code config.yml}) so an admin can
 * actually edit colors/text without rebuilding the plugin - loading straight
 * from the bundled jar resource, as this used to do, meant an on-disk copy
 * would just be silently ignored. Every key is still layered on top of the
 * bundled defaults rather than read from disk alone, so a plugin update that
 * adds new keys (like this GUI-localization pass did) doesn't leave an
 * older on-disk file missing them - same reasoning as {@code
 * Schema.addColumnIfMissing} for the DB.
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<String, String> cs;
    private final Map<String, String> en;

    Messages(Map<String, String> cs, Map<String, String> en) {
        this.cs = cs;
        this.en = en;
    }

    public static Messages load(Plugin plugin) {
        return new Messages(loadFlat(plugin, "lang/cs.yml"), loadFlat(plugin, "lang/en.yml"));
    }

    private static Map<String, String> loadFlat(Plugin plugin, String resourcePath) {
        Map<String, String> merged = new LinkedHashMap<>(flatten(bundledDefaults(plugin, resourcePath)));

        File diskFile = new File(plugin.getDataFolder(), resourcePath);
        if (!diskFile.exists()) {
            // false = don't overwrite - irrelevant on first run (the file doesn't exist yet),
            // but keeps this safe to call again without clobbering admin edits.
            plugin.saveResource(resourcePath, false);
        }
        if (diskFile.exists()) {
            merged.putAll(flatten(YamlConfiguration.loadConfiguration(diskFile)));
        }
        return merged;
    }

    private static YamlConfiguration bundledDefaults(Plugin plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + resourcePath);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourcePath, e);
        }
    }

    static Map<String, String> flatten(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>();
        flattenInto(section, "", out);
        return out;
    }

    private static void flattenInto(ConfigurationSection section, String prefix, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flattenInto(section.getConfigurationSection(key), path, out);
            } else {
                out.put(path, section.getString(key));
            }
        }
    }

    /**
     * Raw localized text for a key with no MiniMessage tags of its own,
     * meant to be composed as a placeholder value into another template.
     */
    public String plain(Locale locale, String key) {
        return template(locale, key);
    }

    String template(Locale locale, String key) {
        Map<String, String> table = "cs".equals(locale.getLanguage()) ? cs : en;
        String value = table.get(key);
        if (value != null) {
            return value;
        }
        return en.getOrDefault(key, key);
    }

    /**
     * All interpolated values should use {@link net.kyori.adventure.text.minimessage.tag.resolver.Placeholder#unparsed}
     * rather than parsed(), since some of them (player names) are player-
     * controlled and must not be re-parsed as MiniMessage markup.
     */
    public Component render(Locale locale, String key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(template(locale, key), placeholders);
    }
}
