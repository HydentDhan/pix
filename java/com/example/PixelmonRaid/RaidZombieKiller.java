package com.example.PixelmonRaid;

import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class RaidZombieKiller {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide) return;
        Entity entity = event.getEntity();

        if (entity.getPersistentData().contains("pixelmonraid_boss")) {
            if (event.getWorld() instanceof ServerWorld) {
                ServerWorld world = (ServerWorld) event.getWorld();
                RaidSession session = RaidSpawner.getSessionSafe(world);

                if (session == null || !session.isRaidEntity(entity.getUUID())) {
                    event.setCanceled(true);
                    entity.remove();
                }
            }
        }


        if (entity instanceof net.minecraft.entity.item.ArmorStandEntity) {
            boolean isHolo = entity.getPersistentData().getBoolean("pixelmonraid_hologram");
            boolean isLegacyHolo = entity.getCustomName() != null && entity.getCustomName().getString().contains("RAID LEADERBOARD");

            if (isHolo || isLegacyHolo) {
                ServerWorld world = (ServerWorld) event.getWorld();
                RaidSession session = RaidSpawner.getSessionSafe(world);


                if (session == null || session.getState() == RaidSession.State.IDLE) {
                    event.setCanceled(true);
                    entity.remove();
                }
            }
        }
    }
}
