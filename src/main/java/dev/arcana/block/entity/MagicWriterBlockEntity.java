package dev.arcana.block.entity;

import dev.arcana.block.ArcanaBlocks;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.screen.MagicWriterScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public final class MagicWriterBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

    public MagicWriterBlockEntity(BlockPos pos, BlockState state) {
        super(ArcanaBlocks.MAGIC_WRITER_ENTITY, pos, state);
    }

    @Override
    public int size() { return items.size(); }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = net.minecraft.inventory.Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = net.minecraft.inventory Inventories.removeStack(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0 && !stack.isEmpty() && GrimoireTier.fromItem(stack.getItem()) == null) return;
        if (slot == 1 && !stack.isEmpty() && !stack.isOf(ArcanaItems.MAGIC_PAPER)) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }

    public GrimoireTier getInsertedTier() {
        ItemStack book = items.get(0);
        return book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
    }

    public boolean consumePaper() {
        ItemStack paper = items.get(1);
        if (paper.isEmpty() || !paper.isOf(ArcanaItems.MAGIC_PAPER)) return false;
        paper.decrement(1);
        markDirty();
        return true;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return !isRemoved() && player.squaredDistanceTo(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.arcana.magic_writer");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MagicWriterScreenHandler(syncId, playerInventory, this,
            ScreenHandlerContext.create(world, pos));
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        net.minecraft.inventory.Inventories.writeNbt(nbt, items, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        net.minecraft.inventory.Inventories.readNbt(nbt, items, registries);
    }
}
