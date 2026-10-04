package eu.purrtech.purrtechPVE.gui;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies one screen of {@link MobMenu} so {@link ItemEditorListener} can route its clicks:
 * the paginated list of every MythicMobs type ({@link View#LIST}), one mob's equipment overview
 * ({@link View#MOB}), or the item picker for one of its equipment slots ({@link View#PICK}).
 * Immutable per screen - navigating opens a fresh inventory, same as {@code ArmorClassMenu}.
 */
public final class MobMenuHolder implements InventoryHolder {

    public enum View {
        LIST, MOB, PICK,
        /** One mob's {@code pvedamage} attacks. */
        ATTACKS,
        /** Per-damage-type amounts of one of those attacks. */
        ATTACK
    }

    private final View view;
    private final String mobType;
    private final EquipmentSlot slot;
    private final String attackId;
    private final int page;
    private final int returnPage;
    private Inventory inventory;

    /**
     * @param mobType    MythicMobs internal name; {@code null} on the list screen
     * @param slot       the equipment slot being filled; only set on the picker screen
     * @param page       page of the list/picker being shown
     * @param returnPage page of the mob LIST to go back to, carried through MOB and PICK so a back
     *                   button always lands on the page the admin came from
     */
    public MobMenuHolder(View view, String mobType, EquipmentSlot slot, int page, int returnPage) {
        this(view, mobType, slot, null, page, returnPage);
    }

    /** @param attackId the {@code pvedamage} attack being edited; only set on the {@link View#ATTACK} screen */
    public MobMenuHolder(View view, String mobType, EquipmentSlot slot, String attackId, int page, int returnPage) {
        this.view = view;
        this.mobType = mobType;
        this.slot = slot;
        this.attackId = attackId;
        this.page = page;
        this.returnPage = returnPage;
    }

    public String attackId() {
        return attackId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public View view() {
        return view;
    }

    public String mobType() {
        return mobType;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    public int page() {
        return page;
    }

    public int returnPage() {
        return returnPage;
    }
}
