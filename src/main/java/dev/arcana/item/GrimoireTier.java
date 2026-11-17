package dev.arcana.item;

import net.minecraft.item.Item;

public enum GrimoireTier {
    NOVICE("novice_grimoire", "Novice Grimoire", 3, 2),
    APPRENTICE("apprentice_grimoire", "Apprentice Grimoire", 5, 3),
    ADEPT("adept_grimoire", "Adept Grimoire", 7, 4),
    MASTER("master_grimoire", "Master Grimoire", 10, 5);

    private final String itemId;
    private final String displayName;
    private final int spellSlots;
    private final int maxComponents;

    GrimoireTier(String itemId, String displayName, int spellSlots, int maxComponents) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.spellSlots = spellSlots;
        this.maxComponents = maxComponents;
    }

    public String itemId() { return itemId; }
    public String displayName() { return displayName; }
    public int spellSlots() { return spellSlots; }
    public int maxComponents() { return maxComponents; }

    public static GrimoireTier fromItem(Item item) {
        for (GrimoireTier tier : values()) {
            if (item == switch (tier) {
                case NOVICE -> dev.arcana.item.ArcanaItems.NOVICE_GRIMOIRE;
                case APPRENTICE -> dev.arcana.item.ArcanaItems.APPRENTICE_GRIMOIRE;
                case ADEPT -> dev.arcana.item.ArcanaItems.ADEPT_GRIMOIRE;
                case MASTER -> dev.arcana.item.ArcanaItems.MASTER_GRIMOIRE;
            }) return tier;
        }
        return null;
    }
}
