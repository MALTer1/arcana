package dev.arcana.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registers Arcana's basic spellcrafting materials. */
public final class ArcanaItems {
    public static final Item MAGIC_DUST = register("magic_dust");
    public static final Item MAGIC_PAPER = register("magic_paper");

    private ArcanaItems() {}

    private static Item register(String path) {
        return Registry.register(Registries.ITEM, Identifier.of("arcana", path), new Item(new Item.Settings()));
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(MAGIC_DUST);
            entries.add(MAGIC_PAPER);
        });
    }
}
