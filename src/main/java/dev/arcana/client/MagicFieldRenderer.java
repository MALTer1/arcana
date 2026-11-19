package dev.arcana.client;

import dev.arcana.entity.ArcanaFieldEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Field visuals are emitted as world particles by the server-authoritative
 * entity tick, so this entity intentionally has no model of its own.
 */
public final class MagicFieldRenderer extends EntityRenderer<ArcanaFieldEntity> {
    public MagicFieldRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ArcanaFieldEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }
}
