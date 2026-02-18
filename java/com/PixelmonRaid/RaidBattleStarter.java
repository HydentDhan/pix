package com.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.text.StringTextComponent;

import java.util.UUID;

public class RaidBattleStarter {
    public static boolean startBattleForPlayer(RaidSession session, ServerWorld world, ServerPlayerEntity player) {
        try {
            UUID bossUUID = null;
            for(UUID id : session.getBossEntityUUIDs()) {
                bossUUID = id;
                break;
            }
            if (bossUUID == null) {
                player.sendMessage(new StringTextComponent("§cError: Boss UUID missing."), player.getUUID());
                return false;
            }

            Entity entity = world.getEntity(bossUUID);
            if (entity == null) {

                player.sendMessage(new StringTextComponent("§cError: Boss entity not found (Chunks unloaded?)."), player.getUUID());
                return false;
            }

            if (!(entity instanceof PixelmonEntity)) return false;
            PixelmonEntity bossPixelmon = (PixelmonEntity) entity;


            if (BattleRegistry.getBattle(player) != null) {
                player.sendMessage(new StringTextComponent("§cYou are already in a battle!"), player.getUUID());
                return false;
            }


            Pokemon[] party = StorageProxy.getParty(player).getAll();


            boolean hasAblePokemon = false;
            for(Pokemon p : party) {
                if (p != null && !p.isEgg() && p.getHealth() > 0) {
                    hasAblePokemon = true;
                    break;
                }
            }
            if (!hasAblePokemon) {
                player.sendMessage(new StringTextComponent("§cYou have no able Pokémon to fight!"), player.getUUID());
                return false;
            }

            PlayerParticipant pp = new PlayerParticipant(player, party);
            WildPixelmonParticipant wp = new WildPixelmonParticipant(bossPixelmon);


            BattleRegistry.startBattle(
                    new BattleParticipant[]{pp},
                    new BattleParticipant[]{wp},
                    new BattleRules()
            );

            return true;
        } catch (Exception e) {
            System.out.println("[PixelmonRaid] Failed to start battle: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}