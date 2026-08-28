package eu.purrtech.purrtechPVE.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.purrtech.purrtechPVE.PurrtechPVE;
import eu.purrtech.purrtechPVE.gui.ItemEditorMenu;
import eu.purrtech.purrtechPVE.gui.ItemEditorTab;
import eu.purrtech.purrtechPVE.gui.ItemListMenu;
import eu.purrtech.purrtechPVE.gui.SetEditorMenu;
import eu.purrtech.purrtechPVE.gui.SetEditorTab;
import eu.purrtech.purrtechPVE.gui.ArmorClassMenu;
import eu.purrtech.purrtechPVE.item.ArmorClass;
import eu.purrtech.purrtechPVE.item.AttributeSlots;
import eu.purrtech.purrtechPVE.item.DamageContribution;
import eu.purrtech.purrtechPVE.item.DamageMode;
import eu.purrtech.purrtechPVE.item.DuplicateTemplateKeyException;
import eu.purrtech.purrtechPVE.item.ItemTemplate;
import eu.purrtech.purrtechPVE.item.ItemTemplateService;
import eu.purrtech.purrtechPVE.item.ModifierContext;
import eu.purrtech.purrtechPVE.item.TemplateNotFoundException;
import eu.purrtech.purrtechPVE.item.TypeModifier;
import eu.purrtech.purrtechPVE.item.UnknownDamageTypeException;
import eu.purrtech.purrtechPVE.itemset.DuplicateSetKeyException;
import eu.purrtech.purrtechPVE.itemset.ItemSet;
import eu.purrtech.purrtechPVE.itemset.ItemSetNotFoundException;
import eu.purrtech.purrtechPVE.trinket.AccessoryMenu;
import eu.purrtech.purrtechPVE.valhalla.ValhallaMmoBulkImporter;
import eu.purrtech.purrtechPVE.valhalla.ValhallaMmoImporter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /pve item ...} - template CRUD + give, command-line only (Fáze 2,
 * ahead of the GUI editor in a later phase).
 */
public final class PveCommand {

    private static final String PERMISSION = "purrtechpve.admin";

    // Vanilla EquipmentSlotGroup names (see AttributeSlots) - hardcoded rather than enumerated,
    // since EquipmentSlotGroup isn't a real enum and exposes no values()/all-instances accessor.
    private static final List<String> VANILLA_SLOT_GROUP_NAMES = List.of(
            "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body", "any", "saddle");

    private static final SuggestionProvider<CommandSourceStack> MATERIAL_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (Material material : Material.values()) {
            if (!material.isItem()) {
                continue;
            }
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> ATTRIBUTE_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (Attribute attribute : Attribute.values()) {
            String name = attribute.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> OPERATION_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
            String name = operation.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private PveCommand() {
    }

    /** Vanilla slot group names + this server's own configured trinket slot names - see {@link AttributeSlots}. */
    private static SuggestionProvider<CommandSourceStack> slotSuggestions(PurrtechPVE plugin) {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            List<String> options = new ArrayList<>(VANILLA_SLOT_GROUP_NAMES);
            options.addAll(plugin.getAccessorySettings().slots());
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(option);
                }
            }
            return builder.buildFuture();
        };
    }

    /** Existing template keys - "give me a list of those items" for commands that target an already-created template. */
    private static SuggestionProvider<CommandSourceStack> templateKeySuggestions(PurrtechPVE plugin) {
        return (ctx, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            for (ItemTemplate template : plugin.getItemTemplateService().listAll()) {
                if (template.key().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(template.key());
                }
            }
            return builder.buildFuture();
        };
    }

    public static LiteralCommandNode<CommandSourceStack> create(PurrtechPVE plugin) {
        return Commands.literal("pve")
                .then(Commands.literal("item")
                        .requires(source -> source.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("create")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .suggests(MATERIAL_SUGGESTIONS)
                                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                        .executes(ctx -> createTemplate(plugin, ctx))))))
                        .then(Commands.literal("replace")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(templateKeySuggestions(plugin))
                                        .executes(ctx -> replaceTemplateBase(plugin, ctx))))
                        .then(Commands.literal("attribute")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("attribute", StringArgumentType.word())
                                                        .suggests(ATTRIBUTE_SUGGESTIONS)
                                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                                .suggests(slotSuggestions(plugin))
                                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("operation", StringArgumentType.word())
                                                                                .suggests(OPERATION_SUGGESTIONS)
                                                                                .executes(ctx -> setItemAttributeModifier(plugin, ctx))))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("attribute", StringArgumentType.word())
                                                        .suggests(ATTRIBUTE_SUGGESTIONS)
                                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                                .suggests(slotSuggestions(plugin))
                                                                .executes(ctx -> removeItemAttributeModifier(plugin, ctx)))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> deleteTemplate(plugin, ctx))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listTemplates(plugin, ctx)))
                        .then(Commands.literal("menu")
                                .executes(ctx -> openItemListMenu(plugin, ctx)))
                        .then(Commands.literal("import")
                                .then(Commands.literal("valhalla")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                        .executes(ctx -> importFromValhalla(plugin, ctx)))))
                                .then(Commands.literal("valhallaall")
                                        .executes(ctx -> bulkImportFromValhalla(plugin, ctx))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> editTemplate(plugin, ctx))))
                        .then(Commands.literal("setbase")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> setBaseFromHand(plugin, ctx))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(ctx -> giveTemplate(plugin, ctx)))))
                        .then(Commands.literal("sync")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> syncTemplate(plugin, ctx))))
                        .then(Commands.literal("damage")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                                        .then(Commands.argument("context", StringArgumentType.word())
                                                                                .executes(ctx -> setDamageContribution(plugin, ctx))))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                        .then(Commands.argument("context", StringArgumentType.word())
                                                                .executes(ctx -> removeDamageContribution(plugin, ctx)))))))
                        .then(Commands.literal("resist")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> setTypeModifier(plugin, ctx))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                        .executes(ctx -> removeTypeModifier(plugin, ctx))))))
                        .then(Commands.literal("enchant")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("enchantment", StringArgumentType.string())
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> setEnchantment(plugin, ctx))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("enchantment", StringArgumentType.string())
                                                        .executes(ctx -> removeEnchantment(plugin, ctx))))))
                        .then(Commands.literal("slots")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("slots", StringArgumentType.greedyString())
                                                .executes(ctx -> setAllowedSlots(plugin, ctx)))))
                        .then(Commands.literal("armor")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("armorClass", StringArgumentType.word())
                                                .executes(ctx -> setItemArmorClass(plugin, ctx)))))
                        .then(Commands.literal("penetration")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("armorClass", StringArgumentType.word())
                                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> setItemArmorPenetration(plugin, ctx))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("armorClass", StringArgumentType.word())
                                                        .executes(ctx -> removeItemArmorPenetration(plugin, ctx))))))
                        .then(Commands.literal("bleed")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("chancePercent", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("durationSeconds", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> setItemBleedEffect(plugin, ctx))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(ctx -> removeItemBleedEffect(plugin, ctx)))))
                        .then(Commands.literal("crit")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("chancePercent", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("bonusDamagePercent", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> setItemCriticalEffect(plugin, ctx))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .executes(ctx -> removeItemCriticalEffect(plugin, ctx))))))
                .then(Commands.literal("accessory")
                        .requires(source -> source.getSender().hasPermission("purrtechpve.accessory.use"))
                        .executes(ctx -> openAccessoryMenu(plugin, ctx)))
                .then(Commands.literal("armorclass")
                        .requires(source -> source.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("set")
                                .then(Commands.argument("armorClass", StringArgumentType.word())
                                        .then(Commands.argument("damageType", StringArgumentType.word())
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> setArmorClassProfile(plugin, ctx))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("armorClass", StringArgumentType.word())
                                        .then(Commands.argument("damageType", StringArgumentType.word())
                                                .executes(ctx -> removeArmorClassProfile(plugin, ctx)))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("armorClass", StringArgumentType.word())
                                        .executes(ctx -> listArmorClassProfile(plugin, ctx))))
                        .then(Commands.literal("menu")
                                .executes(ctx -> openArmorClassMenu(plugin, ctx))))
                .then(Commands.literal("mobprofile")
                        .requires(source -> source.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("set")
                                .then(Commands.argument("mythicMobType", StringArgumentType.word())
                                        .then(Commands.argument("damageType", StringArgumentType.word())
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> setMobProfile(plugin, ctx))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("mythicMobType", StringArgumentType.word())
                                        .then(Commands.argument("damageType", StringArgumentType.word())
                                                .executes(ctx -> removeMobProfile(plugin, ctx)))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("mythicMobType", StringArgumentType.word())
                                        .executes(ctx -> listMobProfile(plugin, ctx)))))
                .then(Commands.literal("set")
                        .requires(source -> source.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("create")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                .executes(ctx -> createSet(plugin, ctx)))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> deleteSet(plugin, ctx))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listSets(plugin, ctx)))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(ctx -> editSet(plugin, ctx))))
                        .then(Commands.literal("addmember")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("templateKey", StringArgumentType.word())
                                                .executes(ctx -> addSetMember(plugin, ctx)))))
                        .then(Commands.literal("removemember")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("templateKey", StringArgumentType.word())
                                                .executes(ctx -> removeSetMember(plugin, ctx)))))
                        .then(Commands.literal("threshold")
                                .then(Commands.literal("damage")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("key", StringArgumentType.word())
                                                        .then(Commands.argument("pieceCount", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                                                        .executes(ctx -> setSetDamageThreshold(plugin, ctx))))))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("key", StringArgumentType.word())
                                                        .then(Commands.argument("pieceCount", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                                        .executes(ctx -> removeSetDamageThreshold(plugin, ctx)))))))
                                .then(Commands.literal("resist")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("key", StringArgumentType.word())
                                                        .then(Commands.argument("pieceCount", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg())
                                                                                .executes(ctx -> setSetModifierThreshold(plugin, ctx)))))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("key", StringArgumentType.word())
                                                        .then(Commands.argument("pieceCount", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("damageType", StringArgumentType.word())
                                                                        .executes(ctx -> removeSetModifierThreshold(plugin, ctx)))))))))
                .build();
    }

    private static int createTemplate(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        String materialArg = StringArgumentType.getString(ctx, "material");
        String displayName = StringArgumentType.getString(ctx, "displayName");

        Material material = Material.matchMaterial(materialArg);
        if (material == null) {
            sender.sendMessage(plugin.getMessages().render(localeOf(plugin, sender), "error.invalid-material",
                    Placeholder.unparsed("material", materialArg)));
            return 0;
        }

        String createdBy = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        try {
            plugin.getItemTemplateService().create(key, material, displayName, createdBy);
        } catch (DuplicateTemplateKeyException e) {
            sender.sendMessage(plugin.getMessages().render(localeOf(plugin, sender), "item.duplicate-key",
                    Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(localeOf(plugin, sender), "item.created",
                Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int deleteTemplate(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        boolean deleted = plugin.getItemTemplateService().delete(key);
        String messageKey = deleted ? "item.deleted" : "item.not-found";
        sender.sendMessage(plugin.getMessages().render(localeOf(plugin, sender), messageKey, Placeholder.unparsed("key", key)));
        return deleted ? Command.SINGLE_SUCCESS : 0;
    }

    private static int listTemplates(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        List<ItemTemplate> templates = plugin.getItemTemplateService().listAll();
        if (templates.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.list-empty"));
            return Command.SINGLE_SUCCESS;
        }
        for (ItemTemplate template : templates) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.list-entry",
                    Placeholder.unparsed("key", template.key()),
                    Placeholder.unparsed("material", template.baseMaterial().name()),
                    Placeholder.unparsed("version", String.valueOf(template.version()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int openItemListMenu(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        ItemListMenu.open(plugin, player, 0);
        return Command.SINGLE_SUCCESS;
    }

    private static int importFromValhalla(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        Locale locale = player.locale();
        String key = StringArgumentType.getString(ctx, "key");
        String displayName = StringArgumentType.getString(ctx, "displayName");

        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<String> rawStats = ValhallaMmoImporter.readRawStats(held);
        if (rawStats.isEmpty()) {
            player.sendMessage(plugin.getMessages().render(locale, "item.import-no-stats"));
            return 0;
        }

        ValhallaMmoImporter.ImportResult result = ValhallaMmoImporter.parse(rawStats.get(),
                plugin.getDamageTypeRegistry().all().keySet());
        Integer customModelData = held.hasItemMeta() && held.getItemMeta().hasCustomModelData()
                ? held.getItemMeta().getCustomModelData() : null;
        try {
            plugin.getItemTemplateService().create(key, held.getType(), customModelData, displayName,
                    player.getUniqueId().toString());
        } catch (DuplicateTemplateKeyException e) {
            player.sendMessage(plugin.getMessages().render(locale, "item.duplicate-key", Placeholder.unparsed("key", key)));
            return 0;
        }
        for (DamageContribution c : result.contributions()) {
            plugin.getItemTemplateService().setDamageContribution(key, c.damageTypeKey(), c.amount(), c.mode(), c.context());
        }
        for (TypeModifier m : result.modifiers()) {
            plugin.getItemTemplateService().setTypeModifier(key, m.damageTypeKey(), m.percent());
        }
        int enchantCount = 0;
        int attributeCount = 0;
        if (held.hasItemMeta()) {
            for (Map.Entry<Enchantment, Integer> enchant : held.getItemMeta().getEnchants().entrySet()) {
                plugin.getItemTemplateService().setEnchantment(key, enchant.getKey().getKey().toString(), enchant.getValue());
                enchantCount++;
            }
            // Copied 1:1, not translated through ValhallaMMO's attribute tables above - whatever
            // real vanilla attribute modifiers the held item already carries (from an anvil,
            // another plugin, /give with components, ...) come over exactly as they are, slot
            // group and all, same as the enchantments above.
            if (held.getItemMeta().hasAttributeModifiers()) {
                for (Map.Entry<Attribute, AttributeModifier> entry : held.getItemMeta().getAttributeModifiers().entries()) {
                    AttributeModifier modifier = entry.getValue();
                    plugin.getItemTemplateService().setAttributeModifier(key, entry.getKey(), modifier.getAmount(),
                            modifier.getOperation(), modifier.getSlotGroup().toString());
                    attributeCount++;
                }
            }
        }

        player.sendMessage(plugin.getMessages().render(locale, "item.import-done",
                Placeholder.unparsed("key", key),
                Placeholder.unparsed("damage", String.valueOf(result.contributions().size())),
                Placeholder.unparsed("resist", String.valueOf(result.modifiers().size())),
                Placeholder.unparsed("enchants", String.valueOf(enchantCount)),
                Placeholder.unparsed("attributes", String.valueOf(attributeCount))));
        if (!result.skipped().isEmpty()) {
            player.sendMessage(plugin.getMessages().render(locale, "item.import-skipped",
                    Placeholder.unparsed("attributes", String.join(", ", result.skipped()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int bulkImportFromValhalla(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = sender instanceof Player player ? player.locale() : plugin.getDefaultLocale();
        String createdBy = sender instanceof Player player ? player.getUniqueId().toString() : "console";

        File itemsFile = ValhallaMmoBulkImporter.defaultItemsFile(plugin);
        if (!itemsFile.isFile()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.bulk-import-file-missing",
                    Placeholder.unparsed("path", itemsFile.getPath())));
            return 0;
        }

        ValhallaMmoBulkImporter.BulkImportResult result;
        try {
            result = ValhallaMmoBulkImporter.importAll(itemsFile, plugin.getItemTemplateService(),
                    plugin.getDamageTypeRegistry().all().keySet(), createdBy);
        } catch (IOException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.bulk-import-error",
                    Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
            return 0;
        }

        sender.sendMessage(plugin.getMessages().render(locale, "item.bulk-import-done",
                Placeholder.unparsed("imported", String.valueOf(result.importedCount())),
                Placeholder.unparsed("failed", String.valueOf(result.failedCount())),
                Placeholder.unparsed("total", String.valueOf(result.outcomes().size())),
                Placeholder.unparsed("enchants", String.valueOf(result.enchantsImported()))));
        List<String> failedIds = result.outcomes().stream()
                .filter(o -> !o.imported())
                .map(o -> o.valhallaId() + " (" + o.reason() + ")")
                .toList();
        if (!failedIds.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.bulk-import-failed-items",
                    Placeholder.unparsed("items", String.join(", ", failedIds))));
        }
        if (!result.skippedAttributes().isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.import-skipped",
                    Placeholder.unparsed("attributes", String.join(", ", result.skippedAttributes()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int editTemplate(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        String key = StringArgumentType.getString(ctx, "key");
        if (plugin.getItemTemplateService().findByKey(key).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        ItemEditorMenu.open(plugin, player, key, ItemEditorTab.BASE);
        return Command.SINGLE_SUCCESS;
    }

    private static int setBaseFromHand(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        String key = StringArgumentType.getString(ctx, "key");
        Locale locale = player.locale();

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            player.sendMessage(plugin.getMessages().render(locale, "item.setbase-empty-hand"));
            return 0;
        }
        Integer customModelData = held.hasItemMeta() && held.getItemMeta().hasCustomModelData()
                ? held.getItemMeta().getCustomModelData() : null;

        try {
            plugin.getItemTemplateService().rebase(key, held.getType(), customModelData);
        } catch (TemplateNotFoundException e) {
            player.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        player.sendMessage(plugin.getMessages().render(locale, "item.setbase-done",
                Placeholder.unparsed("key", key), Placeholder.unparsed("material", held.getType().name())));
        return Command.SINGLE_SUCCESS;
    }

    /** Same as {@link #setBaseFromHand}, but the material is typed (with tab-completion, see {@link #MATERIAL_SUGGESTIONS}) instead of read off a held item. */
    /**
     * Exactly {@link #setBaseFromHand} - same "hold the item you want as the new base" behavior,
     * just under a friendlier name with tab-completion on {@code key} (see {@link
     * #templateKeySuggestions}) instead of {@code setbase}'s plain, unsuggested one. Kept as a
     * separate literal rather than replacing {@code setbase} outright so existing scripts/muscle
     * memory using {@code setbase} keep working.
     */
    private static int replaceTemplateBase(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        return setBaseFromHand(plugin, ctx);
    }

    private static int giveTemplate(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");

        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "error.player-only"));
            return 0;
        }

        ItemStack stack;
        try {
            stack = plugin.getItemTemplateService().renderGiveable(key);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }

        for (Player target : targets) {
            target.getInventory().addItem(stack.clone());
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.given",
                Placeholder.unparsed("key", key),
                Placeholder.unparsed("player", targets.size() == 1 ? targets.get(0).getName() : targets.size() + "x")));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The explicit "push to circulation now" action: edits alone (damage/resist set-remove)
     * never touch already-issued items, only this does - see {@link ItemTemplateService#propagate}.
     */
    private static int syncTemplate(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");

        try {
            plugin.getItemTemplateService().propagate(key);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }

        int touched = plugin.getItemSyncService().resyncAllOnlinePlayers();
        sender.sendMessage(plugin.getMessages().render(locale, "item.synced",
                Placeholder.unparsed("key", key), Placeholder.unparsed("count", String.valueOf(touched))));
        return Command.SINGLE_SUCCESS;
    }

    private static int setDamageContribution(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        DamageMode mode = parseEnum(DamageMode.class, StringArgumentType.getString(ctx, "mode"));
        ModifierContext context = parseEnum(ModifierContext.class, StringArgumentType.getString(ctx, "context"));
        if (mode == null || context == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "error.invalid-mode-or-context"));
            return 0;
        }

        try {
            plugin.getItemTemplateService().setDamageContribution(key, damageType, amount, mode, context);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (UnknownDamageTypeException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.damage-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeDamageContribution(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        ModifierContext context = parseEnum(ModifierContext.class, StringArgumentType.getString(ctx, "context"));
        if (context == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "error.invalid-mode-or-context"));
            return 0;
        }

        try {
            plugin.getItemTemplateService().removeDamageContribution(key, damageType, context);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.damage-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTypeModifier(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double percent = DoubleArgumentType.getDouble(ctx, "percent");

        try {
            plugin.getItemTemplateService().setTypeModifier(key, damageType, percent);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (UnknownDamageTypeException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.resist-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeTypeModifier(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String damageType = StringArgumentType.getString(ctx, "damageType");

        try {
            plugin.getItemTemplateService().removeTypeModifier(key, damageType);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.resist-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setItemAttributeModifier(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String attributeArg = StringArgumentType.getString(ctx, "attribute");
        String slotArg = StringArgumentType.getString(ctx, "slot");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        String operationArg = StringArgumentType.getString(ctx, "operation");

        Attribute attribute = parseAttribute(attributeArg);
        if (attribute == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-attribute", Placeholder.unparsed("attribute", attributeArg)));
            return 0;
        }
        String slot = AttributeSlots.parse(slotArg, plugin.getAccessorySettings().slots());
        if (slot == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-slot", Placeholder.unparsed("slot", slotArg)));
            return 0;
        }
        AttributeModifier.Operation operation = parseEnum(AttributeModifier.Operation.class, operationArg);
        if (operation == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-operation", Placeholder.unparsed("operation", operationArg)));
            return 0;
        }

        try {
            plugin.getItemTemplateService().setAttributeModifier(key, attribute, amount, operation, slot);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.attribute-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("attribute", attribute.name()), Placeholder.unparsed("slot", slot)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeItemAttributeModifier(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String attributeArg = StringArgumentType.getString(ctx, "attribute");
        String slotArg = StringArgumentType.getString(ctx, "slot");

        Attribute attribute = parseAttribute(attributeArg);
        if (attribute == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-attribute", Placeholder.unparsed("attribute", attributeArg)));
            return 0;
        }
        String slot = AttributeSlots.parse(slotArg, plugin.getAccessorySettings().slots());
        if (slot == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-slot", Placeholder.unparsed("slot", slotArg)));
            return 0;
        }

        try {
            plugin.getItemTemplateService().removeAttributeModifier(key, attribute, slot);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.attribute-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("attribute", attribute.name()), Placeholder.unparsed("slot", slot)));
        return Command.SINGLE_SUCCESS;
    }

    private static Attribute parseAttribute(String raw) {
        try {
            return Attribute.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int setEnchantment(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String enchantmentArg = StringArgumentType.getString(ctx, "enchantment");
        int level = IntegerArgumentType.getInteger(ctx, "level");

        NamespacedKey enchantmentKey = enchantmentArg.contains(":")
                ? NamespacedKey.fromString(enchantmentArg) : NamespacedKey.minecraft(enchantmentArg);
        Enchantment enchantment = enchantmentKey != null ? Registry.ENCHANTMENT.get(enchantmentKey) : null;
        if (enchantment == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-enchantment",
                    Placeholder.unparsed("enchantment", enchantmentArg)));
            return 0;
        }

        try {
            plugin.getItemTemplateService().setEnchantment(key, enchantment.getKey().toString(), level);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.enchant-set",
                Placeholder.unparsed("key", key),
                Placeholder.unparsed("enchantment", enchantment.getKey().toString()),
                Placeholder.unparsed("level", String.valueOf(level))));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEnchantment(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String enchantmentArg = StringArgumentType.getString(ctx, "enchantment");

        NamespacedKey enchantmentKey = enchantmentArg.contains(":")
                ? NamespacedKey.fromString(enchantmentArg) : NamespacedKey.minecraft(enchantmentArg);
        String resolvedKey = enchantmentKey != null ? enchantmentKey.toString() : enchantmentArg;

        try {
            plugin.getItemTemplateService().removeEnchantment(key, resolvedKey);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.enchant-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("enchantment", resolvedKey)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setItemArmorClass(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");

        if ("none".equalsIgnoreCase(armorClassArg)) {
            try {
                plugin.getItemTemplateService().setArmorClass(key, null);
            } catch (TemplateNotFoundException e) {
                sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
                return 0;
            }
            sender.sendMessage(plugin.getMessages().render(locale, "item.armor-cleared", Placeholder.unparsed("key", key)));
            return Command.SINGLE_SUCCESS;
        }

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        try {
            plugin.getItemTemplateService().setArmorClass(key, armorClass);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.armor-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("class", armorClass.name())));
        return Command.SINGLE_SUCCESS;
    }

    private static int setItemArmorPenetration(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        try {
            plugin.getItemTemplateService().setArmorPenetration(key, armorClass, amount);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.penetration-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("class", armorClass.name())));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeItemArmorPenetration(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        try {
            plugin.getItemTemplateService().removeArmorPenetration(key, armorClass);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.penetration-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("class", armorClass.name())));
        return Command.SINGLE_SUCCESS;
    }

    private static int setItemBleedEffect(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        double chancePercent = DoubleArgumentType.getDouble(ctx, "chancePercent");
        double durationSeconds = DoubleArgumentType.getDouble(ctx, "durationSeconds");

        try {
            plugin.getItemTemplateService().setBleedEffect(key, chancePercent, durationSeconds);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.bleed-set", Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeItemBleedEffect(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");

        try {
            plugin.getItemTemplateService().removeBleedEffect(key);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.bleed-removed", Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setItemCriticalEffect(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        double chancePercent = DoubleArgumentType.getDouble(ctx, "chancePercent");
        double bonusDamagePercent = DoubleArgumentType.getDouble(ctx, "bonusDamagePercent");

        try {
            plugin.getItemTemplateService().setCriticalEffect(key, chancePercent, bonusDamagePercent);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.critical-set", Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeItemCriticalEffect(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");

        try {
            plugin.getItemTemplateService().removeCriticalEffect(key);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.critical-removed", Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setArmorClassProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double percent = DoubleArgumentType.getDouble(ctx, "percent");

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        if (plugin.getDamageTypeRegistry().find(damageType).isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        plugin.getArmorClassProfileRepository().upsert(armorClass.name(), damageType, percent);
        sender.sendMessage(plugin.getMessages().render(locale, "armorclass.set",
                Placeholder.unparsed("class", armorClass.name()), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeArmorClassProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");
        String damageType = StringArgumentType.getString(ctx, "damageType");

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        boolean removed = plugin.getArmorClassProfileRepository().remove(armorClass.name(), damageType);
        String messageKey = removed ? "armorclass.removed" : "armorclass.not-found";
        sender.sendMessage(plugin.getMessages().render(locale, messageKey,
                Placeholder.unparsed("class", armorClass.name()), Placeholder.unparsed("type", damageType)));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int listArmorClassProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String armorClassArg = StringArgumentType.getString(ctx, "armorClass");

        ArmorClass armorClass = parseArmorClass(armorClassArg);
        if (armorClass == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-armor-class",
                    Placeholder.unparsed("class", armorClassArg)));
            return 0;
        }
        Map<String, Double> profile = plugin.getArmorClassProfileRepository().findByArmorClass(armorClass.name());
        if (profile.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "armorclass.list-empty",
                    Placeholder.unparsed("class", armorClass.name())));
            return Command.SINGLE_SUCCESS;
        }
        for (Map.Entry<String, Double> entry : profile.entrySet()) {
            sender.sendMessage(plugin.getMessages().render(locale, "armorclass.list-entry",
                    Placeholder.unparsed("type", entry.getKey()), Placeholder.unparsed("percent", String.valueOf(entry.getValue()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int openArmorClassMenu(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        ArmorClassMenu.open(plugin, player, ArmorClass.LIGHT);
        return Command.SINGLE_SUCCESS;
    }

    private static ArmorClass parseArmorClass(String raw) {
        try {
            return ArmorClass.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int setAllowedSlots(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String rawSlots = StringArgumentType.getString(ctx, "slots");

        List<String> slotNames = "none".equalsIgnoreCase(rawSlots.trim())
                ? List.of()
                : Arrays.stream(rawSlots.split(",")).map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();

        try {
            plugin.getItemTemplateService().setAllowedSlots(key, slotNames);
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "item.slots-set",
                Placeholder.unparsed("key", key),
                Placeholder.unparsed("slots", slotNames.isEmpty() ? "-" : String.join(", ", slotNames))));
        return Command.SINGLE_SUCCESS;
    }

    private static int openAccessoryMenu(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        AccessoryMenu.open(player, plugin.getAccessorySettings(), plugin.getAccessoryRepository());
        return Command.SINGLE_SUCCESS;
    }

    private static int setMobProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String mythicMobType = StringArgumentType.getString(ctx, "mythicMobType");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double percent = DoubleArgumentType.getDouble(ctx, "percent");

        if (plugin.getDamageTypeRegistry().find(damageType).isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        plugin.getMobDamageProfileRepository().upsert(mythicMobType, damageType, percent);
        sender.sendMessage(plugin.getMessages().render(locale, "mobprofile.set",
                Placeholder.unparsed("mob", mythicMobType), Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMobProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String mythicMobType = StringArgumentType.getString(ctx, "mythicMobType");
        String damageType = StringArgumentType.getString(ctx, "damageType");

        boolean removed = plugin.getMobDamageProfileRepository().remove(mythicMobType, damageType);
        String messageKey = removed ? "mobprofile.removed" : "mobprofile.not-found";
        sender.sendMessage(plugin.getMessages().render(locale, messageKey,
                Placeholder.unparsed("mob", mythicMobType), Placeholder.unparsed("type", damageType)));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int listMobProfile(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String mythicMobType = StringArgumentType.getString(ctx, "mythicMobType");

        Map<String, Double> profile = plugin.getMobDamageProfileRepository().findByMob(mythicMobType);
        if (profile.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "mobprofile.list-empty", Placeholder.unparsed("mob", mythicMobType)));
            return Command.SINGLE_SUCCESS;
        }
        for (Map.Entry<String, Double> entry : profile.entrySet()) {
            sender.sendMessage(plugin.getMessages().render(locale, "mobprofile.list-entry",
                    Placeholder.unparsed("type", entry.getKey()), Placeholder.unparsed("percent", String.valueOf(entry.getValue()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int createSet(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String displayName = StringArgumentType.getString(ctx, "displayName");

        try {
            plugin.getItemSetService().create(key, displayName);
        } catch (DuplicateSetKeyException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.duplicate-key", Placeholder.unparsed("key", key)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "set.created", Placeholder.unparsed("key", key)));
        return Command.SINGLE_SUCCESS;
    }

    private static int deleteSet(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        boolean deleted = plugin.getItemSetService().delete(key);
        String messageKey = deleted ? "set.deleted" : "set.not-found";
        sender.sendMessage(plugin.getMessages().render(localeOf(plugin, sender), messageKey, Placeholder.unparsed("key", key)));
        return deleted ? Command.SINGLE_SUCCESS : 0;
    }

    private static int listSets(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        List<ItemSet> sets = plugin.getItemSetService().listAll();
        if (sets.isEmpty()) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.list-empty"));
            return Command.SINGLE_SUCCESS;
        }
        for (ItemSet set : sets) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.list-entry",
                    Placeholder.unparsed("key", set.key()),
                    Placeholder.unparsed("members", String.valueOf(plugin.getItemSetService().members(set.key()).size()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int editSet(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().render(plugin.getDefaultLocale(), "error.player-only"));
            return 0;
        }
        String key = StringArgumentType.getString(ctx, "key");
        if (plugin.getItemSetService().findByKey(key).isEmpty()) {
            player.sendMessage(plugin.getMessages().render(player.locale(), "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        SetEditorMenu.open(plugin, player, key, SetEditorTab.MEMBERS);
        return Command.SINGLE_SUCCESS;
    }

    private static int addSetMember(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String templateKey = StringArgumentType.getString(ctx, "templateKey");

        try {
            plugin.getItemSetService().addMember(key, templateKey);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", templateKey)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "set.member-added",
                Placeholder.unparsed("key", key), Placeholder.unparsed("item", templateKey)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeSetMember(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        String templateKey = StringArgumentType.getString(ctx, "templateKey");

        try {
            plugin.getItemSetService().removeMember(key, templateKey);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (TemplateNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.not-found", Placeholder.unparsed("key", templateKey)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "set.member-removed",
                Placeholder.unparsed("key", key), Placeholder.unparsed("item", templateKey)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setSetDamageThreshold(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        int pieceCount = IntegerArgumentType.getInteger(ctx, "pieceCount");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        DamageMode mode = parseEnum(DamageMode.class, StringArgumentType.getString(ctx, "mode"));
        if (mode == null) {
            sender.sendMessage(plugin.getMessages().render(locale, "error.invalid-mode-or-context"));
            return 0;
        }

        try {
            plugin.getItemSetService().setDamageThreshold(key, pieceCount, damageType, amount, mode);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (UnknownDamageTypeException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "set.damage-threshold-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("count", String.valueOf(pieceCount)),
                Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeSetDamageThreshold(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        int pieceCount = IntegerArgumentType.getInteger(ctx, "pieceCount");
        String damageType = StringArgumentType.getString(ctx, "damageType");

        boolean removed;
        try {
            removed = plugin.getItemSetService().removeDamageThreshold(key, pieceCount, damageType);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        String messageKey = removed ? "set.damage-threshold-removed" : "set.threshold-not-found";
        sender.sendMessage(plugin.getMessages().render(locale, messageKey,
                Placeholder.unparsed("key", key), Placeholder.unparsed("count", String.valueOf(pieceCount)),
                Placeholder.unparsed("type", damageType)));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int setSetModifierThreshold(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        int pieceCount = IntegerArgumentType.getInteger(ctx, "pieceCount");
        String damageType = StringArgumentType.getString(ctx, "damageType");
        double percent = DoubleArgumentType.getDouble(ctx, "percent");

        try {
            plugin.getItemSetService().setModifierThreshold(key, pieceCount, damageType, percent);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        } catch (UnknownDamageTypeException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "item.unknown-damage-type", Placeholder.unparsed("type", damageType)));
            return 0;
        }
        sender.sendMessage(plugin.getMessages().render(locale, "set.resist-threshold-set",
                Placeholder.unparsed("key", key), Placeholder.unparsed("count", String.valueOf(pieceCount)),
                Placeholder.unparsed("type", damageType)));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeSetModifierThreshold(PurrtechPVE plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Locale locale = localeOf(plugin, sender);
        String key = StringArgumentType.getString(ctx, "key");
        int pieceCount = IntegerArgumentType.getInteger(ctx, "pieceCount");
        String damageType = StringArgumentType.getString(ctx, "damageType");

        boolean removed;
        try {
            removed = plugin.getItemSetService().removeModifierThreshold(key, pieceCount, damageType);
        } catch (ItemSetNotFoundException e) {
            sender.sendMessage(plugin.getMessages().render(locale, "set.not-found", Placeholder.unparsed("key", key)));
            return 0;
        }
        String messageKey = removed ? "set.resist-threshold-removed" : "set.threshold-not-found";
        sender.sendMessage(plugin.getMessages().render(locale, messageKey,
                Placeholder.unparsed("key", key), Placeholder.unparsed("count", String.valueOf(pieceCount)),
                Placeholder.unparsed("type", damageType)));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Locale localeOf(PurrtechPVE plugin, CommandSender sender) {
        return sender instanceof Player player ? player.locale() : plugin.getDefaultLocale();
    }
}
