package com.example.PixelmonRaid;

import com.example.PixelmonRaid.RaidSpawner;
import com.example.PixelmonRaid.RaidSession;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class SetRaidSpawnCommand {

    public static void register(LiteralArgumentBuilder<CommandSource> dispatcher) {
        dispatcher.then(Commands.literal("setspawn")
                .requires(source -> source.hasPermission(2))
                .executes(SetRaidSpawnCommand::execute));
    }

    private static int execute(CommandContext<CommandSource> context) {
        ServerWorld world = context.getSource().getLevel();
        BlockPos pos = new BlockPos(context.getSource().getPosition());

        RaidSession session = RaidSpawner.getSession(world);
        if (session != null) {
            session.setPlayerSpawn(pos);
            context.getSource().sendSuccess(new StringTextComponent("§aRaid Player Spawn set to: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        }
        return Command.SINGLE_SUCCESS;
    }
}