package dev.arcana.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ArcanaItems {
    public static final Item MAGIC_DUST = register("magic_dust", new Item(new Item.Settings()));
    public static final Item MAGIC_PAPER = register("magic_paper", new Item(new Item.Settings().maxCount(1)));
    // Legacy registry entry retained so existing worlds can still load old Spell Page stacks.
    // New pages are always represented by data-bearing Magic Paper.
    public static final Item SPELL_PAGE = register("spell_page", new Item(new Item.Settings()));
    public static final Item NOVICE_GRIMOIRE = register("novice_grimoire", new Item(new Item.Settings()));
    public static final Item APPRENTICE_GRIMOIRE = register("apprentice_grimoire", new Item(new Item.Settings()));
    public static final Item ADEPT_GRIMOIRE = register("adept_grimoire", new Item(new Item.Settings()));
    public static final Item MASTER_GRIMOIRE = register("master_grimoire", new Item(new Item.Settings()));

    private ArcanaItems() {}
    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of("arcana", path), item);
    }
    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(MAGIC_DUST);
            entries.add(MAGIC_PAPER);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(NOVICE_GRIMOIRE);
            entries.add(APPRENTICE_GRIMOIRE);
            entries.add(ADEPT_GRIMOIRE);
            entries.add(MASTER_GRIMOIRE);
        });
    }
}