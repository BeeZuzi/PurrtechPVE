package eu.purrtech.purrtechPVE.config;

/**
 * {@code drop-hologram.enabled} - the server-wide kill switch for the floating name hologram
 * shown above world-dropped items rendered by this plugin. Off here overrides every per-item
 * setting in {@link eu.purrtech.purrtechPVE.db.ItemHologramRepository} - an admin doesn't have to
 * remember to disable it on every item individually. On by default.
 */
public record DropHologramSettings(boolean enabled) {

    public static DropHologramSettings defaults() {
        return new DropHologramSettings(true);
    }
}
