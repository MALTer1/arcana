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
 private final Inventory inventory; private final ScreenHandlerContext context;
 public MagicWriterScreenHandler(int id,PlayerInventory player){this(id,player,new SimpleInventory(3),ScreenHandlerContext.EMPTY);}
 public MagicWriterScreenHandler(int id,PlayerInventory player,MagicWriterBlockEntity be,ScreenHandlerContext ctx){this(id,player,(Inventory)be,ctx);}
 private MagicWriterScreenHandler(int id,PlayerInventory player,Inventory inv,ScreenHandlerContext ctx){
  super(ArcanaBlocks.MAGIC_WRITER_SCREEN_HANDLER,id);checkSize(inv,3);inventory=inv;context=ctx;inv.onOpen(player.player);
  addSlot(new Slot(inv,0,48,66){public boolean canInsert(ItemStack s){return GrimoireTier.fromItem(s.getItem())!=null;}public int getMaxItemCount(){return 1;}});
  addSlot(new Slot(inv,1,48,110){public boolean canInsert(ItemStack s){return s.isOf(ArcanaItems.MAGIC_PAPER)||s.isOf(ArcanaItems.SPELL_PAGE);}});
  addSlot(new Slot(inv,2,88,88){public boolean canInsert(ItemStack s){return false;}public int getMaxItemCount(){return 1;}});
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,104+col*18,158+row*18));
  for(int col=0;col<9;col++)addSlot(new Slot(player,col,104+col*18,212));
 }
 public Inventory getWriterInventory(){return inventory;}
 public boolean canUse(PlayerEntity p){return ScreenHandler.canUse(context,p,ArcanaBlocks.MAGIC_WRITER);}
 public ItemStack quickMove(PlayerEntity p,int index){
  ItemStack empty=ItemStack.EMPTY;Slot slot=slots.get(index);if(slot==null||!slot.hasStack())return empty;
  ItemStack stack=slot.getStack(),original=stack.copy();
  if(index<3){if(!insertItem(stack,3,slots.size(),true))return empty;}
  else {boolean moved=false;if(GrimoireTier.fromItem(stack.getItem())!=null)moved=insertItem(stack,0,1,false);
   else if(stack.isOf(ArcanaItems.MAGIC_PAPER)||stack.isOf(ArcanaItems.SPELL_PAGE))moved=insertItem(stack,1,2,false);
   if(!moved)return empty;}
  if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();
  if(stack.getCount()==original.getCount())return empty;slot.onTakeItem(p,stack);return original;
 }
 public void onClosed(PlayerEntity p){super.onClosed(p);inventory.onClose(p);}
}