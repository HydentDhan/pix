package com.example.PixelmonRaid.integration;

import com.example.PixelmonRaid.RaidSaveData;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.bukkit.entity.Player;

public class RaidTokenEconomy extends EconomyProvider {
   @Override
   public String getName() { return "RaidTokens"; }

   @Override
   public double getBalance(Player player) {
      try {
         ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
         return (double)RaidSaveData.get(world).getTokens(player.getUniqueId());
      } catch (Exception var3) {
         var3.printStackTrace();
         return 0.0D;
      }
   }

   @Override
   public void deposit(Player player, double amount) {
      try {
         ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
         RaidSaveData.get(world).addTokens(player.getUniqueId(), (int)amount);
      } catch (Exception var5) { var5.printStackTrace(); }
   }

   @Override
   public void withdraw(Player player, double amount) {
      try {
         ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
         RaidSaveData.get(world).removeTokens(player.getUniqueId(), (int)amount);
      } catch (Exception var5) { var5.printStackTrace(); }
   }
}