package com.PixelmonRaid;

import java.lang.reflect.Field;
import net.minecraft.inventory.container.ContainerType;

public final class PixelmonRaidModReflectionHelper {
   private PixelmonRaidModReflectionHelper() {
   }

   public static void assignContainer(ContainerType<?> container) {
      try {
         Field f = PixelmonRaidMod.class.getDeclaredField("RAID_REWARDS_CONTAINER");
         f.setAccessible(true);
         f.set((Object)null, container);
      } catch (Throwable var2) {
      }

   }
}
