package dev.arcana.block;

import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.screen.MagicWriterScreenHandler;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ArcanaBlocks {
    public static final Block MAGIC_WRITER = register("magic_writer",
        new MagicWriterBlock(Block.Settings.copy(Blocks.ENCHANTING_TABLE).strength(3.5f).nonOpaque()));

    public static final BlockEntityType<MagicWriterBlockEntity> MAGIC_WRITER_ENTITY = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of("arcana", "magic_writer"),
        BlockEntityType.Builder.create(MagicWriterBlockEntity::new, MAGIC_WRITER).build()
    );

    public static ScreenHandlerType<MagicWriterScreenHandler> MAGIC_WRITER_SCREEN_HANDLER;

    private ArcanaBlocks() {}

    private static Block register(String path, Block block) {
        Identifier id = Identifier.of("arcana", path);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(MAGIC_WRITER));
    }
}
