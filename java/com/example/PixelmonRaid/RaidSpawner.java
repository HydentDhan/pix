package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.network.play.server.STitlePacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class RaidSpawner {
    private static final Map<ResourceLocation, RaidSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, RaidSession> SESSIONS_BY_ID = new HashMap<>();
    private static final List<String> NORMAL_BOSSES = Arrays.asList("Charizard", "Garchomp", "Snorlax", "Lucario", "Gengar");

    public static void clearAllSessions() {
        SESSIONS.clear();
        SESSIONS_BY_ID.clear();
    }

    public static void forceSpawnStatue(ServerWorld world) {
        RaidSession session = getSessionSafe(world);
        if (session != null) {
            session.cleanup();
            spawnBoss(session);
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.world.isClientSide) return;
        if (!(event.world instanceof ServerWorld)) return;
        if (!event.world.dimension().location().toString().equals("minecraft:overworld")) return;

        ServerWorld world = (ServerWorld) event.world;
        RaidSession session = getSessionSafe(world);
        if (session != null) {
            session.tick(world.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isClientSide() && event.getWorld() instanceof ServerWorld) {
            ServerWorld world = (ServerWorld) event.getWorld();
            if (world.dimension().location().equals(World.OVERWORLD.location())) {
                getSession(world);
            }
        }
    }

    public static RaidSession getSessionSafe(ServerWorld world) {
        if (world == null) return null;
        if (!world.dimension().location().toString().equals("minecraft:overworld")) return null;
        ResourceLocation key = world.dimension().location();
        return SESSIONS.get(key);
    }

    public static RaidSession getSession(ServerWorld world) {
        if (world == null) return null;
        if (!world.dimension().location().toString().equals("minecraft:overworld")) return null;
        ResourceLocation key = world.dimension().location();
        return SESSIONS.computeIfAbsent(key, k -> {
            RaidSaveData data = RaidSaveData.get(world);
            RaidSession s = new RaidSession(world, data.getCenter());
            registerSessionById(s);
            return s;
        });
    }

    private static void registerSessionById(RaidSession session) {
        if (session == null) return;
        SESSIONS_BY_ID.put(session.getRaidId(), session);
    }

    public static void registerSessionByIdPublic(RaidSession session) {
        registerSessionById(session);
    }

    public static RaidSession getSessionByRaidId(UUID raidId) {
        if (raidId == null) return null;
        return SESSIONS_BY_ID.get(raidId);
    }

    public static void unregisterSessionById(UUID raidId) {
        if (raidId == null) return;
        SESSIONS_BY_ID.remove(raidId);
    }

    public static boolean spawnBoss(RaidSession session) {
        if (session == null) return false;
        try {
            ServerWorld world = session.getWorld();
            BlockPos pos = session.getCenter();

            if (!world.dimension().location().toString().equals("minecraft:overworld")) return false;
            if (!world.isLoaded(pos)) return false;

            if (session.getBossEntityUUIDs() != null) {
                for (UUID id : new ArrayList<>(session.getBossEntityUUIDs())) {
                    Entity e = world.getEntity(id);
                    if (e != null) e.remove();
                }
            }
            session.clearBossEntities();

            List<StatueEntity> oldStatues = world.getEntitiesOfClass(StatueEntity.class, new AxisAlignedBB(pos).inflate(2));
            for (StatueEntity old : oldStatues) {
                try {
                    if (old.getPersistentData().getBoolean("pixelmonraid_template")) old.remove();
                } catch (Throwable ignored) {}
            }

            int currentDiff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
            String speciesName = PixelmonRaidConfig.getInstance().getRandomBossSpecies(currentDiff);

            if (speciesName == null) speciesName = NORMAL_BOSSES.get(new Random().nextInt(NORMAL_BOSSES.size()));

            Species species = resolveSpecies(speciesName);

            session.setCurrentBossName(species.getName());

            Pokemon pokemon;
            try {
                pokemon = PokemonBuilder.builder().species(species).level(100).build();
            } catch (Throwable ex) {
                System.err.println("[PixelmonRaid] Failed to build Pokemon: " + speciesName);
                ex.printStackTrace();
                return false;
            }

            try {
                for (BattleStatsType stat : BattleStatsType.values()) {
                    try { pokemon.getIVs().setStat(stat, 31); } catch (Throwable ignored) {}
                    try { pokemon.getEVs().setStat(stat, 252); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            EntityType<?> et = ForgeRegistries.ENTITIES.getValue(new ResourceLocation("pixelmon", "statue"));
            if (et == null) return false;

            StatueEntity statue = (StatueEntity) et.create(world);
            if (statue == null) return false;

            statue.setPokemon(pokemon);

            float scale = (float) PixelmonRaidConfig.getInstance().getBossScaleFactor();
            try { statue.setPixelmonScale(scale); } catch (Throwable ignored) {}

            statue.getPersistentData().putBoolean("pixelmonraid_boss", true);
            statue.getPersistentData().putBoolean("pixelmonraid_template", true);
            statue.getPersistentData().putUUID("pixelmonraid_raidId", session.getRaidId());

            int raidPool = PixelmonRaidConfig.getInstance().getBossHP();
            int wins = RaidSaveData.get(world).getWinStreak();
            if (wins > 0) {
                raidPool += (int)(raidPool * (wins * 0.10));
            }

            statue.getPersistentData().putInt("pixelmonraid_hp_pool", raidPool);
            session.setTotalRaidHP(raidPool);
            statue.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            session.registerTemplate(statue.getUUID());
            registerSessionById(session);
            world.addFreshEntity(statue);

            world.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 500.0f, 0.8f);
            STitlePacket titlePacket = new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent("§4§l☠ RAID BOSS SPAWNED ☠"));
            STitlePacket subtitlePacket = new STitlePacket(STitlePacket.Type.SUBTITLE, new StringTextComponent("§c" + session.getCurrentBossName() + " has appeared!"));
            for (ServerPlayerEntity p : world.getPlayers(player -> true)) {
                p.connection.send(titlePacket);
                p.connection.send(subtitlePacket);
            }

            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static void scheduleStartPlayerBattle(final RaidSession session, final ServerPlayerEntity player) {
        if (session == null || player == null) return;
        try {
            player.getServer().execute(() -> {
                try {
                    session.startPlayerBattleRequest(player);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void despawnBosses(RaidSession session) {
        if (session != null) {
            unregisterSessionById(session.getRaidId());
            session.cleanup();
        }
    }

    private static Species resolveSpecies(String speciesName) {
        if (speciesName == null || speciesName.isEmpty()) return PixelmonSpecies.CHARIZARD.getValueUnsafe();

        try {

            Optional<RegistryValue<Species>> opt = PixelmonSpecies.get(speciesName);

            if (opt.isPresent()) {
                RegistryValue<Species> regVal = opt.get();
                if (regVal.isInitialized()) {
                    return regVal.getValueUnsafe();
                }


                Optional<Species> valOpt = regVal.getValue();
                if (valOpt.isPresent()) {
                    return valOpt.get();
                }
            }
        } catch (Throwable t) {
            System.err.println("[PixelmonRaid] Error resolving species '" + speciesName + "': " + t.getMessage());
        }

        System.err.println("[PixelmonRaid] WARNING: Species '" + speciesName + "' not found! Defaulting to Charizard.");
        return PixelmonSpecies.CHARIZARD.getValueUnsafe();
    }
}