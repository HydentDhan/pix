package com.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class ResetRaidCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("resetraid").requires((src) -> src.hasPermission(2)).executes((ctx) -> {
         ServerLevel world = ctx.getSource().getLevel();
         RaidSession session = RaidSpawner.getSession(world);
         if (session == null) {
            ctx.getSource().sendFailure(Component.literal("No session found to reset."));
            return 0;
         } else if (session.getState() == RaidSession.State.IN_BATTLE) {
            ctx.getSource().sendFailure(Component.literal("§cCannot reset while a Battle is active! Use /stopraid first."));
            return 0;
         } else {
            session.forceResetTimer();
            ctx.getSource().sendSuccess(() -> Component.literal("§eRaid scheduler reset. Next raid in 5 minutes (or config interval)."), true);
            return 1;
         }
      }));
   }
}