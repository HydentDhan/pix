package com.PixelmonRaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber
public class RaidLeaderboardUIListener {
   public static final Map<UUID, Integer> openPages = new ConcurrentHashMap<>();

   @SubscribeEvent
   public static void onItemToss(ItemTossEvent event) {
      if (event.getEntity() != null) {
         ItemStack stack = event.getEntity().getItem();
         if (isLeaderboardItem(stack)) {
            event.getEntity().discard();
            if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent.Pre event) {
      if (!event.getEntity().level().isClientSide) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         boolean guiRestored = false;

         if (openPages.containsKey(player.getUUID())) {

            // 1. CURSOR THEFT PROTECTION
            ItemStack cursor = player.containerMenu.getCarried();
            if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
               String name = cursor.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase();
               boolean handled = false;

               player.containerMenu.setCarried(ItemStack.EMPTY);
               player.connection.send(new ClientboundContainerSetSlotPacket(-1, 0, -1, ItemStack.EMPTY));

               int page;
               if (name.contains("next page")) {
                  page = openPages.get(player.getUUID());
                  if (page > 0) {
                     player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                     RaidLeaderboardCommand.openLeaderboard(player, page + 1);
                     handled = true;
                  }
               } else if (name.contains("previous page")) {
                  page = openPages.get(player.getUUID());
                  if (page > 0) {
                     player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                     RaidLeaderboardCommand.openLeaderboard(player, page - 1);
                     handled = true;
                  }
               }

               if (!handled) {
                  guiRestored = true;
               }
            }

            // 2. INVENTORY SWEEP PROTECTION
            for(int page = 0; page < player.getInventory().getContainerSize(); ++page) {
               ItemStack st = player.getInventory().getItem(page);
               if (!st.isEmpty() && isLeaderboardItem(st)) {
                  player.getInventory().removeItemNoUpdate(page);
                  guiRestored = true;
               }
            }

            if (guiRestored) {
               player.inventoryMenu.broadcastChanges();
               if (player.containerMenu != null) {
                  player.containerMenu.broadcastChanges();
               }
               int page = openPages.getOrDefault(player.getUUID(), 1);
               if (page == 0) {
                  RaidLeaderboardCommand.openLastRaidLeaderboard(player);
               } else {
                  RaidLeaderboardCommand.openLeaderboard(player, page);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onContainerClose(PlayerContainerEvent.Close event) {
      // Memory Leak Prevention
      openPages.remove(event.getEntity().getUUID());
   }

   private static boolean isLeaderboardItem(ItemStack stack) {
      if (stack.isEmpty()) return false;
      return GuiItemUtil.hasGuiTag(stack) && GuiItemUtil.getGuiTag(stack).getBoolean("RaidGuiItem");
   }
}