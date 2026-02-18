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

public class StartRaidCommand {

    public static void register(LiteralArgumentBuilder<CommandSource> dispatcher) {
        dispatcher.then(Commands.literal("start")
                .requires(source -> source.hasPermission(2))
                .executes(StartRaidCommand::execute));
    }

    private static int execute(CommandContext<CommandSource> context) {
        ServerWorld world = context.getSource().getLevel();
        RaidSession session = RaidSpawner.getSession(world);

        if (session != null) {
            if (session.getState() == RaidSession.State.IDLE) {
                session.startWaitingNow(world.getGameTime());
                context.getSource().sendSuccess(new StringTextComponent("§aRaid Sequence Started!"), true);
            } else {
                context.getSource().sendFailure(new StringTextComponent("§cA raid is already active or waiting!"));
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}