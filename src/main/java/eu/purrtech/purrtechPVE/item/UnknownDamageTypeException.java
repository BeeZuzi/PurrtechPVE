package eu.purrtech.purrtechPVE.item;

public final class UnknownDamageTypeException extends RuntimeException {

    public UnknownDamageTypeException(String key) {
        super("No damage type registered with key '" + key + "'");
    }
}
