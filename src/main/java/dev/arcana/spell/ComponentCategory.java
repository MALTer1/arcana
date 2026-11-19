package dev.arcana.spell;

public enum ComponentCategory {
    DELIVERY("Form and Delivery"),
    EFFECT("Effect"),
    AUGMENT("Augment"),
    ELEMENT("Element"),
    TARGET("Target");
    private final String displayName;
    ComponentCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
