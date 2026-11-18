package dev.arcana.block.entity;
import dev.arcana.block.ArcanaBlocks;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.screen.MagicWriterScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
public final class MagicWriterBlockEntity extends BlockEntity implements Inventory,NamedScreenHandlerFactory {
 private final DefaultedList<ItemStack> items=DefaultedList.ofSize(3,ItemStack.EMPTY);
 public MagicWriterBlockEntity(BlockPos pos,BlockState state){super(ArcanaBlocks.MAGIC_WRITER_ENTITY,pos,state);}
 public int size(){return 3;} public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
 public ItemStack getStack(int slot){return items.get(slot);}
 public ItemStack removeStack(int slot,int amount){ItemStack r=Inventories.splitStack(items,slot,amount);if(!r.isEmpty())markDirty();return r;}
 public ItemStack removeStack(int slot){ItemStack r=Inventories.removeStack(items,slot);if(!r.isEmpty())markDirty();return r;}
 public void setStack(int slot,ItemStack stack){
  if(slot==0&&!stack.isEmpty()&&GrimoireTier.fromItem(stack.getItem())==null)return;
  if(slot==1&&!stack.isEmpty()&&!stack.isOf(ArcanaItems.MAGIC_PAPER)&&!stack.isOf(ArcanaItems.SPELL_PAGE))return;
  items.set(slot,stack);if(stack.getCount()>getMaxCountPerStack())stack.setCount(getMaxCountPerStack());markDirty();
 }
 public void setOutput(ItemStack stack){items.set(2,stack);markDirty();}
 public GrimoireTier getInsertedTier(){ItemStack s=items.get(0);return s.isEmpty()?null:GrimoireTier.fromItem(s.getItem());}
 public boolean consumeInput(){ItemStack s=items.get(1);if(s.isEmpty())return false;s.decrement(1);markDirty();return true;}
 public void clear(){items.clear();markDirty();}
 public void markDirty(){super.markDirty();if(world!=null&&!world.isClient)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
 public boolean canPlayerUse(PlayerEntity p){return !isRemoved()&&p.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
 public Text getDisplayName(){return Text.translatable("container.arcana.magic_writer");}
 public ScreenHandler createMenu(int id,PlayerInventory inv,PlayerEntity p){return new MagicWriterScreenHandler(id,inv,this,ScreenHandlerContext.create(world,pos));}
 protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup regs){super.writeNbt(nbt,regs);Inventories.writeNbt(nbt,items,regs);}
 protected void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup regs){super.readNbt(nbt,regs);Inventories.readNbt(nbt,items,regs);}
 public Packet<ClientPlayPacketListener> toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup regs){return createNbt(regs);}
}