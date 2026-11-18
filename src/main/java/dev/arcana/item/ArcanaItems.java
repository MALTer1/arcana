package dev.arcana.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ArcanaItems {
    public static final Item MAGIC_DUST = register("magic_dust");
    public static final Item MAGIC_PAPER = register("magic_paper");
    public static final Item SPELL_PAGE = register("spell_page");
    public static final Item NOVICE_GRIMOIRE = register("novice_grimoire");
    public static final Item APPRENTICE_GRIMOIRE = register("apprentice_grimoire");
    public static final Item ADEPT_GRIMOIRE = register("adept_grimoire");
    public static final Item MASTER_GRIMOIRE = register("master_grimoire");

    private ArcanaItems() {}

    private static Item register(String path) {
        return Registry.register(Registries.ITEM, Identifier.of("arcana", path), new Item(new Item.Settings()));
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
