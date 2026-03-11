package com.example.PixelmonRaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SSetSlotPacket;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent.Close;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class RaidLeaderboardUIListener {
   public static final Map<UUID, Integer> openPages = new ConcurrentHashMap<>();

   @SubscribeEvent
   public static void onItemToss(ItemTossEvent event) {
      if (event.getEntityItem() != null) {
         ItemStack stack = event.getEntityItem().getItem();
         if (isLeaderboardItem(stack)) {
            event.getEntityItem().remove();
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.START && !event.player.level.isClientSide) {
         ServerPlayerEntity player = (ServerPlayerEntity)event.player;
         boolean guiRestored = false;

         if (openPages.containsKey(player.getUUID())) {
            ItemStack cursor = player.inventory.getCarried();
            if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
               String name = TextFormatting.stripFormatting(cursor.getHoverName().getString()).toLowerCase();
               boolean handled = false;
               player.inventory.setCarried(ItemStack.EMPTY);
               player.connection.send(new SSetSlotPacket(-1, -1, ItemStack.EMPTY));
               int page;

               if (name.contains("next page")) {
                  page = (Integer)openPages.get(player.getUUID());
                  if (page > 0) {
                     player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
                     RaidLeaderboardCommand.openLeaderboard(player, page + 1);
                     handled = true;
                  }
               } else if (name.contains("previous page")) {
                  page = (Integer)openPages.get(player.getUUID());
                  if (page > 0) {
                     player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
                     RaidLeaderboardCommand.openLeaderboard(player, page - 1);
                     handled = true;
                  }
               }

               if (!handled) {
                  guiRestored = true;
               }
            }

            int page;
            for(page = 0; page < player.inventory.getContainerSize(); ++page) {
               ItemStack st = player.inventory.getItem(page);
               if (!st.isEmpty() && isLeaderboardItem(st)) {
                  player.inventory.removeItemNoUpdate(page);
                  guiRestored = true;
               }
            }

            if (guiRestored) {
               player.inventoryMenu.broadcastChanges();
               if (player.containerMenu != null) {
                  player.containerMenu.broadcastChanges();
               }

               page = (Integer)openPages.getOrDefault(player.getUUID(), 1);
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
   public static void onContainerClose(Close event) {
      openPages.remove(event.getPlayer().getUUID());
   }

   private static boolean isLeaderboardItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.hasTag() && stack.getTag().getBoolean("RaidGuiItem");
      }
   }
}