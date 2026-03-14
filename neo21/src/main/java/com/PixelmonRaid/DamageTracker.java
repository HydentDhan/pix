package com.PixelmonRaid;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class DamageTracker {
   private final Map<UUID, Integer> damageMap = new HashMap<>();
   private final Map<UUID, Long> lastHitMap = new HashMap<>();

   public void addDamage(UUID player, int damage) {
      this.damageMap.put(player, this.damageMap.getOrDefault(player, 0) + damage);
      try {
         if (ModList.get().isLoaded("pixelmonbattlepass")) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
               ServerPlayer playerEntity = server.getPlayerList().getPlayer(player);
               if (playerEntity != null) {
                  Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                  Method addDmgMethod = bpQuestsClass.getMethod("addRaidDamageProgress", ServerPlayer.class, Integer.TYPE);
                  addDmgMethod.invoke(null, playerEntity, damage);
               }
            }
         }
      } catch (Exception var7) {
      }
   }

   public int getDamage(UUID player) {
      return this.damageMap.getOrDefault(player, 0);
   }

   public void resetDamage(UUID player) {
      this.damageMap.put(player, 0);
   }

   public void clear() {
      this.damageMap.clear();
      this.lastHitMap.clear();
   }

   public Map<UUID, Integer> getAllDamage() {
      return new HashMap<>(this.damageMap);
   }

   public int getRank(UUID player) {
      if (!this.damageMap.containsKey(player)) {
         return 0;
      } else {
         int myDmg = this.getDamage(player);
         if (myDmg <= 0) {
            return 999;
         } else {
            int rank = 1;
            Iterator<Integer> var4 = this.damageMap.values().iterator();

            while(var4.hasNext()) {
               int dmg = var4.next();
               if (dmg > myDmg) {
                  ++rank;
               }
            }
            return rank;
         }
      }
   }

   public void updateLastHit(UUID player) {
      this.lastHitMap.put(player, System.currentTimeMillis());
   }

   public long getLastHitTimestamp(UUID player) {
      return this.lastHitMap.getOrDefault(player, 0L);
   }
}