package dev.arcana.spell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.arcana.item.GrimoireTier;
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

/**
 * First-pass physical-grimoire inventory. Each player has a separate collection
 * for each grimoire tier; capacities are enforced independently.
 */
public final class GrimoireStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, List<SpellBookStore.StoredSpell>>>>() {}.getType();
    private static final Map<String, Map<String, List<SpellBookStore.StoredSpell>>> BOOKS = new HashMap<>();

    private GrimoireStore() {}

    public static synchronized void load(MinecraftServer server) {
        BOOKS.clear();
        Path file = fileFor(server);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Map<String, List<SpellBookStore.StoredSpell>>> loaded = GSON.fromJson(reader, DATA_TYPE);
            if (loaded != null) loaded.forEach((player, tiers) -> {
                Map<String, List<SpellBookStore.StoredSpell>> copy = new HashMap<>();
                if (tiers != null) tiers.forEach((tier, spells) -> copy.put(tier,
                    spells == null ? new ArrayList<>() : new ArrayList<>(spells)));
                BOOKS.put(player, copy);
            });
            System.out.println("[Arcana] Loaded grimoire pages from " + file);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Arcana] Could not load grimoires: " + exception.getMessage());
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path file = fileFor(server);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(BOOKS, DATA_TYPE, writer);
            }
            System.out.println("[Arcana] Saved grimoire pages to " + file);
        } catch (IOException exception) {
            System.err.println("[Arcana] Could not save grimoires: " + exception.getMessage());
        }
    }

    public static synchronized List<SpellBookStore.StoredSpell> get(UUID player, GrimoireTier tier) {
        return List.copyOf(list(player, tier));
    }

    public static synchronized boolean insert(UUID player, GrimoireTier tier, SpellBookStore.StoredSpell spell) {
        List<SpellBookStore.StoredSpell> spells = list(player, tier);
        if (spells.size() >= tier.spellSlots()) return false;
        if (find(spells, spell.name) != null) return false;
        if (spell.components == null || spell.components.size() > tier.maxComponents()) return false;
        spells.add(spell);
        return true;
    }

    public static synchronized SpellBookStore.StoredSpell remove(UUID player, GrimoireTier tier, String name) {
        List<SpellBookStore.StoredSpell> spells = list(player, tier);
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i).name.equalsIgnoreCase(name)) return spells.remove(i);
        }
        return null;
    }

    public static synchronized SpellBookStore.StoredSpell find(UUID player, GrimoireTier tier, String name) {
        return find(list(player, tier), name);
    }

    private static SpellBookStore.StoredSpell find(List<SpellBookStore.StoredSpell> spells, String name) {
        return spells.stream().filter(spell -> spell.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static List<SpellBookStore.StoredSpell> list(UUID player, GrimoireTier tier) {
        return BOOKS.computeIfAbsent(player.toString(), ignored -> new HashMap<>())
            .computeIfAbsent(tier.name(), ignored -> new ArrayList<>());
    }

    private static Path fileFor(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("arcana-grimoires.json");
    }
}
