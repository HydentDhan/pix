package com.example.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class StartRaidCommand {

   public static void register(LiteralArgumentBuilder<CommandSourceStack> dispatcher) {
      dispatcher.then(Commands.literal("start")
              .requires(source -> source.hasPermission(2))
              .executes(StartRaidCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> context) {
      ServerLevel world = context.getSource().getLevel();
      RaidSession session = RaidSpawner.getSession(world);

      if (session != null) {
         if (session.getState() == RaidSession.State.IDLE) {
            session.startWaitingNow(world.getGameTime());
            context.getSource().sendSuccess(() -> Component.literal("§aRaid Sequence Started!"), true);
         } else {
            context.getSource().sendFailure(Component.literal("§cA raid is already active or waiting!"));
         }
      }
      return Command.SINGLE_SUCCESS;
   }
}