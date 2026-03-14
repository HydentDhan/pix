package com.PixelmonRaid;

import java.util.Iterator;
import java.util.UUID;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class RaidBattleStarter {
   public static boolean startBattleForPlayer(RaidSession session, ServerLevel world, ServerPlayer player) {
      try {
         UUID bossUUID = null;
         Iterator<UUID> var4 = session.getBossEntityUUIDs().iterator();
         if (var4.hasNext()) {
            bossUUID = var4.next();
         }

         if (bossUUID == null) {
            player.sendSystemMessage(Component.literal("§cError: Boss UUID missing."));
            return false;
         } else {
            Entity entity = world.getEntity(bossUUID);
            if (entity == null) {
               player.sendSystemMessage(Component.literal("§cError: Boss entity not found (Chunks unloaded?)."));
               return false;
            } else if (!(entity instanceof PixelmonEntity bossPixelmon)) {
               return false;
            } else {
               if (BattleRegistry.getBattle(player) != null) {
                  player.sendSystemMessage(Component.literal("§cYou are already in a battle!"));
                  return false;
               } else {
                  Pokemon[] party = StorageProxy.getPartyNow(player).getAll();
                  boolean hasAblePokemon = false;
                  for (Pokemon p : party) {
                     if (p != null && !p.isEgg() && p.getHealth() > 0) {
                        hasAblePokemon = true;
                        break;
                     }
                  }

                  if (!hasAblePokemon) {
                     player.sendSystemMessage(Component.literal("§cYou have no able Pokémon to fight!"));
                     return false;
                  } else {
                     PlayerParticipant pp = new PlayerParticipant(player, party);
                     WildPixelmonParticipant wp = new WildPixelmonParticipant(new PixelmonEntity[]{bossPixelmon});
                     BattleBuilder.builder().registryAccess(world.registryAccess()).teamOne(new BattleParticipant[]{pp}).teamTwo(new BattleParticipant[]{wp}).rules(new BattleRules(java.util.List.of())).start();
                     return true;
                  }
               }
            }
         }
      } catch (Exception var12) {
         System.out.println("[PixelmonRaid] Failed to start battle: " + var12.getMessage());
         var12.printStackTrace();
         return false;
      }
   }
}