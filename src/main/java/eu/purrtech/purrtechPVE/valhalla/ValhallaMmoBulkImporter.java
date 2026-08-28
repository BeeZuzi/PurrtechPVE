package eu.purrtech.purrtechPVE.valhalla;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.purrtech.purrtechPVE.item.BaseItemSnapshots;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DuplicateTemplateKeyException;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bulk-imports every item ValhallaMMO has registered (its in-game {@code
 * /val items} list) in one go, straight off the {@code items.json} it
 * persists them to. Same philosophy as {@link ValhallaMmoImporter}: no
 * dependency on ValhallaMMO's plugin or API at all (it doesn't even need to
 * be installed on this server, just to have left that file behind), read
 * with vanilla Gson (bundled with the Paper server runtime, {@code
 * compileOnly} here) and Bukkit's own item-stack (de)serialization.
 *
 * <p>Verified against ValhallaMMO's own source ({@code
 * item/CustomItemRegistry.java}, {@code persistence/ItemStackGSONAdapter.java},
 * {@code persistence/GsonAdapter.java}, {@code utility/ItemUtils.java}): the
 * top-level JSON value is an array of {@code {id, item, modifiers}} objects,
 * where {@code item} is an {@code ItemStack} written through their own
 * {@code BukkitObjectOutputStream} and then base64-encoded in 76-char lines
 * with the standard alphabet ({@link Base64#getMimeDecoder()} tolerates the
 * line breaks fine), and each {@code modifiers} entry is shaped like
 * {@code {MOD_TYPE: <fully qualified class name>, DATA: {...}}}. Only
 * {@code DefaultAttributeAdd}'s {@code attribute}/{@code value} fields have
 * an equivalent in our model (see {@link ValhallaMmoImporter}'s damage/
 * resistance mapping tables) - read generically off the {@code DATA} object
 * by field name, without ever touching a real ValhallaMMO class.
 */
public final class ValhallaMmoBulkImporter {

    private static final String ATTRIBUTE_ADD_SUFFIX = ".DefaultAttributeAdd";

    private ValhallaMmoBulkImporter() {
    }

    /** Where ValhallaMMO writes its item registry, by filesystem convention - works even if ValhallaMMO isn't currently installed. */
    public static File defaultItemsFile(Plugin plugin) {
        return new File(plugin.getDataFolder().getParentFile(), "ValhallaMMO/items.json");
    }

    /** One ValhallaMMO item id's outcome. {@code reason} is the new template key on success, or why it was skipped on failure. */
    public record ItemImportOutcome(String valhallaId, boolean imported, String reason) {
    }

    public record BulkImportResult(List<ItemImportOutcome> outcomes, Set<String> skippedAttributes, int enchantsImported) {
        public long importedCount() {
            return outcomes.stream().filter(ItemImportOutcome::imported).count();
        }

        public long failedCount() {
            return outcomes.size() - importedCount();
        }
    }

    public static BulkImportResult importAll(File itemsFile, ItemTemplateService itemTemplateService,
                                              Set<String> allDamageTypeKeys, String createdBy) throws IOException {
        List<ItemImportOutcome> outcomes = new ArrayList<>();
        Set<String> skippedAttributes = new LinkedHashSet<>();
        int enchantsImported = 0;

        JsonElement root;
        try (FileReader reader = new FileReader(itemsFile, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        }
        if (root == null || !root.isJsonArray()) {
            return new BulkImportResult(outcomes, skippedAttributes, enchantsImported);
        }

        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String id = object.has("id") && !object.get("id").isJsonNull() ? object.get("id").getAsString() : null;
            if (id == null || id.isBlank()) {
                continue;
            }

            ItemStack decoded = decodeItem(object);
            if (decoded == null) {
                outcomes.add(new ItemImportOutcome(id, false, "could not decode the item stack"));
                continue;
            }

            // Deliberately NOT de-duplicated against other keys in this same batch: the sanitized
            // ValhallaMMO id IS the stable key, so re-running this import (e.g. after ValhallaMMO
            // gets new items added) correctly reports already-imported ones as duplicates below,
            // instead of piling up "-2", "-3", ... copies of them every time.
            String key = sanitizeKey(id);
            Map<String, Double> attributes = readAttributes(object);
            ValhallaMmoImporter.ImportResult mapped = ValhallaMmoImporter.fromAttributes(attributes, allDamageTypeKeys);
            skippedAttributes.addAll(mapped.skipped());

            String displayName = displayNameOf(decoded, id);
            Integer customModelData = decoded.hasItemMeta() && decoded.getItemMeta().hasCustomModelData()
                    ? decoded.getItemMeta().getCustomModelData() : null;
            byte[] baseItemSnapshot = BaseItemSnapshots.capture(decoded);
            List<String> customLore = BaseItemSnapshots.captureLore(decoded);
            try {
                itemTemplateService.create(key, decoded.getType(), customModelData, baseItemSnapshot, customLore, displayName, createdBy);
            } catch (DuplicateTemplateKeyException e) {
                outcomes.add(new ItemImportOutcome(id, false, "an item template with key '" + key + "' already exists"));
                continue;
            }
            for (DamageContribution c : mapped.contributions()) {
                itemTemplateService.setDamageContribution(key, c.damageTypeKey(), c.amount(), c.mode(), c.context());
            }
            for (TypeModifier m : mapped.modifiers()) {
                itemTemplateService.setTypeModifier(key, m.damageTypeKey(), m.percent());
            }
            for (Map.Entry<Enchantment, Integer> enchant : decoded.getEnchantments().entrySet()) {
                itemTemplateService.setEnchantment(key, enchant.getKey().getKey().toString(), enchant.getValue());
                enchantsImported++;
            }
            outcomes.add(new ItemImportOutcome(id, true, key));
        }
        return new BulkImportResult(outcomes, skippedAttributes, enchantsImported);
    }

    private static ItemStack decodeItem(JsonObject object) {
        if (!object.has("item") || object.get("item").isJsonNull()) {
            return null;
        }
        String raw = object.get("item").getAsString();
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(raw.trim());
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object read = in.readObject();
                return read instanceof ItemStack stack ? stack : null;
            }
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static Map<String, Double> readAttributes(JsonObject object) {
        Map<String, Double> attributes = new LinkedHashMap<>();
        if (!object.has("modifiers") || !object.get("modifiers").isJsonArray()) {
            return attributes;
        }
        for (JsonElement modifierElement : object.getAsJsonArray("modifiers")) {
            if (!modifierElement.isJsonObject()) {
                continue;
            }
            JsonObject modifier = modifierElement.getAsJsonObject();
            String modType = modifier.has("MOD_TYPE") && !modifier.get("MOD_TYPE").isJsonNull()
                    ? modifier.get("MOD_TYPE").getAsString() : "";
            if (!modType.endsWith(ATTRIBUTE_ADD_SUFFIX)) {
                continue;
            }
            JsonObject data = modifier.has("DATA") && modifier.get("DATA").isJsonObject()
                    ? modifier.getAsJsonObject("DATA") : null;
            if (data == null || !data.has("attribute") || data.get("attribute").isJsonNull()
                    || !data.has("value") || data.get("value").isJsonNull()) {
                continue;
            }
            attributes.put(data.get("attribute").getAsString(), data.get("value").getAsDouble());
        }
        return attributes;
    }

    private static String displayNameOf(ItemStack stack, String fallback) {
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.displayName() != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        }
        return fallback;
    }

    /** {@code "valhalla-" + <id, lowercased, non [a-z0-9_-] chars collapsed to '-'>} - stable across re-imports of the same id. */
    private static String sanitizeKey(String id) {
        String base = id.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "item";
        }
        return "valhalla-" + base;
    }
}
