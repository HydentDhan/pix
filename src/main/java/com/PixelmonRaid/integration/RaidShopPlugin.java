package com.example.PixelmonRaid.integration;

import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.plugin.java.JavaPlugin;

public class RaidShopPlugin extends JavaPlugin {
   public void onEnable() {
      try {
         ShopGuiPlusApi.registerEconomyProvider(new RaidTokenEconomy());
         this.getLogger().info("Successfully bridged Forge Raid Tokens to ShopGUI+!");
      } catch (Exception var2) {
         this.getLogger().severe("Failed to hook into ShopGUI+!");
      }

   }
}
