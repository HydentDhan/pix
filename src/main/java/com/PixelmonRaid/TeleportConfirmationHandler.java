package com.PixelmonRaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TeleportConfirmationHandler {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 1000L * 30L;

    private TeleportConfirmationHandler() {}

    private static final class Pending {
        final BlockPos pos;
        Pending(BlockPos pos, long expiry) { this.pos = pos; }
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new TeleportConfirmationHandler());
    }

    public static void offerTeleport(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) return;
        long expire = System.currentTimeMillis() + EXPIRE_MS;
        PENDING.put(player.getUUID(), new Pending(pos, expire));
    }

    public static void confirm(ServerPlayerEntity player) {
        UUID uid = player.getUUID();
        Pending p = PENDING.get(uid);
        if (p == null) {
            player.sendMessage(new StringTextComponent("§cNo pending teleport request found."), uid);
            return;
        }

        try {
            player.teleportTo(player.getLevel(), p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5, player.yRot, player.xRot);
            player.sendMessage(new StringTextComponent("§aTeleported to the raid!"), uid);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            PENDING.remove(uid);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event == null || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        String msg = event.getMessage().trim().toLowerCase();

        if (msg.equals("yes") || msg.equals("y")) {
            if (PENDING.containsKey(player.getUUID())) {
                confirm(player);
                event.setCanceled(true);
            }
        }
    }
}