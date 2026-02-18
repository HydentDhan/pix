package com.PixelmonRaid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class SetRaidCenterCommand {

    public static void register(LiteralArgumentBuilder<CommandSource> dispatcher) {
        dispatcher.then(Commands.literal("setcenter")
                .requires(source -> source.hasPermission(2))
                .executes(SetRaidCenterCommand::execute));
    }

    private static int execute(CommandContext<CommandSource> context) {
        ServerWorld world = context.getSource().getLevel();
        BlockPos pos = new BlockPos(context.getSource().getPosition());

        RaidSession session = RaidSpawner.getSession(world);
        if (session != null) {
            session.setCenter(pos);
            context.getSource().sendSuccess(new StringTextComponent("§aRaid Center set to: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        }
        return Command.SINGLE_SUCCESS;
    }
}