package dev.arcana.spell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ComponentKnowledgeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType();
    private static final Map<String, Set<String>> KNOWLEDGE = new HashMap<>();
    private ComponentKnowledgeStore() {}

    public static synchronized Set<String> get(UUID playerId) {
        return Set.copyOf(KNOWLEDGE.getOrDefault(playerId.toString(), Set.of()));
    }
    public static synchronized boolean knows(UUID playerId, String id) {
        return KNOWLEDGE.getOrDefault(playerId.toString(), Set.of()).contains(id);
    }
    public static synchronized boolean learn(UUID playerId, String id) {
        if (ComponentRegistry.get(id) == null) return false;
        return KNOWLEDGE.computeIfAbsent(playerId.toString(), ignored -> new LinkedHashSet<>()).add(id);
    }
    public static synchronized int learnAll(UUID playerId) {
        Set<String> known = KNOWLEDGE.computeIfAbsent(playerId.toString(), ignored -> new LinkedHashSet<>());
        int before = known.size();
        ComponentRegistry.all().forEach(component -> known.add(component.id()));
        return known.size() - before;
    }
    public static synchronized void load(MinecraftServer server) {
        KNOWLEDGE.clear();
        Path file = fileFor(server);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Set<String>> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) loaded.forEach((id, components) -> {
                Set<String> valid = new LinkedHashSet<>();
                if (components != null) for (String component : components)
                    if (ComponentRegistry.get(component) != null) valid.add(component);
                KNOWLEDGE.put(id, valid);
            });
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Arcana] Could not load component knowledge: " + exception.getMessage());
        }
    }
    public static synchronized void save(MinecraftServer server) {
        Path file = fileFor(server);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) { GSON.toJson(KNOWLEDGE, DATA_TYPE, writer); }
        } catch (IOException exception) {
            System.err.println("[Arcana] Could not save component knowledge: " + exception.getMessage());
        }
    }
    private static Path fileFor(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("arcana-component-knowledge.json");
    }
}