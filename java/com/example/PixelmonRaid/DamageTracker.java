package com.example.PixelmonRaid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DamageTracker {
    private final Map<UUID, Integer> damageMap = new HashMap<>();
    private final Map<UUID, Long> lastHitMap = new HashMap<>();

    public void addDamage(UUID player, int damage) {
        damageMap.put(player, damageMap.getOrDefault(player, 0) + damage);

        try {
            if (net.minecraftforge.fml.ModList.get().isLoaded("pixelmonbattlepass")) {
                net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    net.minecraft.entity.player.ServerPlayerEntity playerEntity = server.getPlayerList().getPlayer(player);
                    if (playerEntity != null) {Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                        java.lang.reflect.Method addDmgMethod = bpQuestsClass.getMethod("addRaidDamageProgress", net.minecraft.entity.player.ServerPlayerEntity.class, int.class);
                        addDmgMethod.invoke(null, playerEntity, damage);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public int getDamage(UUID player) {
        return damageMap.getOrDefault(player, 0);
    }

    public void resetDamage(UUID player) {
        damageMap.put(player, 0);
    }

    public void clear() {
        damageMap.clear();
        lastHitMap.clear();
    }

    public Map<UUID, Integer> getAllDamage() {
        return new HashMap<>(damageMap);
    }

    public int getRank(UUID player) {
        if (!damageMap.containsKey(player)) return 0;
        int myDmg = getDamage(player);
        if (myDmg <= 0) return 999;

        int rank = 1;
        for (int dmg : damageMap.values()) {
            if (dmg > myDmg) rank++;
        }
        return rank;
    }

    public void updateLastHit(UUID player) {
        lastHitMap.put(player, System.currentTimeMillis());
    }

    public long getLastHitTimestamp(UUID player) {
        return lastHitMap.getOrDefault(player, 0L);
    }
}
