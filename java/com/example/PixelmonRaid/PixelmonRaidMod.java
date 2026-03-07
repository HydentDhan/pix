package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.Pixelmon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.server.CustomServerBossInfoManager;
import net.minecraft.server.CustomServerBossInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PixelmonRaidMod.MODID)
public class PixelmonRaidMod {
    public static final String MODID = "pixelmonraid";
    public static final ResourceLocation MAIN_BAR_ID = new ResourceLocation(MODID, "raid_boss_bar");
    public static final ResourceLocation SPACER_BAR_ID = new ResourceLocation(MODID, "spacer_bar");

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

    private void setup(final FMLCommonSetupEvent event) {
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

        manager.getEvents().forEach(bar -> {
            if (!bar.getId().equals(MAIN_BAR_ID) && !bar.getId().equals(SPACER_BAR_ID)) {
                if (bar.getName().getString().contains("Raid") || bar.getName().getString().contains("Time")) {
                    bar.removeAllPlayers();
                    bar.setVisible(false);
                }
            }
        });

        CustomServerBossInfo bar = manager.get(MAIN_BAR_ID);
        if (bar == null) {
            bar = manager.create(MAIN_BAR_ID, new StringTextComponent("Raid System Loading..."));
            bar.setColor(BossInfo.Color.RED);
            bar.setOverlay(BossInfo.Overlay.NOTCHED_10);
        }
        bar.removeAllPlayers();
        bar.setVisible(false);
        bar.setValue(0);

        ServerWorld world = event.getServer().getLevel(World.OVERWORLD);
        if (world != null) {
            RaidSaveData.get(world);
            RaidSession session = RaidSpawner.getSessionSafe(world);
            if (session != null) session.forceResetTimer();
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isClientSide) {
            if (event.world instanceof ServerWorld) {
                if (event.world.dimension().location().equals(World.OVERWORLD.location())) {
                    RaidSession session = RaidSpawner.getSessionSafe((ServerWorld) event.world);
                    if (session != null) {
                        session.tick(event.world.getGameTime());
                    }
                }
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