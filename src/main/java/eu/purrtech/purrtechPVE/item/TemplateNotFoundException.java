package eu.purrtech.purrtechPVE.item;

public final class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String key) {
        super("No item template with key '" + key + "'");
    }
}
