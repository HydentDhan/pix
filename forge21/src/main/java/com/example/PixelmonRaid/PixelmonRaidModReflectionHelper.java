package com.example.PixelmonRaid;
import net.minecraft.world.inventory.MenuType;
import java.lang.reflect.Field;

public final class PixelmonRaidModReflectionHelper {
   private PixelmonRaidModReflectionHelper() {}
   public static void assignContainer(MenuType<?> container) {
      try {
         Field f = PixelmonRaidMod.class.getDeclaredField("RAID_REWARDS_CONTAINER");
         f.setAccessible(true);
         f.set((Object)null, container);
      } catch (Throwable var2) {}
   }
}