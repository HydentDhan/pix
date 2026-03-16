package com.PixelmonRaid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pixelmonmod.pixelmon.api.battles.BattleEndCause;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;

import net.minecraft.world.entity.Entity;

public class RaidBossParticipant extends WildPixelmonParticipant {

   private final PixelmonEntity bossEntity;
   private final UUID raidId;

   public RaidBossParticipant(PixelmonEntity boss, UUID raidId) {
      super(boss);
      this.bossEntity = boss;
      this.raidId = raidId;
   }

   public UUID getRaidId() {
      return this.raidId;
   }

   @Override
   public Entity getEntity() {
      return this.bossEntity;
   }


   @Override
   public boolean hasMorePokemon() { return true; }

   @Override
   public boolean hasRemainingPokemon() { return true; }

   @Override
   public int countAblePokemon() { return this.allPokemon != null ? this.allPokemon.length : 1; }

   @Override
   public PixelmonWrapper getFaintedPokemon() { return null; }

   @Override
   public int countFaintedPokemon() { return 0; }

   @Override
   public boolean faintedLastTurn() { return false; }

   @Override
   public List<PixelmonWrapper> getActiveUnfaintedPokemon() {
      return new ArrayList<>(this.controlledPokemon);
   }

   @Override
   public void endBattle(BattleEndCause cause) {
      for (PixelmonWrapper pw : this.controlledPokemon) {
         pw.resetBattleEvolution();
         if (pw.getEntity() != null) {
            pw.getEntity().onEndBattle();
         }
      }
   }
}