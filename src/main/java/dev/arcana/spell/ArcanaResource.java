package dev.arcana.spell;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime Arcana pool. Values are intentionally session data for now. */
public final class ArcanaResource {
    public static final int MAX_ARCANA = 100;
    private static final int REGEN_PER_SECOND = 2;
    private static final Map<UUID, Integer> CURRENT = new ConcurrentHashMap<>();
    private static int tickCounter;

    private ArcanaResource() {}

    public static int current(ServerPlayerEntity player) {
        return CURRENT.computeIfAbsent(player.getUuid(), ignored -> MAX_ARCANA);
    }

    public static synchronized void tick(ServerPlayerEntity player) {
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;
        CURRENT.compute(player.getUuid(), (id, value) ->
            Math.min(MAX_ARCANA, (value == null ? MAX_ARCANA : value) + REGEN_PER_SECOND));
    }

    public static boolean trySpend(ServerPlayerEntity player, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Arcana cost cannot be negative.");
        final boolean[] paid = {false};
        CURRENT.compute(player.getUuid(), (id, value) -> {
            int current = value == null ? MAX_ARCANA : value;
            if (current < amount) return current;
            paid[0] = true;
            return current - amount;
        });
        return paid[0];
    }

    public static void restore(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return;
        CURRENT.compute(player.getUuid(), (id, value) ->
            Math.min(MAX_ARCANA, (value == null ? MAX_ARCANA : value) + amount));
    }
}
