package dev.arcana.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ArcanaEntities {
    public static final EntityType<ArcanaProjectileEntity> MAGIC_PROJECTILE =
        Registry.register(Registries.ENTITY_TYPE, Identifier.of("arcana", "magic_projectile"),
            EntityType.Builder.<ArcanaProjectileEntity>create(ArcanaProjectileEntity::new, SpawnGroup.MISC)
                .dimensions(0.25F, 0.25F)
                .maxTrackingRange(64)
                .trackingTickInterval(1)
                .build("arcana:magic_projectile"));

    private ArcanaEntities() {}

    public static void register() {
        // Static initialization registers the entity type.
    }
}
