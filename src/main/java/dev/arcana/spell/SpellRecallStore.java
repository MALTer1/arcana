package dev.arcana.spell;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session safe recall points. Only grounded, collision-free positions are
 * recorded, and recall never crosses dimensions or places a player inside a block.
 */
public final class SpellRecallStore {
    private record SafePoint(net.minecraft.registry.RegistryKey<World> dimension, Vec3d position) {}
    private static final Map<UUID, SafePoint> POINTS = new ConcurrentHashMap<>();

    private SpellRecallStore() {}

    public static void record(ServerPlayerEntity player) {
        if (!player.isOnGround() || player.isRemoved()) return;
        Vec3d position = player.getPos();
        if (!player.getWorld().isSpaceEmpty(player, player.getBoundingBox())) return;
        POINTS.put(player.getUuid(), new SafePoint(player.getServerWorld().getRegistryKey(), position));
    }

    public static boolean recall(ServerPlayerEntity player) {
        SafePoint point = POINTS.get(player.getUuid());
        if (point == null || !point.dimension().equals(player.getServerWorld().getRegistryKey())) return false;
        Vec3d offset = point.position().subtract(player.getPos());
        if (!player.getWorld().isSpaceEmpty(player, player.getBoundingBox().offset(offset))) return false;
        player.requestTeleport(point.position().x, point.position().y, point.position().z);
        return true;
    }
}
