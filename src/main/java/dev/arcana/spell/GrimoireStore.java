package dev.arcana.spell;

import dev.arcana.item.GrimoireTier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/** Spell pages stored directly on each physical Grimoire ItemStack. */
public final class GrimoireStore {
    private static final String ROOT_KEY = "ArcanaGrimoire";
    private static final String SPELLS_KEY = "Spells";

    private GrimoireStore() {}

    public static synchronized List<SpellBookStore.StoredSpell> get(ItemStack stack, GrimoireTier tier) {
        if (stack.isEmpty() || tier == null || GrimoireTier.fromItem(stack.getItem()) != tier) return List.of();
        NbtList list = getSpellList(stack);
        List<SpellBookStore.StoredSpell> spells = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            NbtList componentsTag = tag.getList("Components", NbtElement.STRING_TYPE);
            List<String> components = new ArrayList<>();
            for (int j = 0; j < componentsTag.size(); j++) components.add(componentsTag.getString(j));
            spells.add(new SpellBookStore.StoredSpell(
                tag.getString("Name"), components, tag.getString("Behavior")));
        }
        return List.copyOf(spells);
    }

    public static synchronized boolean insert(ItemStack stack, GrimoireTier tier, SpellBookStore.StoredSpell spell) {
        if (stack.isEmpty() || tier == null || GrimoireTier.fromItem(stack.getItem()) != tier) return false;
        List<SpellBookStore.StoredSpell> spells = new ArrayList<>(get(stack, tier));
        if (spells.size() >= tier.spellSlots() || find(spells, spell.name) != null
            || spell.components == null || spell.components.size() > tier.maxComponents()) return false;
        spells.add(spell);
        write(stack, spells);
        return true;
    }

    public static synchronized SpellBookStore.StoredSpell remove(ItemStack stack, GrimoireTier tier, String name) {
        if (stack.isEmpty() || tier == null || GrimoireTier.fromItem(stack.getItem()) != tier) return null;
        List<SpellBookStore.StoredSpell> spells = new ArrayList<>(get(stack, tier));
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i).name.equalsIgnoreCase(name)) {
                SpellBookStore.StoredSpell removed = spells.remove(i);
                write(stack, spells);
                return removed;
            }
        }
        return null;
    }

    public static synchronized SpellBookStore.StoredSpell find(ItemStack stack, GrimoireTier tier, String name) {
        return find(get(stack, tier), name);
    }

    private static SpellBookStore.StoredSpell find(List<SpellBookStore.StoredSpell> spells, String name) {
        return spells.stream().filter(spell -> spell.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static NbtList getSpellList(ItemStack stack) {
        NbtCompound custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        NbtCompound root = custom.getCompound(ROOT_KEY);
        return root.getList(SPELLS_KEY, NbtElement.COMPOUND_TYPE);
    }

    private static void write(ItemStack stack, List<SpellBookStore.StoredSpell> spells) {
        NbtCompound custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (SpellBookStore.StoredSpell spell : spells) {
            NbtCompound tag = new NbtCompound();
            tag.putString("Name", spell.name);
            tag.putString("Behavior", spell.behavior == null ? "" : spell.behavior);
            NbtList components = new NbtList();
            if (spell.components != null) spell.components.forEach(value -> components.add(net.minecraft.nbt.NbtString.of(value)));
            tag.put("Components", components);
            list.add(tag);
        }
        root.put(SPELLS_KEY, list);
        custom.put(ROOT_KEY, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
    }

    /** Retained as no-op compatibility hooks for older initialization code. */
    public static void load(net.minecraft.server.MinecraftServer server) {}
    public static void save(net.minecraft.server.MinecraftServer server) {}
}
