package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.Pixelmon;
import net.minecraft.server.CustomServerBossInfo;
import net.minecraft.server.CustomServerBossInfoManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.BossInfo.Color;
import net.minecraft.world.BossInfo.Overlay;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.WorldTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("pixelmonraid")
public class PixelmonRaidMod {
   public static final String MODID = "pixelmonraid";
   public static final ResourceLocation MAIN_BAR_ID = new ResourceLocation("pixelmonraid", "raid_boss_bar");
   public static final ResourceLocation SPACER_BAR_ID = new ResourceLocation("pixelmonraid", "spacer_bar");
   private static final Logger LOGGER = LogManager.getLogger();

   public PixelmonRaidMod() {
      PixelmonRaidConfig.loadConfig();
      FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(RaidSpawner.class);
      MinecraftForge.EVENT_BUS.register(new RaidAdminUIListener());
      MinecraftForge.EVENT_BUS.register(new RaidLeaderboardUIListener());
      MinecraftForge.EVENT_BUS.register(PixelmonBossAttackListener.class);
      Pixelmon.EVENT_BUS.register(PixelmonBossAttackListener.class);
   }

   private void setup(FMLCommonSetupEvent event) {
      LOGGER.info("Pixelmon Raid System Initialized.");
   }

   @SubscribeEvent
   public void onRegisterCommands(RegisterCommandsEvent event) {
      RaidCommand.register(event.getDispatcher());
      RaidAdminCommand.register(event.getDispatcher());
      RaidLeaderboardCommand.register(event.getDispatcher());
   }

   @SubscribeEvent
   public void onServerStarted(FMLServerStartedEvent event) {
      CustomServerBossInfoManager manager = event.getServer().getCustomBossEvents();
      CustomServerBossInfo spacer = manager.get(SPACER_BAR_ID);
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
      CustomServerBossInfo bar = manager.get(MAIN_BAR_ID);
      if (bar == null) {
         bar = manager.create(MAIN_BAR_ID, new StringTextComponent("Raid System Loading..."));
         bar.setColor(Color.RED);
         bar.setOverlay(Overlay.NOTCHED_10);
      }

      bar.removeAllPlayers();
      bar.setVisible(false);
      bar.setValue(0);
      ServerWorld world = event.getServer().getLevel(World.OVERWORLD);
      if (world != null) {
         RaidSaveData.get(world);
         RaidSession session = RaidSpawner.getSessionSafe(world);
         if (session != null) {
            session.forceResetTimer();
         }
      }
   }

   @SubscribeEvent
   public void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END && !event.world.isClientSide && event.world instanceof ServerWorld && event.world.dimension().location().equals(World.OVERWORLD.location())) {
         RaidSession session = RaidSpawner.getSessionSafe((ServerWorld)event.world);
         if (session != null) {
            session.tick(event.world.getGameTime());
         }
      }
   }

   @SubscribeEvent
   public void onServerStopping(FMLServerStoppingEvent event) {
      RaidSpawner.clearAllSessions();
      CustomServerBossInfoManager manager = event.getServer().getCustomBossEvents();
      CustomServerBossInfo spacer = manager.get(SPACER_BAR_ID);
      if (spacer != null) {
         spacer.removeAllPlayers();
         spacer.setVisible(false);
         manager.remove(spacer);
      }

      CustomServerBossInfo main = manager.get(MAIN_BAR_ID);
      if (main != null) {
         main.removeAllPlayers();
         main.setVisible(false);
      }
   }
}