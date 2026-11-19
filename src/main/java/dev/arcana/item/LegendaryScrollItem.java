package dev.arcana.item;

import net.minecraft.item.Item;

/** Rare consumable used to awaken a spell page into its Legendary form. */
public final class LegendaryScrollItem extends Item {
    public LegendaryScrollItem(Settings settings) {
        super(settings.maxCount(1));
    }
}
