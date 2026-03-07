package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.server.ServerWorld;
import java.util.*;

public class RaidRewardHandler {

    public static void distributeRewards(RaidSession session, boolean victory, UUID killerId) {
        if (session == null) return;
        DamageTracker tracker = session.getDamageTracker();
        if (tracker == null) return;

        Map<UUID, Integer> damageMap = tracker.getAllDamage();
        if (damageMap == null || damageMap.isEmpty()) return;

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<UUID, Integer> rankMap = new HashMap<>();
        int previousDamage = Integer.MIN_VALUE;
        int assignedRankForPrev = 0;

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, Integer> e = sorted.get(i);
            int dmg = e.getValue();
            int rank;
            if (dmg == previousDamage) {
                rank = assignedRankForPrev;
            } else {
                rank = i + 1;
                assignedRankForPrev = rank;
                previousDamage = dmg;
            }
            rankMap.put(e.getKey(), rank);
        }

        PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
        int difficulty = cfg.getRaidDifficulty();
        int tokenChance = cfg.getTokenDropChance();
        int maxRanks = cfg.getRankCount();
        Random rng = new Random();

        for (Map.Entry<UUID, Integer> entry : sorted) {
            UUID pid = entry.getKey();
            int rank = rankMap.get(pid);
            ServerPlayerEntity player = session.getWorld().getServer().getPlayerList().getPlayer(pid);

            if (player != null) {
                if (victory) {
                    List<String> rewards;
                    int moneyAmt;

                    if (rank <= maxRanks) {
                        rewards = cfg.getRewardsForRank(rank);
                        moneyAmt = cfg.getMoneyForRank(rank);
                    } else {
                        rewards = cfg.getRewardsParticipation();
                        moneyAmt = cfg.getMoneyParticipation();
                    }

                    if (rewards != null) {
                        for (String r : rewards) giveReward(player, r);
                    }

                    giveMoney(player, moneyAmt, cfg);

                    if (killerId != null && pid.equals(killerId)) {
                        for (String k : cfg.getKillShotRewards()) giveReward(player, k);
                        giveMoney(player, cfg.getMoneyKillshot(), cfg);
                        player.sendMessage(new StringTextComponent("§c§l☠ KILLSHOT BONUS! ☠"), Util.NIL_UUID);
                    }

                    if (rng.nextInt(100) < tokenChance) {
                        int amount = cfg.getTokensForDifficulty(difficulty);
                        RaidSaveData.get(session.getWorld()).addTokens(pid, amount);
                        player.sendMessage(new StringTextComponent("§6§l⛃ FOUND TOKENS! §eYou got " + amount + " Raid Tokens!"), Util.NIL_UUID);
                        if(cfg.isSoundEnabled()) {
                            player.level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundCategory.PLAYERS, cfg.getSoundVolume(), 1.0f);
                        }
                    }

                } else {
                    for (String p : cfg.getRewardsParticipation()) giveReward(player, p);
                    giveMoney(player, cfg.getMoneyParticipation(), cfg);
                }
            }
        }
    }

    private static void giveMoney(ServerPlayerEntity player, int amount, PixelmonRaidConfig cfg) {
        if (amount <= 0) return;
        String baseCmd = cfg.getMoneyCommand();
        if (baseCmd == null || baseCmd.isEmpty()) return;

        String cmd = baseCmd.replace("%player%", player.getGameProfile().getName())
                .replace("%amount%", String.valueOf(amount));

        try {
            player.getServer().getCommands().performCommand(
                    player.getServer().createCommandSourceStack(),
                    cmd
            );
            player.sendMessage(new StringTextComponent("§a§l$ §r§aReceived §e$" + amount + " §areward!"), Util.NIL_UUID);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void giveReward(ServerPlayerEntity player, String rewardStr) {
        ItemStack stack = parseReward(rewardStr);
        if (!stack.isEmpty()) {
            if (!player.inventory.add(stack)) {
                player.drop(stack, false);
            }
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
                parts = new String[3];
                parts[0] = metaParts[0];
                parts[1] = metaParts.length > 1 ? metaParts[1] : "1";
                parts[2] = nbt;
            } else {
                parts = rewardStr.split(" ");
            }

            ResourceLocation loc = new ResourceLocation(parts[0]);
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;

            int count = 1;
            if (parts.length >= 2) {
                try { count = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }

            ItemStack stack = new ItemStack(item, count);
            if (parts.length >= 3) {
                String nbtStr = parts[2];
                if (!nbtStr.isEmpty()) {
                    try {
                        CompoundNBT nbt = JsonToNBT.parseTag(nbtStr);
                        stack.setTag(nbt);
                    } catch (Exception ignored) {}
                }
            }
            return stack;
        } catch (Exception e) {
            e.printStackTrace();
            return ItemStack.EMPTY;
        }
    }

    public static void broadcastLeaderboard(RaidSession session) {
        DamageTracker tracker = session.getDamageTracker();
        if (tracker == null) return;

        Map<UUID, Integer> damageMap = tracker.getAllDamage();
        if (damageMap == null || damageMap.isEmpty()) return;
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(damageMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        session.getWorld().getServer().getPlayerList().broadcastMessage(new StringTextComponent("§6§l===== ⚔ RAID RESULTS ⚔ ====="), ChatType.SYSTEM, Util.NIL_UUID);
        int displayRank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (displayRank > 5) break;
            Map.Entry<UUID, Integer> e = sorted.get(i);
            String name = "Unknown";
            ServerPlayerEntity spe = session.getWorld().getServer().getPlayerList().getPlayer(e.getKey());
            if(spe != null) name = spe.getName().getString();
            session.getWorld().getServer().getPlayerList().broadcastMessage(
                    new StringTextComponent(String.format("§e#%d %s §f- §c%d Dmg", displayRank, name, e.getValue())),
                    ChatType.SYSTEM, Util.NIL_UUID
            );
            displayRank++;
        }
    }
}
