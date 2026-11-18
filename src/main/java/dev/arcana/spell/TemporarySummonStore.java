package dev.arcana.spell;

import net.minecraft.entity.Entity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks temporary spell-created helpers while they are loaded in the world. */
public final class TemporarySummonStore {
    private record TimedEntity(Entity entity, int ticksRemaining) {}
    private static final Map<UUID, TimedEntity> SUMMONS = new ConcurrentHashMap<>();

    private TemporarySummonStore() {}

    public static void track(Entity entity, int lifetimeTicks) {
        SUMMONS.put(entity.getUuid(), new TimedEntity(entity, Math.max(1, lifetimeTicks)));
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, TimedEntity>> iterator = SUMMONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TimedEntity> entry = iterator.next();
            TimedEntity tracked = entry.getValue();
            Entity entity = tracked.entity();
            if (entity.isRemoved()) {
                SUMMONS.remove(entry.getKey());
                continue;
            }
            int remaining = tracked.ticksRemaining() - 1;
            if (remaining <= 0) {
                entity.discard();
                SUMMONS.remove(entry.getKey());
            } else {
                SUMMONS.replace(entry.getKey(), tracked, new TimedEntity(entity, remaining));
            }
        }
    }
}
