package dev.arcana.screen;

import dev.arcana.block.ArcanaBlocks;
import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;

public final class MagicWriterScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final ScreenHandlerContext context;
    public MagicWriterScreenHandler(int id, PlayerInventory player) {
        this(id, player, new SimpleInventory(3), ScreenHandlerContext.EMPTY);
    }
    public MagicWriterScreenHandler(int id, PlayerInventory player, MagicWriterBlockEntity be, ScreenHandlerContext context) {
        this(id, player, (Inventory) be, context);
    }
    private MagicWriterScreenHandler(int id, PlayerInventory player, Inventory inventory, ScreenHandlerContext context) {
        super(ArcanaBlocks.MAGIC_WRITER_SCREEN_HANDLER, id);
        checkSize(inventory, 3);
        this.inventory = inventory;
        this.context = context;
        inventory.onOpen(player.player);
        addSlot(new Slot(inventory, 0, 28, 52) {
            public boolean canInsert(ItemStack stack) { return GrimoireTier.fromItem(stack.getItem()) != null; }
            public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(inventory, 1, 28, 97) {
            public boolean canInsert(ItemStack stack) { return stack.isOf(ArcanaItems.MAGIC_PAPER); }
            public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(inventory, 2, 99, 75) {
            public boolean canInsert(ItemStack stack) { return false; }
            public int getMaxItemCount() { return 1; }
        });
        int startX = 199;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(player, col + row * 9 + 9, startX + col * 18, 194 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(player, col, startX + col * 18, 248));
    }
    public Inventory getWriterInventory() { return inventory; }
    public boolean canUse(PlayerEntity player) { return ScreenHandler.canUse(context, player, ArcanaBlocks.MAGIC_WRITER); }
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasStack()) return empty;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index < 3) {
            if (!insertItem(stack, 3, slots.size(), true)) return empty;
        } else {
            boolean moved = false;
            if (GrimoireTier.fromItem(stack.getItem()) != null) moved = insertItem(stack, 0, 1, false);
            else if (stack.isOf(ArcanaItems.MAGIC_PAPER)) moved = insertItem(stack, 1, 2, false);
            if (!moved) return empty;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        if (stack.getCount() == original.getCount()) return empty;
        slot.onTakeItem(player, stack);
        return original;
    }
    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
    }
}