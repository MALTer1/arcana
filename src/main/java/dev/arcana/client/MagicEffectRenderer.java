package dev.arcana.client;

import dev.arcana.entity.ArcanaEffectEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** Reaction entities are rendered through server-synchronized world particles. */
public final class MagicEffectRenderer extends EntityRenderer<ArcanaEffectEntity> {
    public MagicEffectRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ArcanaEffectEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }
}
