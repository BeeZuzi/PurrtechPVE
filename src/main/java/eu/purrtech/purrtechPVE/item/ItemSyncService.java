package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import eu.purrtech.purrtechPVE.db.ItemUpgradeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;
import java.util.Optional;

/**
 * Re-renders circulating item stacks that are stale in either of two ways:
 * <ul>
 *   <li>stamped with a {@code template_version} older than the template's {@code syncedVersion}
 *       - caught up to the last version an admin explicitly pushed with {@code /pve item sync},
 *       never further;</li>
 *   <li>stamped with a {@code lang_hash} different from the currently loaded lang/locale - re-
 *       rendered at the stack's own version with fresh text, so a lang edit never changes stats.</li>
 * </ul>
 * Online players are swept on push/reload, offline ones on join, and containers/dropped items
 * lazily when opened/picked up (see {@code ItemSyncJoinListener}).
 */
public final class ItemSyncService {

    private final ItemTemplateRepository templateRepository;
    private final ItemTemplateSnapshotRepository snapshotRepository;
    private final ItemRenderer renderer;
    private final ItemUpgradeRepository upgradeRepository;
    private final StackStateCarrier stateCarrier;

    public ItemSyncService(ItemTemplateRepository templateRepository, ItemTemplateSnapshotRepository snapshotRepository,
                            ItemRenderer renderer, ItemUpgradeRepository upgradeRepository) {
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
        this.upgradeRepository = upgradeRepository;
        this.stateCarrier = new StackStateCarrier(renderer);
    }

    /** Sweeps every online player's inventory + ender chest. Returns how many stacks were re-rendered. */
    public int resyncAllOnlinePlayers() {
        int touched = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            touched += resyncPlayer(player);
        }
        return touched;
    }

    /** Sweeps one player's inventory (main + armor + offhand) + ender chest. Meant for both an explicit push and PlayerJoinEvent catch-up. */
    public int resyncPlayer(Player player) {
        return resyncPlayerInventory(player.getInventory()) + resyncInventory(player.getEnderChest());
    }

    private int resyncPlayerInventory(PlayerInventory inventory) {
        int touched = resyncInventory(inventory);

        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            Optional<ItemStack> updated = resyncStack(armor[i]);
            if (updated.isPresent()) {
                armor[i] = updated.get();
                armorChanged = true;
                touched++;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }

        Optional<ItemStack> offhand = resyncStack(inventory.getItemInOffHand());
        if (offhand.isPresent()) {
            inventory.setItemInOffHand(offhand.get());
            touched++;
        }

        return touched;
    }

    public int resyncInventory(Inventory inventory) {
        int touched = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            Optional<ItemStack> updated = resyncStack(contents[slot]);
            if (updated.isPresent()) {
                inventory.setItem(slot, updated.get());
                touched++;
            }
        }
        return touched;
    }

    /**
     * Re-renders {@code stack} right now at its own version, regardless of staleness - used after its
     * per-item upgrades changed so the lore shows them. Empty if it isn't one of ours or its snapshot is gone.
     */
    public Optional<ItemStack> forceRerender(ItemStack stack) {
        Optional<ItemRenderer.StampedTemplate> stampOpt = renderer.readStamp(stack);
        if (stampOpt.isEmpty()) {
            return Optional.empty();
        }
        ItemRenderer.StampedTemplate stamp = stampOpt.get();
        return templateRepository.findByKey(stamp.templateKey())
                .flatMap(template -> renderUpdated(stack, stamp, template, stamp.templateVersion()));
    }

    /** The re-rendered replacement for {@code stack}, or empty if it isn't ours or is already up to date. */
    public Optional<ItemStack> resyncStack(ItemStack stack) {
        Optional<ItemRenderer.StampedTemplate> stampOpt = renderer.readStamp(stack);
        if (stampOpt.isEmpty()) {
            return Optional.empty();
        }
        ItemRenderer.StampedTemplate stamp = stampOpt.get();

        Optional<ItemTemplate> templateOpt = templateRepository.findByKey(stamp.templateKey());
        if (templateOpt.isEmpty()) {
            // template was deleted since this item was given - leave the stack exactly as it is
            return Optional.empty();
        }
        ItemTemplate template = templateOpt.get();

        boolean versionStale = stamp.templateVersion() < template.syncedVersion();
        boolean langStale = !Objects.equals(stamp.langHash(), renderer.currentLangHash());
        if (!versionStale && !langStale) {
            return Optional.empty();
        }

        // A lang-only refresh keeps the stack's own version - it may legitimately be newer than
        // syncedVersion (given from the live template), and a text change must not roll it back.
        int targetVersion = versionStale ? template.syncedVersion() : stamp.templateVersion();
        return renderUpdated(stack, stamp, template, targetVersion);
    }

    private Optional<ItemStack> renderUpdated(ItemStack stack, ItemRenderer.StampedTemplate stamp, ItemTemplate template, int targetVersion) {
        TemplateSnapshot snapshot = snapshotRepository.find(template.id(), targetVersion)
                .orElseThrow(() -> new IllegalStateException("Missing snapshot v" + targetVersion
                        + " for template " + template.key() + " - every version bump must write one"));

        // The item's own upgrades ride on top of whatever version it is being rendered at, so the lore
        // shows them and a template sync can never drop them.
        ItemUpgrades upgrades = renderer.readInstanceId(stack).map(upgradeRepository::find).orElse(ItemUpgrades.NONE);
        ItemStack rendered = renderer.renderSnapshot(UpgradeApplier.apply(snapshot, upgrades), template.armorClass(), template.armorAmount());
        // The old stack's version rendered fresh (upgrades included, they're in its lore too) is what
        // tells template-given enchants/attributes/lore from the ones a player added since; null
        // (snapshot gone) just means those aren't carried.
        ItemStack baseline = targetVersion == stamp.templateVersion()
                ? rendered
                : snapshotRepository.find(template.id(), stamp.templateVersion())
                        .map(old -> renderer.renderSnapshot(UpgradeApplier.apply(old, upgrades), template.armorClass(), template.armorAmount()))
                        .orElse(null);
        stateCarrier.carry(stack, baseline, rendered);
        rendered.setAmount(stack.getAmount());
        return Optional.of(rendered);
    }
}
