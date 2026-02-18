package com.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;

public class ResetRaidCommand {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("resetraid")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerWorld world = ctx.getSource().getLevel();
                    RaidSession session = RaidSpawner.getSession(world);

                    if (session == null) {
                        ctx.getSource().sendFailure(new StringTextComponent("No session found to reset."));
                        return 0;
                    }


                    if (session.getState() == RaidSession.State.IN_BATTLE) {
                        ctx.getSource().sendFailure(new StringTextComponent("§cCannot reset while a Battle is active! Use /stopraid first if you really want to end it."));
                        return 0;
                    }

                    session.forceResetTimer();

                    ctx.getSource().sendSuccess(new StringTextComponent("§eRaid scheduler reset. Next raid in 5 minutes (or config interval)."), true);
                    return 1;
                })
        );
    }
}