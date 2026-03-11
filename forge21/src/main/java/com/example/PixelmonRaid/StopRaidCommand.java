package com.example.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class StopRaidCommand {

   public static void register(LiteralArgumentBuilder<CommandSourceStack> dispatcher) {
      dispatcher.then(Commands.literal("stop")
              .requires(source -> source.hasPermission(2))
              .executes(StopRaidCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> context) {
      ServerLevel world = context.getSource().getLevel();
      RaidSession session = RaidSpawner.getSession(world);

      if (session != null) {
         session.cleanup();
         context.getSource().sendSuccess(() -> Component.literal("§cRaid force-stopped."), true);
      }
      return Command.SINGLE_SUCCESS;
   }
}