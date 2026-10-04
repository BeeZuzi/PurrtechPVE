package eu.purrtech.purrtechPVE.item;

import eu.purrtech.purrtechPVE.db.ItemUpgradeRepository;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies one upgrade to a circulating item: makes sure it carries an instance id (stamped lazily,
 * the first time anything is upgraded - an item nobody upgraded never gets one), stores the new
 * amount, and returns the item re-rendered so its lore shows the result. The caller puts the
 * returned stack back where the old one was.
 */
public final class ItemUpgradeService {

    /** Upper bound for one entry, only so a held-down click can't push a double into nonsense. */
    public static final double MAX_UPGRADE = 10_000;

    private final ItemRenderer renderer;
    private final ItemUpgradeRepository repository;
    private final ItemSyncService syncService;

    public ItemUpgradeService(ItemRenderer renderer, ItemUpgradeRepository repository, ItemSyncService syncService) {
        this.renderer = renderer;
        this.repository = repository;
        this.syncService = syncService;
    }

    /** This item's current upgrades; empty if it has no instance id yet. */
    public ItemUpgrades upgradesOf(ItemStack stack) {
        return renderer.readInstanceId(stack).map(repository::find).orElse(ItemUpgrades.NONE);
    }

    /**
     * Sets {@code entryKey}'s upgrade to {@code amount} (clamped to 0..{@link #MAX_UPGRADE}).
     *
     * @return the re-rendered stack, or empty if {@code stack} isn't one of our templated items
     */
    public Optional<ItemStack> setUpgrade(ItemStack stack, UpgradeCategory category, String entryKey, double amount) {
        if (renderer.readStamp(stack).isEmpty()) {
            return Optional.empty();
        }
        ItemStack stamped = stack.clone();
        UUID instanceId = renderer.readInstanceId(stamped).orElseGet(() -> stampInstanceId(stamped));
        repository.set(instanceId, category, entryKey, Math.max(0, Math.min(amount, MAX_UPGRADE)));
        return syncService.forceRerender(stamped);
    }

    private UUID stampInstanceId(ItemStack stack) {
        UUID id = UUID.randomUUID();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(renderer.instanceIdPdc(), PersistentDataType.STRING, id.toString());
        stack.setItemMeta(meta);
        return id;
    }
}
