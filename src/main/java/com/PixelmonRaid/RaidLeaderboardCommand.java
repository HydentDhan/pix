package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class RaidLeaderboardCommand {
   public static void register(CommandDispatcher<CommandSource> dispatcher) {
      dispatcher.register(Commands.literal("raid").requires((source) -> {
         return true;
      }).then(Commands.literal("leaderboard").executes(RaidLeaderboardCommand::executeLastRaid)));
      dispatcher.register(Commands.literal("raidstats").requires((source) -> {
         return true;
      }).executes(RaidLeaderboardCommand::executeAllTime));
   }

   public static int executeAllTime(CommandContext<CommandSource> context) {
      try {
         ServerPlayerEntity player = (ServerPlayerEntity)((CommandSource)context.getSource()).getEntity();
         openLeaderboard(player, 1);
      } catch (Exception var2) {
      }

      return 1;
   }

   public static int executeLastRaid(CommandContext<CommandSource> context) {
      try {
         ServerPlayerEntity player = (ServerPlayerEntity)((CommandSource)context.getSource()).getEntity();
         openLastRaidLeaderboard(player);
      } catch (Exception var2) {
      }

      return 1;
   }

   public static void openLastRaidLeaderboard(ServerPlayerEntity player) {
      ServerWorld world = (ServerWorld)player.level;
      RaidSession session = RaidSpawner.getSessionSafe(world);
      RaidSaveData data = RaidSaveData.get(world);
      boolean isActive = session != null && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH);

      player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
         ChestContainer chest = ChestContainer.threeRows(id, playerInv);

         for(int i = 0; i < 27; ++i) {
            ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            pane.setHoverName(new StringTextComponent(" "));
            pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.getSlot(i).set(pane);
         }

         int ix;
         if (isActive) {
            Map<UUID, Integer> damages = session.getDamageTracker().getAllDamage();
            List<Entry<UUID, Integer>> sorted = new ArrayList<>(damages.entrySet());
            sorted.sort((a, b) -> {
               return Integer.compare(b.getValue(), a.getValue());
            });

            ix = 11;

            for(int ixx = 0; ixx < Math.min(5, sorted.size()); ++ixx) {
               Entry<UUID, Integer> entryx = sorted.get(ixx);
               ServerPlayerEntity sp = world.getServer().getPlayerList().getPlayer(entryx.getKey());
               String namex = sp != null ? sp.getGameProfile().getName() : "Unknown";
               int dmgx = entryx.getValue();
               chest.getSlot(ix).set(createHead(entryx.getKey(), namex, ixx + 1, dmgx));
               ++ix;
            }
         } else {
            List<String> lastLb = data.getLastLeaderboard();
            int slot = 11;

            for(ix = 0; ix < Math.min(5, lastLb.size()); ++ix) {
               String entry = lastLb.get(ix);
               String[] parts = entry.split(":");
               if (parts.length >= 2) {
                  String name = parts[0];
                  int dmg = 0;

                  try {
                     dmg = Integer.parseInt(parts[1]);
                  } catch (Exception var18) {
                  }

                  UUID playerId = null;
                  Iterator<Entry<UUID, RaidSaveData.RaidPlayerStats>> var16 = data.getSortedAllTimeLeaderboard().iterator();

                  while(var16.hasNext()) {
                     Entry<UUID, RaidSaveData.RaidPlayerStats> stat = var16.next();
                     if (stat.getValue().lastKnownName.equals(name)) {
                        playerId = stat.getKey();
                        break;
                     }
                  }

                  chest.getSlot(slot).set(createHead(playerId, name, ix + 1, dmg));
                  ++slot;
               }
            }
         }

         ItemStack info = new ItemStack(Items.PAPER);
         info.setHoverName(new StringTextComponent(isActive ? "§a§lCurrent Raid Leaderboard" : "§7§lLast Raid Leaderboard"));
         info.getOrCreateTag().putBoolean("RaidGuiItem", true);
         chest.getSlot(4).set(info);
         return chest;
      }, new StringTextComponent(isActive ? PixelmonRaidConfig.getInstance().getUiCurrentLeaderboardTitle() : PixelmonRaidConfig.getInstance().getUiLastLeaderboardTitle())));

      RaidLeaderboardUIListener.openPages.put(player.getUUID(), 0);
   }

   private static ItemStack createHead(UUID id, String name, int rank, int damage) {
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      CompoundNBT tag = head.getOrCreateTag();
      CompoundNBT skullOwner = new CompoundNBT();
      if (id != null) {
         skullOwner.putUUID("Id", id);
      } else {
         skullOwner.putUUID("Id", UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
      }

      skullOwner.putString("Name", name);
      tag.put("SkullOwner", skullOwner);
      tag.putBoolean("RaidGuiItem", true);
      String color = rank == 1 ? "§6§l" : (rank == 2 ? "§e§l" : (rank == 3 ? "§b§l" : "§f§l"));
      String title = color + "#" + rank + " " + name;
      CompoundNBT display = new CompoundNBT();
      display.putString("Name", "{\"text\":\"" + title + "\"}");
      ListNBT lore = new ListNBT();
      lore.add(StringNBT.valueOf("{\"text\":\"§7Damage Dealt: §c" + String.format("%,d", damage) + "\"}"));
      display.put("Lore", lore);
      tag.put("display", display);
      return head;
   }

   public static void openLeaderboard(ServerPlayerEntity player, int page) {
      ServerWorld world = (ServerWorld)player.level;
      RaidSaveData data = RaidSaveData.get(world);
      List<Entry<UUID, RaidSaveData.RaidPlayerStats>> allStats = data.getSortedAllTimeLeaderboard();
      int maxPages = (int)Math.ceil((double)allStats.size() / 45.0D);

      if (maxPages == 0) {
         maxPages = 1;
      }

      if (page < 1) {
         page = 1;
      }

      if (page > maxPages) {
         page = maxPages;
      }

      // Snapshot the variables so Java's lambda expression will accept them
      final int finalPage = page;
      final int finalMaxPages = maxPages;

      player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
         ChestContainer chest = ChestContainer.sixRows(id, playerInv);
         int start = (finalPage - 1) * 45;
         int end = Math.min(start + 45, allStats.size());

         int filledSlots;
         for(filledSlots = start; filledSlots < end; ++filledSlots) {
            Entry<UUID, RaidSaveData.RaidPlayerStats> entry = allStats.get(filledSlots);
            int rank = filledSlots + 1;
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            CompoundNBT tag = head.getOrCreateTag();
            CompoundNBT skullOwner = new CompoundNBT();
            skullOwner.putUUID("Id", entry.getKey());
            skullOwner.putString("Name", entry.getValue().lastKnownName);
            tag.put("SkullOwner", skullOwner);
            tag.putBoolean("RaidGuiItem", true);
            String color = rank == 1 ? "§6§l" : (rank == 2 ? "§e§l" : (rank == 3 ? "§b§l" : "§f§l"));
            String title = color + "#" + rank + " " + entry.getValue().lastKnownName;
            CompoundNBT display = new CompoundNBT();
            display.putString("Name", "{\"text\":\"" + title + "\"}");
            ListNBT lore = new ListNBT();
            lore.add(StringNBT.valueOf("{\"text\":\"§7Total Damage: §c" + String.format("%,d", entry.getValue().totalDamage) + "\"}"));
            lore.add(StringNBT.valueOf("{\"text\":\"§7Total Kills: §e" + entry.getValue().kills + "\"}"));
            lore.add(StringNBT.valueOf("{\"text\":\"§7Raids Joined: §b" + entry.getValue().raidsJoined + "\"}"));
            display.put("Lore", lore);
            tag.put("display", display);
            chest.getSlot(filledSlots - start).set(head);
         }

         filledSlots = end - start;

         int i;
         ItemStack pane;
         for(i = filledSlots; i < 45; ++i) {
            pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
            pane.setHoverName(new StringTextComponent(" "));
            pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.getSlot(i).set(pane);
         }

         for(i = 45; i < 54; ++i) {
            pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            pane.setHoverName(new StringTextComponent(" "));
            pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.getSlot(i).set(pane);
         }

         ItemStack info;
         if (finalPage > 1) {
            info = new ItemStack(Items.ARROW);
            info.setHoverName(new StringTextComponent("§e« Previous Page"));
            info.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.getSlot(45).set(info);
         }

         if (finalPage < finalMaxPages) {
            info = new ItemStack(Items.ARROW);
            info.setHoverName(new StringTextComponent("§eNext Page »"));
            info.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.getSlot(53).set(info);
         }

         info = new ItemStack(Items.PAPER);
         info.setHoverName(new StringTextComponent("§bPage " + finalPage + " / " + finalMaxPages));
         info.getOrCreateTag().putBoolean("RaidGuiItem", true);
         chest.getSlot(49).set(info);
         return chest;
      }, new StringTextComponent(PixelmonRaidConfig.getInstance().getUiLeaderboardTitle())));

      RaidLeaderboardUIListener.openPages.put(player.getUUID(), page);
   }
}