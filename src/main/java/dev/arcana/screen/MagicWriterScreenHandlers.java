package dev.arcana.screen;

import dev.arcana.block.ArcanaBlocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class MagicWriterScreenHandlers {
    private MagicWriterScreenHandlers() {}

    public static void register() {
        ArcanaBlocks.MAGIC_WRITER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("arcana", "magic_writer"),
            new ScreenHandlerType<>(MagicWriterScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );
    }
}
