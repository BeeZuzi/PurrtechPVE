package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
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

    public ItemSyncService(ItemTemplateRepository templateRepository, ItemTemplateSnapshotRepository snapshotRepository,
                            ItemRenderer renderer) {
        this.templateRepository = templateRepository;
        this.snapshotRepository = snapshotRepository;
        this.renderer = renderer;
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
        TemplateSnapshot snapshot = snapshotRepository.find(template.id(), targetVersion)
                .orElseThrow(() -> new IllegalStateException("Missing snapshot v" + targetVersion
                        + " for template " + template.key() + " - every version bump must write one"));

        ItemStack rendered = renderer.renderSnapshot(snapshot);
        rendered.setAmount(stack.getAmount());
        return Optional.of(rendered);
    }
}
