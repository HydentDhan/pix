package com.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class RaidBattleStarter {
   public static boolean startBattleForPlayer(RaidSession session, ServerWorld world, ServerPlayerEntity player) {
      try {
         UUID bossUUID = null;
         Iterator<UUID> var4 = session.getBossEntityUUIDs().iterator();
         if (var4.hasNext()) {
            UUID id = var4.next();
            bossUUID = id;
         }

         if (bossUUID == null) {
            player.sendMessage(new StringTextComponent("§cError: Boss UUID missing."), player.getUUID());
            return false;
         } else {
            Entity entity = world.getEntity(bossUUID);
            if (entity == null) {
               player.sendMessage(new StringTextComponent("§cError: Boss entity not found (Chunks unloaded?)."), player.getUUID());
               return false;
            } else if (!(entity instanceof PixelmonEntity)) {
               return false;
            } else {
               PixelmonEntity bossPixelmon = (PixelmonEntity)entity;
               if (BattleRegistry.getBattle(player) != null) {
                  player.sendMessage(new StringTextComponent("§cYou are already in a battle!"), player.getUUID());
                  return false;
               } else {
                  Pokemon[] party = StorageProxy.getParty(player).getAll();
                  boolean hasAblePokemon = false;
                  Pokemon[] var8 = party;
                  int var9 = party.length;

                  for(int var10 = 0; var10 < var9; ++var10) {
                     Pokemon p = var8[var10];
                     if (p != null && !p.isEgg() && p.getHealth() > 0) {
                        hasAblePokemon = true;
                        break;
                     }
                  }

                  if (!hasAblePokemon) {
                     player.sendMessage(new StringTextComponent("§cYou have no able Pokémon to fight!"), player.getUUID());
                     return false;
                  } else {
                     PlayerParticipant pp = new PlayerParticipant(player, party);
                     WildPixelmonParticipant wp = new WildPixelmonParticipant(new PixelmonEntity[]{bossPixelmon});
                     BattleRegistry.startBattle(new BattleParticipant[]{pp}, new BattleParticipant[]{wp}, new BattleRules());
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