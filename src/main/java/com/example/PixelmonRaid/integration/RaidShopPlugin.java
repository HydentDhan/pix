package com.example.PixelmonRaid.integration;

import org.bukkit.plugin.java.JavaPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;

public class RaidShopPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        try {
            ShopGuiPlusApi.registerEconomyProvider(new RaidTokenEconomy());
            getLogger().info("Successfully bridged Forge Raid Tokens to ShopGUI+!");
        } catch (Exception e) {
            getLogger().severe("Failed to hook into ShopGUI+!");
        }
    }
}