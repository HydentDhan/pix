package com.example.PixelmonRaid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

public class DropSet {
   private final List<ItemStack> drops = new ArrayList();
   private final Random random = new Random();

   public void addDrop(ItemStack itemStack) {
      this.drops.add(itemStack);
   }

   public List<ItemStack> getAllDrops() {
      return new ArrayList(this.drops);
   }

   public ItemStack getRandomDrop() {
      return this.drops.isEmpty() ? ItemStack.EMPTY : (ItemStack)this.drops.get(this.random.nextInt(this.drops.size()));
   }

   public CompoundNBT serializeNBT() {
      return new CompoundNBT();
   }

   public void deserializeNBT(CompoundNBT nbt) {}

   public String toString() {
      StringBuilder sb = new StringBuilder("DropSet{");
      Iterator var2 = this.drops.iterator();
      while(var2.hasNext()) {
         ItemStack item = (ItemStack)var2.next();
         sb.append(item.getHoverName().getString()).append(", ");
      }
      sb.append("}");
      return sb.toString();
   }
}