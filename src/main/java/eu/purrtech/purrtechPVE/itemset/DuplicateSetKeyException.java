package eu.purrtech.purrtechPVE.itemset;

public final class DuplicateSetKeyException extends RuntimeException {

    public DuplicateSetKeyException(String key) {
        super("An item set with key '" + key + "' already exists");
    }
}
