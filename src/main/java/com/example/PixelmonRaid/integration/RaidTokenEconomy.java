package com.example.PixelmonRaid.integration;

import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.bukkit.entity.Player;
import com.example.PixelmonRaid.RaidSaveData;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraft.world.server.ServerWorld;

public class RaidTokenEconomy extends EconomyProvider {

    @Override
    public String getName() {
        // FIXED: This now perfectly matches 'customEconomy: RaidTokens' in your YAML
        return "RaidTokens";
    }

    @Override
    public double getBalance(Player player) {
        try {
            ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
            return RaidSaveData.get(world).getTokens(player.getUniqueId());
        } catch (Exception e) {
            e.printStackTrace(); // FIXED: This will print the error to your console instead of hiding it!
            return 0;
        }
    }

    @Override
    public void deposit(Player player, double amount) {
        try {
            ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
            RaidSaveData.get(world).addTokens(player.getUniqueId(), (int)amount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void withdraw(Player player, double amount) {
        try {
            ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
            RaidSaveData.get(world).removeTokens(player.getUniqueId(), (int)amount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}