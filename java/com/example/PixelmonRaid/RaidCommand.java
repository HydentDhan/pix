package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.Util;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.particles.ParticleTypes;

public class RaidCommand {

    private static final int JOIN_RADIUS = 30;

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .requires(source -> true)

                .then(Commands.literal("warp")
                        .requires(source -> true)
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                            ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
                            ServerWorld world = (ServerWorld) player.level;

                            RaidSession session = RaidSpawner.getSessionSafe(world);
                            if (session == null) {
                                ctx.getSource().sendFailure(new StringTextComponent("§cNo active raid to warp to."));
                                return 0;
                            }

                            BlockPos warpPos = session.getPlayerSpawn();
                            if (warpPos == null || (warpPos.getX() == 0 && warpPos.getY() == 0 && warpPos.getZ() == 0)) {
                                ctx.getSource().sendFailure(new StringTextComponent("§cWarp location has not been set by admins!"));
                                return 0;
                            }

                            player.teleportTo(world, warpPos.getX() + 0.5, warpPos.getY(), warpPos.getZ() + 0.5, player.yRot, player.xRot);
                            ctx.getSource().sendSuccess(new StringTextComponent("§aWoosh! Teleported to the Raid Arena."), false);
                            return 1;
                        })
                )

                .then(Commands.literal("join")
                        .requires(source -> true)
                        .executes(context -> {
                            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                                ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
                                RaidSession session = RaidSpawner.getSessionSafe((ServerWorld) player.level);

                                if (session != null && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH)) {
                                    BlockPos center = session.getCenter();

                                    if (center != null && player.blockPosition().distSqr(center) > (JOIN_RADIUS * JOIN_RADIUS)) {
                                        player.sendMessage(new StringTextComponent("§c§l❌ TOO FAR! ❌"), Util.NIL_UUID);
                                        player.sendMessage(new StringTextComponent("§7You must be within §e" + JOIN_RADIUS + " blocks §7of the Boss to join!"), Util.NIL_UUID);

                                        ServerWorld world = (ServerWorld) player.level;
                                        for (int i = 0; i < 360; i += 10) {
                                            double angle = Math.toRadians(i);
                                            double x = center.getX() + 0.5 + Math.cos(angle) * JOIN_RADIUS;
                                            double z = center.getZ() + 0.5 + Math.sin(angle) * JOIN_RADIUS;
                                            world.sendParticles(ParticleTypes.FLAME, x, center.getY() + 1, z, 1, 0, 0, 0, 0);
                                        }
                                        return 0;
                                    }
                                    session.startPlayerBattleRequest(player);
                                } else {
                                    player.sendMessage(new StringTextComponent("§c§l❌ NO ACTIVE RAID ❌"), Util.NIL_UUID);
                                }
                            }
                            return 1;
                        })
                )

                .then(Commands.literal("shop")
                        .requires(source -> true)
                        .executes(context -> {
                            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                                RaidAdminCommand.openTokenShop((ServerPlayerEntity) context.getSource().getEntity());
                            }
                            return 1;
                        })
                )

                .then(Commands.literal("status")
                        .requires(source -> true)
                        .executes(context -> {
                            ServerWorld world = context.getSource().getLevel();
                            RaidSession session = RaidSpawner.getSessionSafe(world);

                            if (session == null) {
                                context.getSource().sendFailure(new StringTextComponent("§cNo active raid session found."));
                            } else {
                                String timeMsg = "§7Unknown";
                                long currentTick = world.getGameTime();

                                String displayBossName = session.getCurrentBossName();
                                boolean isMystery = false;

                                if (session.getState() == RaidSession.State.IDLE) {
                                    isMystery = true;
                                    if (!session.isAutoRaidEnabled()) {
                                        timeMsg = "§cPAUSED (Auto-Raid Disabled)";
                                    } else {
                                        long nextRaid = RaidSaveData.get(world).getNextRaidTick();
                                        long diff = Math.max(0, nextRaid - currentTick);
                                        timeMsg = "§bNext Raid In: §f" + formatTime(diff);
                                    }
                                } else if (session.getState() == RaidSession.State.IN_BATTLE) {
                                    timeMsg = "§c⚔ BATTLE ACTIVE ⚔";
                                } else if (session.getState() == RaidSession.State.SUDDEN_DEATH) {
                                    timeMsg = "§4☠ SUDDEN DEATH ☠";
                                }

                                if (isMystery) displayBossName = "§k???§r §7(Mystery)§r";

                                int currentHP = session.getTotalRaidHP();
                                int maxHP = Math.max(1, session.getMaxRaidHP());
                                float pct = (float)currentHP / (float)maxHP * 100f;

                                String hpDisplay = isMystery ? "§7???" : "§d" + currentHP + " §7/ §d" + maxHP + " §8(§b" + String.format("%.1f%%", pct) + "§8)";

                                String msg = "\n" +
                                        "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                                        "       §5§l⚔ PIXELMON RAID STATUS ⚔       \n" +
                                        "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                                        " §e📡 Current Phase:  §f" + session.getState() + "\n" +
                                        " §e☠ Target Boss:    §c§l" + displayBossName + "\n" +
                                        " §e❤ Boss Vitality:  " + hpDisplay + "\n" +
                                        " §e⏱ Timer Info:     " + timeMsg + "\n" +
                                        " §e🗡 Challengers:    §a" + session.getPlayers().size() + " Active\n" +
                                        "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
                                context.getSource().sendSuccess(new StringTextComponent(msg), false);
                            }
                            return 1;
                        })
                )

                .then(Commands.literal("tokens")
                        .then(Commands.literal("balance")
                                .requires(source -> true)
                                .executes(ctx -> {
                                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
                                        ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
                                        int bal = RaidSaveData.get((ServerWorld)player.level).getTokens(player.getUUID());
                                        player.sendMessage(new StringTextComponent("§6§l[Raid] §eYou have §6" + bal + " §eTokens."), player.getUUID());
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    private static String formatTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}