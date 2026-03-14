package com.PixelmonRaid;

import net.minecraft.network.chat.Component;

public final class CommandTexts {
   private CommandTexts() {
   }

   public static Component of(String s) {
      return Component.literal(s);
   }
}