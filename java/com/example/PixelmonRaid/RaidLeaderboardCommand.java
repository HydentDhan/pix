package com.example.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

public class RaidLeaderboardCommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .requires(source -> true)
                .then(Commands.literal("leaderboard")
                        .executes(RaidLeaderboardCommand::execute))
        );
        dispatcher.register(Commands.literal("raidstats")
                .requires(source -> true)
                .executes(RaidLeaderboardCommand::execute));
    }

    public static int execute(CommandContext<CommandSource> context) {
        try {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
            openLeaderboard(player, 1);
        } catch (Exception e) {}
        return Command.SINGLE_SUCCESS;
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
                tag.putString("SkullOwner", entry.getValue().lastKnownName);

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

            for (int i = 45; i < 54; i++) chest.setItem(i, new ItemStack(Items.BLACK_STAINED_GLASS_PANE).setHoverName(new StringTextComponent(" ")));
            if (currentPage > 1) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.setHoverName(new StringTextComponent("§e« Previous Page"));
                chest.setItem(45, prev);
            }

            if (currentPage < finalMaxPages) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.setHoverName(new StringTextComponent("§eNext Page »"));
                chest.setItem(53, next);
            }

            ItemStack info = new ItemStack(Items.PAPER);
            info.setHoverName(new StringTextComponent("§bPage " + currentPage + " / " + finalMaxPages));
            chest.setItem(49, info);

            return chest;
        }, new StringTextComponent(PixelmonRaidConfig.getInstance().getUiLeaderboardTitle())));
        RaidLeaderboardUIListener.openPages.put(player.getUUID(), currentPage);
    }
}