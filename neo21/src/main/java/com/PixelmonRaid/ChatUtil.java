package com.PixelmonRaid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChatUtil {
   public static Component styled(String content, ChatFormatting color) {
      return Component.literal(content).withStyle(color);
   }

   public static void sendInfo(ServerPlayer player, String msg) {
      try {
         player.sendSystemMessage(styled("[Raid] " + msg, ChatFormatting.AQUA));
      } catch (Throwable ignored) {}
   }

   public static void sendSuccess(ServerPlayer player, String msg) {
      try {
         player.sendSystemMessage(styled("[Raid] " + msg, ChatFormatting.GREEN));
      } catch (Throwable ignored) {}
   }

   public static void sendWarn(ServerPlayer player, String msg) {
      try {
         player.sendSystemMessage(styled("[Raid] " + msg, ChatFormatting.YELLOW));
      } catch (Throwable ignored) {}
   }

   public static void broadcast(ServerPlayer player, String msg) {
      try {
         // 1.21 uses broadcastSystemMessage directly
         player.server.getPlayerList().broadcastSystemMessage(styled("[Raid] " + msg, ChatFormatting.GOLD), false);
      } catch (Throwable ignored) {}
   }
}