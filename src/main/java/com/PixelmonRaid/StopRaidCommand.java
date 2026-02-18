package com.PixelmonRaid;

import com.PixelmonRaid.RaidSpawner;
import com.PixelmonRaid.RaidSession;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class StopRaidCommand {

    public static void register(LiteralArgumentBuilder<CommandSource> dispatcher) {
        dispatcher.then(Commands.literal("stop")
                .requires(source -> source.hasPermission(2))
                .executes(StopRaidCommand::execute));
    }

    private static int execute(CommandContext<CommandSource> context) {
        ServerWorld world = context.getSource().getLevel();
        RaidSession session = RaidSpawner.getSession(world);

        if (session != null) {
            session.cleanup();
            context.getSource().sendSuccess(new StringTextComponent("§cRaid force-stopped."), true);
        }
        return Command.SINGLE_SUCCESS;
    }
}