package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class RaidCommand {
   private static final int JOIN_RADIUS = 30;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("raid").requires((source) -> {
         return true;
      }).then(Commands.literal("warp").requires((source) -> {
         return true;
      }).executes((ctx) -> {
         if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            return 0;
         } else {
            ServerPlayer player = (ServerPlayer)ctx.getSource().getEntity();
            ServerLevel world = (ServerLevel)player.level();
            RaidSession session = RaidSpawner.getSessionSafe(world);
            if (session == null) {
               ctx.getSource().sendFailure(Component.literal("§cNo active raid to warp to."));
               return 0;
            } else {
               BlockPos warpPos = session.getPlayerSpawn();
               if (warpPos != null && (warpPos.getX() != 0 || warpPos.getY() != 0 || warpPos.getZ() != 0)) {
                  player.teleportTo(world, (double)warpPos.getX() + 0.5D, (double)warpPos.getY(), (double)warpPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
                  ctx.getSource().sendSystemMessage(Component.literal("§aWoosh! Teleported to the Raid Arena."));
                  return 1;
               } else {
                  ctx.getSource().sendFailure(Component.literal("§cWarp location has not been set by admins!"));
                  return 0;
               }
            }
         }
      })).then(Commands.literal("join").requires((source) -> {
         return true;
      }).executes((context) -> {
         if (context.getSource().getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)context.getSource().getEntity();
            RaidSession session = RaidSpawner.getSessionSafe((ServerLevel)player.level());

            if (session == null || session.getState() != RaidSession.State.IN_BATTLE && session.getState() != RaidSession.State.SUDDEN_DEATH) {
               player.sendSystemMessage(Component.literal("§c§l✖ NO ACTIVE RAID ✖"));
            } else {
               BlockPos center = session.getCenter();
               if (center != null && player.blockPosition().distSqr(center) > 900.0D) {
                  player.sendSystemMessage(Component.literal("§c§l✖ TOO FAR! ✖"));
                  player.sendSystemMessage(Component.literal("§7You must be within §e30 blocks §7of the Boss to join!"));
                  ServerLevel world = (ServerLevel)player.level();

                  for(int i = 0; i < 360; i += 10) {
                     double angle = Math.toRadians((double)i);
                     double x = (double)center.getX() + 0.5D + Math.cos(angle) * 30.0D;
                     double z = (double)center.getZ() + 0.5D + Math.sin(angle) * 30.0D;
                     world.sendParticles(ParticleTypes.END_ROD, x, (double)(center.getY() + 1), z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                  }
                  return 0;
               }
               session.startPlayerBattleRequest(player);
            }
         }
         return 1;
      })).then(Commands.literal("shop").requires((source) -> {
         return PixelmonRaidConfig.getInstance().isInternalShopEnabled();
      }).executes((context) -> {
         if (context.getSource().getEntity() instanceof ServerPlayer) {
            RaidAdminCommand.openTokenShop((ServerPlayer)context.getSource().getEntity());
         }
         return 1;
      })).then(Commands.literal("status").requires((source) -> {
         return true;
      }).executes((context) -> {
         ServerLevel world = context.getSource().getLevel();
         RaidSession session = RaidSpawner.getSessionSafe(world);

         if (session == null) {
            context.getSource().sendFailure(Component.literal("§cNo active raid session found."));
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
                  long diff = Math.max(0L, nextRaid - currentTick);
                  timeMsg = "§bNext Raid In: §f" + formatTime(diff);
               }
            } else if (session.getState() == RaidSession.State.IN_BATTLE) {
               timeMsg = "§c⚔ BATTLE ACTIVE ⚔";
            } else if (session.getState() == RaidSession.State.SUDDEN_DEATH) {
               timeMsg = "§4☠ SUDDEN DEATH ☠";
            }

            if (isMystery) {
               displayBossName = "§k???§r §7(Mystery)§r";
            }

            int currentHP = session.getTotalRaidHP();
            int maxHP = Math.max(1, session.getMaxRaidHP());
            float pct = (float)currentHP / (float)maxHP * 100.0F;
            String hpDisplay = isMystery ? "§7???" : "§d" + currentHP + " §7/ §d" + maxHP + " §8(§b" + String.format("%.1f%%", pct) + "§8)";
            String msg = "\n§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n       §5§l⚔ PIXELMON RAID STATUS ⚔    \n§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n §e► Current Phase:  §f" + session.getState() + "\n §e☠ Target Boss:    §c§l" + displayBossName + "\n §e❤ Boss Vitality:  " + hpDisplay + "\n §e⌛ Timer Info:     " + timeMsg + "\n §e❖ Challengers:    §a" + session.getPlayers().size() + " Active\n§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
            context.getSource().sendSystemMessage(Component.literal(msg));
         }
         return 1;
      })).then(Commands.literal("tokens").then(Commands.literal("balance").requires((source) -> {
         return true;
      }).executes((ctx) -> {
         if (ctx.getSource().getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)ctx.getSource().getEntity();
            int bal = RaidSaveData.get((ServerLevel)player.level()).getTokens(player.getUUID());
            player.sendSystemMessage(Component.literal("§6§l[Raid] §eYou have §6" + bal + " §eTokens."));
         }
         return 1;
      }))));
   }

   private static String formatTime(long ticks) {
      long totalSeconds = ticks / 20L;
      if (totalSeconds < 60L) {
         return totalSeconds + "s";
      } else {
         long minutes = totalSeconds / 60L;
         long hours = minutes / 60L;
         long days = hours / 24L;
         minutes %= 60L;
         hours %= 24L;
         StringBuilder timeStr = new StringBuilder();
         if (days > 0L) {
            timeStr.append(days).append("d ");
         }
         if (hours > 0L) {
            timeStr.append(hours).append("h ");
         }
         if (minutes > 0L) {
            timeStr.append(minutes).append("m");
         }
         return timeStr.toString().trim();
      }
   }
}