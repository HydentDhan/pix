package com.PixelmonRaid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber
public class RaidZombieKiller {

   @SubscribeEvent
   public static void onEntityJoin(EntityJoinLevelEvent event) {
      if (event.getLevel().isClientSide()) return;
      Entity entity = event.getEntity();

      if (entity.getPersistentData().contains("pixelmonraid_boss")) {
         if (event.getLevel() instanceof ServerLevel world) {
            RaidSession session = RaidSpawner.getSessionSafe(world);

            if (session == null || !session.isRaidEntity(entity.getUUID())) {
               event.setCanceled(true);
               entity.remove(Entity.RemovalReason.DISCARDED);
            }
         }
      }

      if (entity instanceof ArmorStand) {
         boolean isHolo = entity.getPersistentData().getBoolean("pixelmonraid_hologram");
         boolean isLegacyHolo = entity.getCustomName() != null && entity.getCustomName().getString().contains("RAID LEADERBOARD");

         if (isHolo || isLegacyHolo) {
            if (event.getLevel() instanceof ServerLevel world) {
               RaidSession session = RaidSpawner.getSessionSafe(world);
               if (session == null || session.getState() == RaidSession.State.IDLE) {
                  event.setCanceled(true);
                  entity.remove(Entity.RemovalReason.DISCARDED);
               }
            }
         }
      }
   }
}