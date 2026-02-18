package com.example.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

public class RaidLeaderboardCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .requires(source -> true)
                .then(Commands.literal("leaderboard")
                        .executes(RaidLeaderboardCommand::executeLastRaid))
        );
        dispatcher.register(Commands.literal("raidstats")
                .requires(source -> true)
                .executes(RaidLeaderboardCommand::executeAllTime));
    }

    public static int executeAllTime(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
            openLeaderboard(player, 1);
        } catch (Exception e) {}
        return Command.SINGLE_SUCCESS;
    }

    public static int executeLastRaid(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
            openLastRaidLeaderboard(player);
        } catch (Exception e) {}
        return Command.SINGLE_SUCCESS;
    }

    public static void openLastRaidLeaderboard(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.level;
        RaidSession session = RaidSpawner.getSessionSafe(world);
        RaidSaveData data = RaidSaveData.get(world);

        boolean isActive = session != null && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH);

        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.threeRows(id, playerInv);

            for (int i = 0; i < 27; i++) {
                ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                pane.setHoverName(new StringTextComponent(" "));
                pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
                chest.setItem(i, pane);
            }

            if (isActive) {
                Map<UUID, Integer> damages = session.getDamageTracker().getAllDamage();
                List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(damages.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                int slot = 11;
                for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                    Map.Entry<UUID, Integer> entry = sorted.get(i);
                    ServerPlayerEntity sp = world.getServer().getPlayerList().getPlayer(entry.getKey());
                    String name = (sp != null) ? sp.getGameProfile().getName() : "Unknown";
                    int dmg = entry.getValue();
                    chest.setItem(slot, createHead(entry.getKey(), name, i + 1, dmg));
                    slot++;
                }
            } else {
                List<String> lastLb = data.getLastLeaderboard();
                int slot = 11;
                for (int i = 0; i < Math.min(5, lastLb.size()); i++) {
                    String entry = lastLb.get(i);
                    String[] parts = entry.split(":");
                    if (parts.length >= 2) {
                        String name = parts[0];
                        int dmg = 0;
                        try { dmg = Integer.parseInt(parts[1]); } catch (Exception e) {}

                        UUID playerId = null;
                        for (Map.Entry<UUID, RaidSaveData.RaidPlayerStats> stat : data.getSortedAllTimeLeaderboard()) {
                            if (stat.getValue().lastKnownName.equals(name)) {
                                playerId = stat.getKey();
                                break;
                            }
                        }

                        chest.setItem(slot, createHead(playerId, name, i + 1, dmg));
                        slot++;
                    }
                }
            }

            ItemStack info = new ItemStack(Items.PAPER);
            info.setHoverName(new StringTextComponent(isActive ? "§a§lCurrent Raid Leaderboard" : "§7§lLast Raid Leaderboard"));
            info.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.setItem(4, info);

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

        String color = (rank == 1) ? "§6§l" : (rank == 2 ? "§e§l" : (rank == 3 ? "§b§l" : "§f§l"));
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
        ServerWorld world = (ServerWorld) player.level;
        RaidSaveData data = RaidSaveData.get(world);
        List<Map.Entry<UUID, RaidSaveData.RaidPlayerStats>> allStats = data.getSortedAllTimeLeaderboard();

        int maxPages = (int) Math.ceil((double) allStats.size() / 45.0);
        if (maxPages == 0) maxPages = 1;
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        final int currentPage = page;
        final int finalMaxPages = maxPages;

        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);

            int start = (currentPage - 1) * 45;
            int end = Math.min(start + 45, allStats.size());

            for (int i = start; i < end; i++) {
                Map.Entry<UUID, RaidSaveData.RaidPlayerStats> entry = allStats.get(i);
                int rank = i + 1;
                ItemStack head = new ItemStack(Items.PLAYER_HEAD);

                CompoundNBT tag = head.getOrCreateTag();

                CompoundNBT skullOwner = new CompoundNBT();
                skullOwner.putUUID("Id", entry.getKey());
                skullOwner.putString("Name", entry.getValue().lastKnownName);
                tag.put("SkullOwner", skullOwner);
                tag.putBoolean("RaidGuiItem", true);

                String color = (rank == 1) ? "§6§l" : (rank == 2 ? "§e§l" : (rank == 3 ? "§b§l" : "§f§l"));
                String title = color + "#" + rank + " " + entry.getValue().lastKnownName;

                CompoundNBT display = new CompoundNBT();
                display.putString("Name", "{\"text\":\"" + title + "\"}");
                ListNBT lore = new ListNBT();
                lore.add(StringNBT.valueOf("{\"text\":\"§7Total Damage: §c" + String.format("%,d", entry.getValue().totalDamage) + "\"}"));
                lore.add(StringNBT.valueOf("{\"text\":\"§7Total Kills: §e" + entry.getValue().kills + "\"}"));
                lore.add(StringNBT.valueOf("{\"text\":\"§7Raids Joined: §b" + entry.getValue().raidsJoined + "\"}"));

                display.put("Lore", lore);
                tag.put("display", display);

                chest.setItem(i - start, head);
            }

            int filledSlots = end - start;
            for (int i = filledSlots; i < 45; i++) {
                ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                pane.setHoverName(new StringTextComponent(" "));
                pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
                chest.setItem(i, pane);
            }

            for (int i = 45; i < 54; i++) {
                ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                pane.setHoverName(new StringTextComponent(" "));
                pane.getOrCreateTag().putBoolean("RaidGuiItem", true);
                chest.setItem(i, pane);
            }

            if (currentPage > 1) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.setHoverName(new StringTextComponent("§e« Previous Page"));
                prev.getOrCreateTag().putBoolean("RaidGuiItem", true);
                chest.setItem(45, prev);
            }

            if (currentPage < finalMaxPages) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.setHoverName(new StringTextComponent("§eNext Page »"));
                next.getOrCreateTag().putBoolean("RaidGuiItem", true);
                chest.setItem(53, next);
            }

            ItemStack info = new ItemStack(Items.PAPER);
            info.setHoverName(new StringTextComponent("§bPage " + currentPage + " / " + finalMaxPages));
            info.getOrCreateTag().putBoolean("RaidGuiItem", true);
            chest.setItem(49, info);

            return chest;
        }, new StringTextComponent(PixelmonRaidConfig.getInstance().getUiLeaderboardTitle())));
        RaidLeaderboardUIListener.openPages.put(player.getUUID(), currentPage);
    }
}
