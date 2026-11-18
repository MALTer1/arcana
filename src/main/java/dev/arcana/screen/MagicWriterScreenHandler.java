package dev.arcana.screen;

import dev.arcana.block.ArcanaBlocks;
import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;

public final class MagicWriterScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final ScreenHandlerContext context;

    public MagicWriterScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), ScreenHandlerContext.EMPTY);
    }

    public MagicWriterScreenHandler(int syncId, PlayerInventory playerInventory,
                                    MagicWriterBlockEntity blockEntity, ScreenHandlerContext context) {
        this(syncId, playerInventory, (Inventory) blockEntity, context);
    }

    private MagicWriterScreenHandler(int syncId, PlayerInventory playerInventory,
                                     Inventory inventory, ScreenHandlerContext context) {
        super(ArcanaBlocks.MAGIC_WRITER_SCREEN_HANDLER, syncId);
        checkSize(inventory, 2);
        this.inventory = inventory;
        this.context = context;
        inventory.onOpen(playerInventory.player);

        addSlot(new Slot(inventory, 0, 48, 66) {
            @Override public boolean canInsert(ItemStack stack) { return GrimoireTier.fromItem(stack.getItem()) != null; }
            @Override public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(inventory, 1, 48, 110) {
            @Override public boolean canInsert(ItemStack stack) { return stack.isOf(ArcanaItems.MAGIC_PAPER); }
            @Override public int getMaxItemCount() { return 64; }
        });

        // Centered player inventory, kept in its own lower panel.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 104 + col * 18, 158 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 104 + col * 18, 212));
        }
    }

    public Inventory getWriterInventory() { return inventory; }

    @Override public boolean canUse(PlayerEntity player) {
        return ScreenHandler.canUse(context, player, ArcanaBlocks.MAGIC_WRITER);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasStack()) return original;
        ItemStack stack = slot.getStack();
        original = stack.copy();
        if (index < 2) {
            if (!insertItem(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (GrimoireTier.fromItem(stack.getItem()) != null) moved = insertItem(stack, 0, 1, false);
            else if (stack.isOf(ArcanaItems.MAGIC_PAPER)) moved = insertItem(stack, 1, 2, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, stack);
        return original;
    }

    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
    }
}
