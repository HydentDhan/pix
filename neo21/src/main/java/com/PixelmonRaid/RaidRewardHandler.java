package com.PixelmonRaid;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public class RaidRewardHandler {
   public static void distributeRewards(RaidSession session, boolean victory, UUID killerId) {
      if (session != null) {
         DamageTracker tracker = session.getDamageTracker();
         if (tracker != null) {
            Map<UUID, Integer> damageMap = tracker.getAllDamage();
            if (damageMap != null && !damageMap.isEmpty()) {
               List<Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
               sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
               Map<UUID, Integer> rankMap = new HashMap<>();
               int previousDamage = Integer.MIN_VALUE;
               int assignedRankForPrev = 0;

               int tokenChance;
               for(int i = 0; i < sorted.size(); ++i) {
                  Entry<UUID, Integer> e = sorted.get(i);
                  int dmg = e.getValue();
                  if (dmg == previousDamage) {
                     tokenChance = assignedRankForPrev;
                  } else {
                     tokenChance = i + 1;
                     assignedRankForPrev = tokenChance;
                     previousDamage = dmg;
                  }
                  rankMap.put(e.getKey(), tokenChance);
               }

               PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
               int difficulty = cfg.getRaidDifficulty();
               PixelmonRaidConfig.TierRewardConfig tier = cfg.getTierRewards(difficulty);
               tokenChance = tier.tokenDropChance;
               int maxRanks = tier.getRankCount();
               Random rng = new Random();

               for (Entry<UUID, Integer> entry : sorted) {
                  UUID pid = entry.getKey();
                  int rank = rankMap.get(pid);
                  ServerPlayer player = session.getWorld().getServer().getPlayerList().getPlayer(pid);

                  if (player == null) continue;
                  if (victory) {
                     List<String> itemsToGive = new ArrayList<>();
                     List<String> commandsToRun = new ArrayList<>();
                     if (rank <= maxRanks) {
                        PixelmonRaidConfig.RankReward reward = tier.winners.get("Winner_" + rank);
                        if (reward != null) {
                           itemsToGive = reward.items;
                           commandsToRun = reward.commands;
                        }
                     } else {
                        itemsToGive = tier.participation.items;
                        commandsToRun = tier.participation.commands;
                     }

                     if (itemsToGive != null) {
                        for (String cmd : itemsToGive) {
                           giveReward(player, cmd);
                        }
                     }

                     if (commandsToRun != null) {
                        for (String cmd : commandsToRun) {
                           executeCommand(player, cmd);
                        }
                     }

                     if (killerId != null && pid.equals(killerId)) {
                        if (tier.killshot.items != null) {
                           for (String cmd : tier.killshot.items) {
                              giveReward(player, cmd);
                           }
                        }

                        if (tier.killshot.commands != null) {
                           for (String cmd : tier.killshot.commands) {
                              executeCommand(player, cmd);
                           }
                        }

                        player.sendSystemMessage(Component.literal("§c§l☠ KILLSHOT BONUS! ☠"));
                        try {
                           if (ModList.get().isLoaded("pixelmonbattlepass")) {
                              Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                              Method addKillMethod = bpQuestsClass.getMethod("addRaidKillProgress", ServerPlayer.class);
                              addKillMethod.invoke((Object)null, player);
                           }
                        } catch (Exception var26) {}
                     }

                     if (rng.nextInt(100) < tokenChance) {
                        int amount = tier.minTokens;
                        if (tier.maxTokens > tier.minTokens) {
                           amount += rng.nextInt(tier.maxTokens - tier.minTokens + 1);
                        }

                        RaidSaveData.get(session.getWorld()).addTokens(pid, amount);
                        player.sendSystemMessage(Component.literal("§6§l⛃ FOUND TOKENS! §eYou got " + amount + " Raid Tokens!"));
                        try {
                           if (ModList.get().isLoaded("pixelmonbattlepass")) {
                              Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                              Method addTokenMethod = bpQuestsClass.getMethod("addRaidTokenProgress", ServerPlayer.class, Integer.TYPE);
                              addTokenMethod.invoke((Object)null, player, amount);
                           }
                        } catch (Exception var25) {}

                        if (cfg.isSoundEnabled()) {
                           player.level().playSound((Player)null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, cfg.getSoundVolume(), 1.0F);
                        }
                     }
                  } else {
                     if (tier.participation.items != null) {
                        for (String cmd : tier.participation.items) {
                           giveReward(player, cmd);
                        }
                     }

                     if (tier.participation.commands != null) {
                        for (String cmd : tier.participation.commands) {
                           executeCommand(player, cmd);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void executeCommand(ServerPlayer player, String cmdTemplate) {
      if (cmdTemplate != null && !cmdTemplate.isEmpty()) {
         String cmd = cmdTemplate.replace("%player%", player.getGameProfile().getName());
         try {
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), cmd);
         } catch (Exception var4) {
            var4.printStackTrace();
         }
      }
   }

   private static void giveReward(ServerPlayer player, String rewardStr) {
      ItemStack stack = parseReward(rewardStr);
      if (!stack.isEmpty() && !player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
   }

   private static ItemStack parseReward(String rewardStr) {
      try {
         String[] parts;
         if (rewardStr.contains("{")) {
            int nbtIndex = rewardStr.indexOf("{");
            String meta = rewardStr.substring(0, nbtIndex).trim();
            String nbt = rewardStr.substring(nbtIndex);
            String[] metaParts = meta.split(" ");
            parts = new String[]{metaParts[0], metaParts.length > 1 ? metaParts[1] : "1", nbt};
         } else {
            parts = rewardStr.split(" ");
         }

         ResourceLocation loc = ResourceLocation.parse(parts[0]);
         Item item = BuiltInRegistries.ITEM.get(loc);
         if (item != null && item != Items.AIR) {
            int count = 1;
            if (parts.length >= 2) {
               try {
                  count = Integer.parseInt(parts[1]);
               } catch (NumberFormatException var9) {}
            }

            ItemStack stack = new ItemStack(item, count);
            if (parts.length >= 3) {
               String nbtStr = parts[2];
               if (!nbtStr.isEmpty()) {
                  try {
                     CompoundTag nbt = TagParser.parseTag(nbtStr);
                     GuiItemUtil.setGuiTag(stack, nbt);
                  } catch (Exception var8) {}
               }
            }

            return stack;
         } else {
            return ItemStack.EMPTY;
         }
      } catch (Exception var10) {
         var10.printStackTrace();
         return ItemStack.EMPTY;
      }
   }

   public static void broadcastLeaderboard(RaidSession session) {
      DamageTracker tracker = session.getDamageTracker();
      if (tracker != null) {
         Map<UUID, Integer> damageMap = tracker.getAllDamage();
         if (damageMap != null && !damageMap.isEmpty()) {
            List<Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            session.getWorld().getServer().getPlayerList().broadcastSystemMessage(Component.literal("§6§l===== ⚔ RAID RESULTS ⚔ ====="), false);
            int displayRank = 1;
            for(int i = 0; i < sorted.size() && displayRank <= 5; ++i) {
               Entry<UUID, Integer> e = sorted.get(i);
               String name = "Unknown";
               ServerPlayer spe = session.getWorld().getServer().getPlayerList().getPlayer(e.getKey());
               if (spe != null) {
                  name = spe.getName().getString();
               }

               session.getWorld().getServer().getPlayerList().broadcastSystemMessage(Component.literal(String.format("§e#%d %s §f- §c%d Dmg", displayRank, name, e.getValue())), false);
               ++displayRank;
            }
         }
      }
   }
}