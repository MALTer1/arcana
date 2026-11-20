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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-persistent index of engraved spell pages. */
public final class SpellBookStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<StoredSpell>>>() {}.getType();
    private static final Map<String, List<StoredSpell>> BOOKS = new HashMap<>();
    private SpellBookStore() {}

    public static final class StoredSpell {
        public String name;
        public List<String> components;
        public String behavior;
        public boolean legendary;
        public String legendaryEffect;
        public String customDescription;

        public StoredSpell() {}
        public StoredSpell(String name, List<String> components, String behavior) {
            this(name, components, behavior, false, "", "");
        }
        public StoredSpell(String name, List<String> components, String behavior,
                           boolean legendary, String legendaryEffect) {
            this(name, components, behavior, legendary, legendaryEffect, "");
        }
        public StoredSpell(String name, List<String> components, String behavior,
                           boolean legendary, String legendaryEffect, String customDescription) {
            this.name = name;
            this.components = new ArrayList<>(components == null ? List.of() : components);
            this.behavior = behavior == null ? "" : behavior;
            this.legendary = legendary;
            this.legendaryEffect = legendaryEffect == null ? "" : legendaryEffect;
            this.customDescription = customDescription == null ? "" : customDescription;
        }
        public int arcanaCost() {
            return SpellCostCalculator.calculate(components, legendary).total();
        }
    }

    public static synchronized void load(MinecraftServer server) {
        BOOKS.clear();
        Path file = fileFor(server);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, List<StoredSpell>> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) loaded.forEach((id, spells) ->
                BOOKS.put(id, spells == null ? new ArrayList<>() : new ArrayList<>(spells)));
            System.out.println("[Arcana] Loaded spellbooks from " + file);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Arcana] Could not load spellbooks: " + exception.getMessage());
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path file = fileFor(server);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) { GSON.toJson(BOOKS, DATA_TYPE, writer); }
        } catch (IOException exception) {
            System.err.println("[Arcana] Could not save spellbooks: " + exception.getMessage());
        }
    }

    public static synchronized List<StoredSpell> get(UUID playerId) {
        return List.copyOf(BOOKS.getOrDefault(playerId.toString(), List.of()));
    }
    public static synchronized boolean add(UUID playerId, StoredSpell spell) {
        List<StoredSpell> spells = BOOKS.computeIfAbsent(playerId.toString(), ignored -> new ArrayList<>());
        if (find(spells, spell.name) != null) return false;
        spells.add(spell);
        return true;
    }
    public static synchronized StoredSpell find(UUID playerId, String name) {
        return find(BOOKS.getOrDefault(playerId.toString(), List.of()), name);
    }
    public static synchronized boolean remove(UUID playerId, String name) {
        List<StoredSpell> spells = BOOKS.get(playerId.toString());
        return spells != null && spells.removeIf(spell -> spell.name.equalsIgnoreCase(name));
    }
    private static StoredSpell find(List<StoredSpell> spells, String name) {
        return spells.stream().filter(spell -> spell.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    private static Path fileFor(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("arcana-spellbooks.json");
    }
}
