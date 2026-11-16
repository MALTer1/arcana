package dev.arcana.spell;

public enum ComponentCategory {
    DELIVERY("Delivery and Shape"), ELEMENT("Element"), TARGET("Target"), EFFECT("Power and Effect");
    private final String displayName;
    ComponentCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
