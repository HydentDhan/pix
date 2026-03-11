package com.example.PixelmonRaid;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraft.network.play.server.STitlePacket.Type;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.WorldTickEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
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
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.START && !event.world.isClientSide()) {
         if (event.world instanceof ServerWorld) {
            if (event.world.dimension().location().toString().equals("minecraft:overworld")) {
               ServerWorld world = (ServerWorld)event.world;
               RaidSession session = getSessionSafe(world);
               if (session != null) {
                  session.tick(world.getGameTime());
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onWorldLoad(Load event) {
      if (!event.getWorld().isClientSide() && event.getWorld() instanceof ServerWorld) {
         ServerWorld world = (ServerWorld)event.getWorld();
         if (world.dimension().location().equals(World.OVERWORLD.location())) {
            getSession(world);
         }
      }
   }

   public static RaidSession getSessionSafe(ServerWorld world) {
      if (world == null) {
         return null;
      } else if (!world.dimension().location().toString().equals("minecraft:overworld")) {
         return null;
      } else {
         ResourceLocation key = world.dimension().location();
         return SESSIONS.get(key);
      }
   }

   public static RaidSession getSession(ServerWorld world) {
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
            ServerWorld world = session.getWorld();
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
                        e.remove();
                     }
                  }
               }

               session.clearBossEntities();
               List<StatueEntity> oldStatues = world.getEntitiesOfClass(StatueEntity.class, (new AxisAlignedBB(pos)).inflate(2.0D));
               for (StatueEntity old : oldStatues) {
                  try {
                     if (old.getPersistentData().getBoolean("pixelmonraid_template")) {
                        old.remove();
                     }
                  } catch (Throwable var21) {
                  }
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
                  System.err.println("[PixelmonRaid] Failed to build Pokemon: " + speciesName);
                  var20.printStackTrace();
                  return false;
               }

               try {
                  for (BattleStatsType stat : BattleStatsType.values()) {
                     try {
                        pokemon.getIVs().setStat(stat, 31);
                     } catch (Throwable var19) {
                     }

                     try {
                        pokemon.getEVs().setStat(stat, 252);
                     } catch (Throwable var18) {
                     }
                  }
               } catch (Throwable var22) {
               }

               EntityType<?> et = ForgeRegistries.ENTITIES.getValue(new ResourceLocation("pixelmon", "statue"));
               if (et == null) {
                  return false;
               } else {
                  StatueEntity statue = (StatueEntity)et.create(world);
                  if (statue == null) {
                     return false;
                  } else {
                     statue.setPokemon(pokemon);
                     float scale = (float)PixelmonRaidConfig.getInstance().getBossScaleFactor();

                     try {
                        statue.setPixelmonScale(scale);
                     } catch (Throwable var17) {
                     }

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
                     world.playSound((PlayerEntity)null, pos, SoundEvents.WITHER_SPAWN, SoundCategory.HOSTILE, 500.0F, 0.8F);

                     STitlePacket titlePacket = new STitlePacket(Type.TITLE, new StringTextComponent("§4§l☠ RAID BOSS SPAWNED ☠"));
                     STitlePacket subtitlePacket = new STitlePacket(Type.SUBTITLE, new StringTextComponent("§c" + session.getCurrentBossName() + " has appeared!"));

                     for (ServerPlayerEntity p : world.players()) {
                        p.connection.send(titlePacket);
                        p.connection.send(subtitlePacket);
                     }

                     return true;
                  }
               }
            }
         } catch (Throwable var23) {
            var23.printStackTrace();
            return false;
         }
      }
   }

   public static void scheduleStartPlayerBattle(RaidSession session, ServerPlayerEntity player) {
      if (session != null && player != null) {
         try {
            player.getServer().execute(() -> {
               try {
                  session.startPlayerBattleRequest(player);
               } catch (Throwable var3) {
                  var3.printStackTrace();
               }
            });
         } catch (Throwable var3) {
            var3.printStackTrace();
         }
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
         } catch (Throwable var4) {
            System.err.println("[PixelmonRaid] Error resolving species '" + speciesName + "': " + var4.getMessage());
         }

         System.err.println("[PixelmonRaid] WARNING: Species '" + speciesName + "' not found! Defaulting to Charizard.");
         return PixelmonSpecies.CHARIZARD.getValueUnsafe();
      } else {
         return PixelmonSpecies.CHARIZARD.getValueUnsafe();
      }
   }
}