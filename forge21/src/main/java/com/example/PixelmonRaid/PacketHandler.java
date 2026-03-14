package com.example.PixelmonRaid;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
public final class PacketHandler {
   public static void registerPackets() {}
   public static void sendToPlayer(ServerPlayer player, Object packet) {}
   public static void sendBossBarToPlayer(ServerPlayer player, float percent, String title) {}
   public static void sendRewardScreenPacketToPlayer(ServerPlayer player, List<String> rewards) {}
   public static void sendAdminOpenPacketToPlayer(ServerPlayer player, List<String> rewards) {}
   public static void sendEndResultsToPlayer(ServerPlayer player, List<String> lines) {}
}