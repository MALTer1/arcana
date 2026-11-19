package dev.arcana.spell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** World-persistent Arcana pool for each player. */
public final class ArcanaResource {
    public static final int MAX_ARCANA = 200;
    private static final int REGEN_PER_SECOND = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final Map<UUID, Integer> CURRENT = new ConcurrentHashMap<>();
    private static int tickCounter;

    private ArcanaResource() {}

    public static synchronized void load(MinecraftServer server) {
        CURRENT.clear();
        tickCounter = 0;
        Path file = fileFor(server);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Integer> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                loaded.forEach((id, amount) -> {
                    try {
                        if (amount != null) CURRENT.put(UUID.fromString(id), Math.max(0, Math.min(MAX_ARCANA, amount)));
                    } catch (IllegalArgumentException ignored) {
                        System.err.println("[Arcana] Ignoring invalid player UUID in Arcana data: " + id);
                    }
                });
            }
            System.out.println("[Arcana] Loaded player Arcana pools from " + file);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Arcana] Could not load Arcana pools: " + exception.getMessage());
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path file = fileFor(server);
        Map<String, Integer> snapshot = new HashMap<>();
        CURRENT.forEach((id, amount) -> snapshot.put(id.toString(), amount));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(snapshot, DATA_TYPE, writer);
            }
            System.out.println("[Arcana] Saved player Arcana pools to " + file);
        } catch (IOException exception) {
            System.err.println("[Arcana] Could not save Arcana pools: " + exception.getMessage());
        }
    }

    public static int current(ServerPlayerEntity player) {
        return CURRENT.computeIfAbsent(player.getUuid(), ignored -> MAX_ARCANA);
    }

    public static synchronized void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            CURRENT.compute(player.getUuid(), (id, value) ->
                Math.min(MAX_ARCANA, (value == null ? MAX_ARCANA : value) + REGEN_PER_SECOND));
        }
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

    private static Path fileFor(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("arcana-pools.json");
    }
}
