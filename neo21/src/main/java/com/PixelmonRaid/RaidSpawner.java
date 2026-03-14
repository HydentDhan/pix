package com.PixelmonRaid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber
public class RaidSpawner {
   private static final Map<ResourceLocation, RaidSession> SESSIONS = new HashMap<>();
   private static final Map<UUID, RaidSession> SESSIONS_BY_ID = new HashMap<>();
   private static final List<String> NORMAL_BOSSES = Arrays.asList("Charizard", "Garchomp", "Snorlax", "Lucario", "Gengar");

   public static void clearAllSessions() {
      SESSIONS.clear();
      SESSIONS_BY_ID.clear();
   }

   public static void forceSpawnStatue(ServerLevel world) {
      RaidSession session = getSessionSafe(world);
      if (session != null) {
         session.cleanup();
         spawnBoss(session);
      }
   }

   @SubscribeEvent
   public static void onWorldTick(LevelTickEvent.Post event) {
      if (!event.getLevel().isClientSide()) {
         if (event.getLevel() instanceof ServerLevel world) {
            if (world.dimension().location().toString().equals("minecraft:overworld")) {
               RaidSession session = getSessionSafe(world);
               if (session != null) {
                  session.tick(world.getGameTime());
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onWorldLoad(LevelEvent.Load event) {
      if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel world) {
         if (world.dimension().location().equals(Level.OVERWORLD.location())) {
            getSession(world);
         }
      }
   }

   public static RaidSession getSessionSafe(ServerLevel world) {
      if (world == null) {
         return null;
      } else if (!world.dimension().location().toString().equals("minecraft:overworld")) {
         return null;
      } else {
         ResourceLocation key = world.dimension().location();
         return SESSIONS.get(key);
      }
   }

   public static RaidSession getSession(ServerLevel world) {
      if (world == null) {
         return null;
      } else if (!world.dimension().location().toString().equals("minecraft:overworld")) {
         return null;
      } else {
         ResourceLocation key = world.dimension().location();
         return SESSIONS.computeIfAbsent(key, (k) -> {
            RaidSaveData data = RaidSaveData.get(world);
            RaidSession s = new RaidSession(world, data.getCenter());
            registerSessionById(s);
            return s;
         });
      }
   }

   private static void registerSessionById(RaidSession session) {
      if (session != null) {
         SESSIONS_BY_ID.put(session.getRaidId(), session);
      }
   }

   public static void registerSessionByIdPublic(RaidSession session) {
      registerSessionById(session);
   }

   public static RaidSession getSessionByRaidId(UUID raidId) {
      return raidId == null ? null : SESSIONS_BY_ID.get(raidId);
   }

   public static void unregisterSessionById(UUID raidId) {
      if (raidId != null) {
         SESSIONS_BY_ID.remove(raidId);
      }
   }

   public static boolean spawnBoss(RaidSession session) {
      if (session == null) {
         return false;
      } else {
         try {
            ServerLevel world = session.getWorld();
            BlockPos pos = session.getCenter();
            if (!world.dimension().location().toString().equals("minecraft:overworld")) {
               return false;
            } else if (!world.isLoaded(pos)) {
               return false;
            } else {
               if (session.getBossEntityUUIDs() != null) {
                  for (UUID id : new ArrayList<>(session.getBossEntityUUIDs())) {
                     Entity e = world.getEntity(id);
                     if (e != null) {
                        e.remove(Entity.RemovalReason.DISCARDED);
                     }
                  }
               }

               session.clearBossEntities();
               List<StatueEntity> oldStatues = world.getEntitiesOfClass(StatueEntity.class, (new AABB(pos)).inflate(2.0D));
               for (StatueEntity old : oldStatues) {
                  try {
                     if (old.getPersistentData().getBoolean("pixelmonraid_template")) {
                        old.remove(Entity.RemovalReason.DISCARDED);
                     }
                  } catch (Throwable var21) {}
               }

               int currentDiff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
               String speciesName = PixelmonRaidConfig.getInstance().getRandomBossSpecies(currentDiff);
               if (speciesName == null) {
                  speciesName = NORMAL_BOSSES.get((new Random()).nextInt(NORMAL_BOSSES.size()));
               }

               Species species = resolveSpecies(speciesName);
               session.setCurrentBossName(species.getName());
               Pokemon pokemon;
               try {
                  pokemon = PokemonBuilder.builder().species(species).level(100).build();
               } catch (Throwable var20) {
                  return false;
               }

               try {
                  for (BattleStatsType stat : BattleStatsType.values()) {
                     try { pokemon.getIVs().setStat(stat, 31); } catch (Throwable var19) {}
                     try { pokemon.getEVs().setStat(stat, 252); } catch (Throwable var18) {}
                  }
               } catch (Throwable var22) {}

               EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("pixelmon", "statue"));
               if (et == null) {
                  return false;
               } else {
                  StatueEntity statue = (StatueEntity)et.create(world);
                  if (statue == null) {
                     return false;
                  } else {
                     statue.setPokemon(pokemon);
                     float scale = (float)PixelmonRaidConfig.getInstance().getBossScaleFactor();

                     try { statue.setPixelmonScale(scale); } catch (Throwable var17) {}

                     statue.getPersistentData().putBoolean("pixelmonraid_boss", true);
                     statue.getPersistentData().putBoolean("pixelmonraid_template", true);
                     statue.getPersistentData().putUUID("pixelmonraid_raidId", session.getRaidId());
                     int wins = RaidSaveData.get(world).getWinStreak();
                     int raidPool = PixelmonRaidConfig.getInstance().getBossHP(currentDiff, wins);
                     statue.getPersistentData().putInt("pixelmonraid_hp_pool", raidPool);
                     session.setTotalRaidHP(raidPool);
                     statue.setPos((double)pos.getX() + 0.5D, (double)pos.getY(), (double)pos.getZ() + 0.5D);
                     session.registerTemplate(statue.getUUID());
                     registerSessionById(session);
                     world.addFreshEntity(statue);
                     world.playSound((Player)null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 500.0F, 0.8F);

                     ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(Component.literal("§4§l☠ RAID BOSS SPAWNED ☠"));
                     ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(Component.literal("§c" + session.getCurrentBossName() + " has appeared!"));
                     for (ServerPlayer p : world.players()) {
                        p.connection.send(titlePacket);
                        p.connection.send(subtitlePacket);
                     }

                     return true;
                  }
               }
            }
         } catch (Throwable var23) {
            return false;
         }
      }
   }

   public static void scheduleStartPlayerBattle(RaidSession session, ServerPlayer player) {
      if (session != null && player != null) {
         try {
            player.getServer().execute(() -> {
               try {
                  session.startPlayerBattleRequest(player);
               } catch (Throwable var3) {}
            });
         } catch (Throwable var3) {}
      }
   }

   public static void despawnBosses(RaidSession session) {
      if (session != null) {
         unregisterSessionById(session.getRaidId());
         session.cleanup();
      }
   }

   private static Species resolveSpecies(String speciesName) {
      if (speciesName != null && !speciesName.isEmpty()) {
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
         } catch (Throwable var4) {}
         return PixelmonSpecies.CHARIZARD.getValueUnsafe();
      } else {
         return PixelmonSpecies.CHARIZARD.getValueUnsafe();
      }
   }
}