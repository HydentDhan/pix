package com.PixelmonRaid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pixelmonmod.pixelmon.Pixelmon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod("pixelmonraid")
public class PixelmonRaidMod {
   public static final String MODID = "pixelmonraid";
   public static final ResourceLocation MAIN_BAR_ID = ResourceLocation.fromNamespaceAndPath("pixelmonraid", "raid_boss_bar");
   public static final ResourceLocation SPACER_BAR_ID = ResourceLocation.fromNamespaceAndPath("pixelmonraid", "spacer_bar");
   private static final Logger LOGGER = LogManager.getLogger();

   public PixelmonRaidMod(IEventBus modEventBus) {
      PixelmonRaidConfig.loadConfig();
      modEventBus.addListener(this::setup);
      NeoForge.EVENT_BUS.register(this);
      NeoForge.EVENT_BUS.register(RaidSpawner.class);
      NeoForge.EVENT_BUS.register(RaidAdminUIListener.class);
      NeoForge.EVENT_BUS.register(RaidLeaderboardUIListener.class);
      NeoForge.EVENT_BUS.register(PixelmonBossAttackListener.class);
      Pixelmon.EVENT_BUS.register(PixelmonBossAttackListener.class);
   }

   private void setup(final FMLCommonSetupEvent event) {
      LOGGER.info("Pixelmon Raid System Initialized for 1.21.1.");
   }

   @SubscribeEvent
   public void onRegisterCommands(RegisterCommandsEvent event) {
      RaidCommand.register(event.getDispatcher());
      RaidAdminCommand.register(event.getDispatcher());
      RaidLeaderboardCommand.register(event.getDispatcher());
   }

   @SubscribeEvent
   public void onServerStarted(ServerStartedEvent event) {
      CustomBossEvents manager = event.getServer().getCustomBossEvents();
      CustomBossEvent spacer = manager.get(SPACER_BAR_ID);
      if (spacer != null) {
         spacer.removeAllPlayers();
         spacer.setVisible(false);
         manager.remove(spacer);
      }

      manager.getEvents().forEach((barx) -> {
         if (!barx.getId().equals(MAIN_BAR_ID) && !barx.getId().equals(SPACER_BAR_ID) && (barx.getName().getString().contains("Raid") || barx.getName().getString().contains("Time"))) {
            barx.removeAllPlayers();
            barx.setVisible(false);
         }
      });
      CustomBossEvent bar = manager.get(MAIN_BAR_ID);
      if (bar == null) {
         bar = manager.create(MAIN_BAR_ID, Component.literal("Raid System Loading..."));
         bar.setColor(BossBarColor.RED);
         bar.setOverlay(BossBarOverlay.NOTCHED_10);
      }

      bar.removeAllPlayers();
      bar.setVisible(false);
      bar.setProgress(0.0F);
      ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
      if (world != null) {
         RaidSaveData.get(world);
         RaidSession session = RaidSpawner.getSessionSafe(world);
         if (session != null) {
            session.forceResetTimer();
         }
      }
   }

   @SubscribeEvent
   public void onWorldTick(LevelTickEvent.Post event) {
      if (!event.getLevel().isClientSide && event.getLevel() instanceof ServerLevel sl && sl.dimension().location().equals(Level.OVERWORLD.location())) {
         RaidSession session = RaidSpawner.getSessionSafe(sl);
         if (session != null) {
            session.tick(sl.getGameTime());
         }
      }
   }

   @SubscribeEvent
   public void onServerStopping(ServerStoppingEvent event) {
      RaidSpawner.clearAllSessions();
      CustomBossEvents manager = event.getServer().getCustomBossEvents();
      CustomBossEvent spacer = manager.get(SPACER_BAR_ID);
      if (spacer != null) {
         spacer.removeAllPlayers();
         spacer.setVisible(false);
         manager.remove(spacer);
      }

      CustomBossEvent main = manager.get(MAIN_BAR_ID);
      if (main != null) {
         main.removeAllPlayers();
         main.setVisible(false);
      }
   }
}