package eu.purrtech.purrtechPVE.item;

/** Thrown by {@code ItemTemplateService.setDamageContribution} for a damage type key that's real but can't be used as a normal wielded/worn contribution - currently just {@code "bleed"} (see {@link BleedEffect}'s javadoc). */
public final class NonContributableDamageTypeException extends RuntimeException {

    public NonContributableDamageTypeException(String key) {
        super("Damage type '" + key + "' can't be used as a normal damage contribution - configure it through its own dedicated stat instead");
    }
}
