package com.PixelmonRaid;

import java.util.List;

import net.minecraft.entity.player.ServerPlayerEntity;

public final class PacketHandler {
    public static void registerPackets() {
    }

    public static void sendToPlayer(ServerPlayerEntity player, Object packet) {}
    public static void sendBossBarToPlayer(ServerPlayerEntity player, float percent, String title) {}
    public static void sendRewardScreenPacketToPlayer(ServerPlayerEntity player, List<String> rewards) {}
    public static void sendAdminOpenPacketToPlayer(ServerPlayerEntity player, List<String> rewards) {}
    public static void sendEndResultsToPlayer(ServerPlayerEntity player, List<String> lines) {}
}