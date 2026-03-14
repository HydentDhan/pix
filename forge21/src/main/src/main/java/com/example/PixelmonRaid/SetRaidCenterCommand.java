package com.example.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class SetRaidCenterCommand {

   public static void register(LiteralArgumentBuilder<CommandSourceStack> dispatcher) {
      dispatcher.then(Commands.literal("setcenter")
              .requires(source -> source.hasPermission(2))
              .executes(SetRaidCenterCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> context) {
      ServerLevel world = context.getSource().getLevel();
      BlockPos pos = BlockPos.containing(context.getSource().getPosition());

      RaidSession session = RaidSpawner.getSession(world);
      if (session != null) {
         session.setCenter(pos);
         context.getSource().sendSuccess(() -> Component.literal("§aRaid Center set to: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
      }
      return Command.SINGLE_SUCCESS;
   }
}