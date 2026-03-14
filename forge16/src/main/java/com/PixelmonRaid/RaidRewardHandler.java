package com.PixelmonRaid;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class RaidRewardHandler {
   public static void distributeRewards(RaidSession session, boolean victory, UUID killerId) {
      if (session != null) {
         DamageTracker tracker = session.getDamageTracker();
         if (tracker != null) {
            Map<UUID, Integer> damageMap = tracker.getAllDamage();
            if (damageMap != null && !damageMap.isEmpty()) {
               List<Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
               sorted.sort((a, b) -> {
                  return Integer.compare(b.getValue(), a.getValue());
               });
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

               Iterator<Entry<UUID, Integer>> var15 = sorted.iterator();
               while(var15.hasNext()) {
                  Entry<UUID, Integer> entry = var15.next();
                  UUID pid = entry.getKey();
                  int rank = rankMap.get(pid);
                  ServerPlayerEntity player = session.getWorld().getServer().getPlayerList().getPlayer(pid);

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

                     String cmd;
                     Iterator<String> var32;
                     if (itemsToGive != null) {
                        var32 = itemsToGive.iterator();
                        while(var32.hasNext()) {
                           cmd = var32.next();
                           giveReward(player, cmd);
                        }
                     }

                     if (commandsToRun != null) {
                        var32 = commandsToRun.iterator();
                        while(var32.hasNext()) {
                           cmd = var32.next();
                           executeCommand(player, cmd);
                        }
                     }

                     if (killerId != null && pid.equals(killerId)) {
                        if (tier.killshot.items != null) {
                           var32 = tier.killshot.items.iterator();
                           while(var32.hasNext()) {
                              cmd = var32.next();
                              giveReward(player, cmd);
                           }
                        }

                        if (tier.killshot.commands != null) {
                           var32 = tier.killshot.commands.iterator();
                           while(var32.hasNext()) {
                              cmd = var32.next();
                              executeCommand(player, cmd);
                           }
                        }

                        player.sendMessage(new StringTextComponent("§c§l☠ KILLSHOT BONUS! ☠"), Util.NIL_UUID);
                        try {
                           if (ModList.get().isLoaded("pixelmonbattlepass")) {
                              Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                              Method addKillMethod = bpQuestsClass.getMethod("addRaidKillProgress", ServerPlayerEntity.class);
                              addKillMethod.invoke((Object)null, player);
                           }
                        } catch (Exception var26) {
                        }
                     }

                     if (rng.nextInt(100) < tokenChance) {
                        int amount = tier.minTokens;
                        if (tier.maxTokens > tier.minTokens) {
                           amount += rng.nextInt(tier.maxTokens - tier.minTokens + 1);
                        }

                        RaidSaveData.get(session.getWorld()).addTokens(pid, amount);
                        player.sendMessage(new StringTextComponent("§6§l⛃ FOUND TOKENS! §eYou got " + amount + " Raid Tokens!"), Util.NIL_UUID);
                        try {
                           if (ModList.get().isLoaded("pixelmonbattlepass")) {
                              Class<?> bpQuestsClass = Class.forName("com.pixel.pixelmonbattlepass.BattlepassQuests");
                              Method addTokenMethod = bpQuestsClass.getMethod("addRaidTokenProgress", ServerPlayerEntity.class, Integer.TYPE);
                              addTokenMethod.invoke((Object)null, player, amount);
                           }
                        } catch (Exception var25) {
                        }

                        if (cfg.isSoundEnabled()) {
                           player.level.playSound((PlayerEntity)null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundCategory.PLAYERS, cfg.getSoundVolume(), 1.0F);
                        }
                     }
                  } else {
                     Iterator<String> var20;
                     String cmd;
                     if (tier.participation.items != null) {
                        var20 = tier.participation.items.iterator();
                        while(var20.hasNext()) {
                           cmd = var20.next();
                           giveReward(player, cmd);
                        }
                     }

                     if (tier.participation.commands != null) {
                        var20 = tier.participation.commands.iterator();
                        while(var20.hasNext()) {
                           cmd = var20.next();
                           executeCommand(player, cmd);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void executeCommand(ServerPlayerEntity player, String cmdTemplate) {
      if (cmdTemplate != null && !cmdTemplate.isEmpty()) {
         String cmd = cmdTemplate.replace("%player%", player.getGameProfile().getName());
         try {
            player.getServer().getCommands().performCommand(player.getServer().createCommandSourceStack(), cmd);
         } catch (Exception var4) {
            var4.printStackTrace();
         }
      }
   }

   private static void giveReward(ServerPlayerEntity player, String rewardStr) {
      ItemStack stack = parseReward(rewardStr);
      if (!stack.isEmpty() && !player.inventory.add(stack)) {
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

         ResourceLocation loc = new ResourceLocation(parts[0]);
         Item item = ForgeRegistries.ITEMS.getValue(loc);
         if (item != null && item != Items.AIR) {
            int count = 1;
            if (parts.length >= 2) {
               try {
                  count = Integer.parseInt(parts[1]);
               } catch (NumberFormatException var9) {
               }
            }

            ItemStack stack = new ItemStack(item, count);
            if (parts.length >= 3) {
               String nbtStr = parts[2];
               if (!nbtStr.isEmpty()) {
                  try {
                     CompoundNBT nbt = JsonToNBT.parseTag(nbtStr);
                     stack.setTag(nbt);
                  } catch (Exception var8) {
                  }
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
            sorted.sort((a, b) -> {
               return Integer.compare(b.getValue(), a.getValue());
            });
            session.getWorld().getServer().getPlayerList().broadcastMessage(new StringTextComponent("§6§l===== ⚔ RAID RESULTS ⚔ ====="), ChatType.SYSTEM, Util.NIL_UUID);
            int displayRank = 1;

            for(int i = 0; i < sorted.size() && displayRank <= 5; ++i) {
               Entry<UUID, Integer> e = sorted.get(i);
               String name = "Unknown";
               ServerPlayerEntity spe = session.getWorld().getServer().getPlayerList().getPlayer(e.getKey());
               if (spe != null) {
                  name = spe.getName().getString();
               }

               session.getWorld().getServer().getPlayerList().broadcastMessage(new StringTextComponent(String.format("§e#%d %s §f- §c%d Dmg", displayRank, name, e.getValue())), ChatType.SYSTEM, Util.NIL_UUID);
               ++displayRank;
            }

         }
      }
   }
}