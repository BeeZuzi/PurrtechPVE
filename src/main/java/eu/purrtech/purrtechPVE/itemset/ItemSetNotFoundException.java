package eu.purrtech.purrtechPVE.itemset;

public final class ItemSetNotFoundException extends RuntimeException {

    public ItemSetNotFoundException(String key) {
        super("No item set with key '" + key + "'");
    }
}
