package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.db.ItemTemplateRepository;
import eu.purrtech.purrtechPVE.db.ItemTemplateSnapshotRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;

/**
 * Re-renders circulating item stacks that are stamped with a
 * {@code template_key}/{@code template_version} older than that template's
 * {@code syncedVersion} - i.e. it catches items up to the last version an
 * admin explicitly pushed with {@code /pve item sync}, never further,
 * regardless of how many un-pushed edits have piled up on the live template
 * since. Online players are swept immediately; offline players catch up the
 * next time they join (see the join listener). Items sitting in world
 * containers/dropped on the ground are not covered yet - would need
 * chunk-load-triggered container scanning, left as a follow-up.
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

    /**
     * Same sweep as {@link #resyncAllOnlinePlayers()}, but re-renders every stamped stack
     * unconditionally instead of only ones behind their template's {@code syncedVersion}. Needed
     * because a {@code lang/*.yml} (or locale) change doesn't bump any template's version - it's
     * global text, not a per-template edit - so the normal stale check would never catch it.
     * Called by {@code PurrtechPVE.reload()} so editing lang and running {@code /pve reload}
     * updates already-issued items' name/lore too, not just future ones.
     */
    public int resyncAllOnlinePlayersFull() {
        int touched = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            touched += resyncPlayer(player, true);
        }
        return touched;
    }

    /** Sweeps one player's inventory (main + armor + offhand) + ender chest. Meant for both an explicit push and PlayerJoinEvent catch-up. */
    public int resyncPlayer(Player player) {
        return resyncPlayer(player, false);
    }

    private int resyncPlayer(Player player, boolean force) {
        return resyncPlayerInventory(player.getInventory(), force) + resyncInventory(player.getEnderChest(), force);
    }

    private int resyncPlayerInventory(PlayerInventory inventory, boolean force) {
        int touched = resyncInventory(inventory, force);

        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            Optional<ItemStack> updated = resyncStackIfStale(armor[i], force);
            if (updated.isPresent()) {
                armor[i] = updated.get();
                armorChanged = true;
                touched++;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }

        Optional<ItemStack> offhand = resyncStackIfStale(inventory.getItemInOffHand(), force);
        if (offhand.isPresent()) {
            inventory.setItemInOffHand(offhand.get());
            touched++;
        }

        return touched;
    }

    private int resyncInventory(Inventory inventory, boolean force) {
        int touched = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            Optional<ItemStack> updated = resyncStackIfStale(contents[slot], force);
            if (updated.isPresent()) {
                inventory.setItem(slot, updated.get());
                touched++;
            }
        }
        return touched;
    }

    private Optional<ItemStack> resyncStackIfStale(ItemStack stack, boolean force) {
        Optional<ItemRenderer.StampedTemplate> stamp = renderer.readStamp(stack);
        if (stamp.isEmpty()) {
            return Optional.empty();
        }

        Optional<ItemTemplate> templateOpt = templateRepository.findByKey(stamp.get().templateKey());
        if (templateOpt.isEmpty()) {
            // template was deleted since this item was given - leave the stack exactly as it is
            return Optional.empty();
        }
        ItemTemplate template = templateOpt.get();
        if (!force && stamp.get().templateVersion() >= template.syncedVersion()) {
            return Optional.empty();
        }

        TemplateSnapshot snapshot = snapshotRepository.find(template.id(), template.syncedVersion())
                .orElseThrow(() -> new IllegalStateException("Missing snapshot v" + template.syncedVersion()
                        + " for template " + template.key() + " - every version bump must write one"));

        ItemStack rendered = renderer.renderSnapshot(snapshot);
        rendered.setAmount(stack.getAmount());
        return Optional.of(rendered);
    }
}
