package dev.arcana.spell;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public final class SpellPageStore {
    private static final String KEY = "ArcanaSpellPage";
    private SpellPageStore() {}

    public static SpellBookStore.StoredSpell get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (!root.contains(KEY, NbtElement.COMPOUND_TYPE)) return null;
        NbtCompound tag = root.getCompound(KEY);
        NbtList list = tag.getList("Components", NbtElement.STRING_TYPE);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) ids.add(list.getString(i));
        String name = tag.getString("Name");
        if (name.isBlank() || ids.isEmpty()) return null;
        return new SpellBookStore.StoredSpell(name, ids, tag.getString("Behavior"),
            tag.getBoolean("Legendary"), tag.getString("LegendaryEffect"), tag.getString("CustomDescription"));
    }

    public static void set(ItemStack stack, SpellBookStore.StoredSpell spell) {
        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        NbtCompound tag = new NbtCompound();
        tag.putString("Name", spell.name);
        tag.putString("Behavior", spell.behavior == null ? "" : spell.behavior);
        tag.putBoolean("Legendary", spell.legendary);
        tag.putString("LegendaryEffect", spell.legendaryEffect == null ? "" : spell.legendaryEffect);
        tag.putString("CustomDescription", spell.customDescription == null ? "" : spell.customDescription);
        NbtList ids = new NbtList();
        if (spell.components != null) spell.components.forEach(id -> ids.add(NbtString.of(id)));
        tag.put("Components", ids);
        root.put(KEY, tag);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(spell.legendary ? "✦ " + spell.name : spell.name));
    }
}
