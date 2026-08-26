package eu.purrtech.purrtechPVE.item;

public final class DuplicateTemplateKeyException extends RuntimeException {

    public DuplicateTemplateKeyException(String key) {
        super("An item template with key '" + key + "' already exists");
    }
}
