package com.PixelmonRaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

public final class TeleportConfirmationHandler {
   private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
   private static final long EXPIRE_MS = 1000L * 30L;

   private TeleportConfirmationHandler() {}

   private static final class Pending {
      final BlockPos pos;
      Pending(BlockPos pos, long expiry) {
         this.pos = pos;
      }
   }

   public static void register() {
      NeoForge.EVENT_BUS.register(new TeleportConfirmationHandler());
   }

   public static void offerTeleport(ServerPlayer player, BlockPos pos) {
      if (player == null || pos == null) return;
      long expire = System.currentTimeMillis() + EXPIRE_MS;
      PENDING.put(player.getUUID(), new Pending(pos, expire));
   }

   public static void confirm(ServerPlayer player) {
      UUID uid = player.getUUID();
      Pending p = PENDING.get(uid);
      if (p == null) {
         player.sendSystemMessage(Component.literal("§cNo pending teleport request found."));
         return;
      }

      try {
         player.teleportTo(player.serverLevel(), p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5, player.getYRot(), player.getXRot());
         player.sendSystemMessage(Component.literal("§aTeleported to the raid!"));
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         PENDING.remove(uid);
      }
   }

   @SubscribeEvent
   public void onServerChat(ServerChatEvent event) {
      if (event == null || !(event.getPlayer() instanceof ServerPlayer)) return;
      ServerPlayer player = (ServerPlayer) event.getPlayer();
      String msg = event.getMessage().getString().trim().toLowerCase();

      if (msg.equals("yes") || msg.equals("y")) {
         if (PENDING.containsKey(player.getUUID())) {
            confirm(player);
            event.setCanceled(true);
         }
      }
   }
}