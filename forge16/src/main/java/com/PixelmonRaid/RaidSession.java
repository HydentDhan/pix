package com.PixelmonRaid;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.enums.EnumGrowth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.CustomServerBossInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.event.ClickEvent.Action;
import net.minecraft.world.BossInfo.Color;
import net.minecraft.world.BossInfo.Overlay;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

public class RaidSession {
   private final UUID raidId;
   private ServerWorld world;
   private RaidSession.State state;
   private long battleStartTick;
   private long suddenDeathStartTick;
   private boolean isSpawning;
   private final Set<UUID> players;
   private UUID templateUUID;
   private final Set<UUID> activeCopyUUIDs;
   private final DamageTracker damageTracker;
   private final Map<UUID, Long> joinTimes;
   private final Map<UUID, Long> fatigueMap;
   private final Map<UUID, Integer> spawnDelays;
   private final Set<UUID> pendingBattles;
   private final Map<UUID, Long> rejoinCooldowns;
   private final List<UUID> hologramLines;
   private boolean rewardsDistributed;
   private boolean hasEnraged;
   private int totalRaidHP;
   private int maxRaidHP;
   private String currentBossName;
   private volatile boolean battleActive;
   private int spawnFailureCount;
   private long lastBroadcastTime;
   private long lastHpBroadcastTick;
   private static final List<String> BANNED_MOVES = Arrays.asList("Fissure", "Guillotine", "Horn Drill", "Sheer Cold", "Endeavor", "Pain Split", "Perish Song", "Destiny Bond", "Super Fang", "Nature's Madness", "Curse", "Toxic", "Leech Seed", "Spore", "Hypnosis", "Sleep Powder", "Yawn", "Lovely Kiss", "Secret Power", "Grass Whistle", "Sing", "Dark Void", "Dire Claw", "Psycho Shift");

   public RaidSession(ServerWorld world, BlockPos ignored) {
      this.state = RaidSession.State.IDLE;
      this.isSpawning = false;
      this.players = ConcurrentHashMap.newKeySet();
      this.templateUUID = null;
      this.activeCopyUUIDs = ConcurrentHashMap.newKeySet();
      this.damageTracker = new DamageTracker();
      this.joinTimes = new HashMap<>();
      this.fatigueMap = new HashMap<>();
      this.spawnDelays = new ConcurrentHashMap<>();
      this.pendingBattles = ConcurrentHashMap.newKeySet();
      this.rejoinCooldowns = new ConcurrentHashMap<>();
      this.hologramLines = new ArrayList<>();
      this.rewardsDistributed = false;
      this.hasEnraged = false;
      this.currentBossName = "Charizard";
      this.battleActive = false;
      this.spawnFailureCount = 0;
      this.lastBroadcastTime = 0L;
      this.lastHpBroadcastTick = -1L;
      this.world = world;
      this.raidId = UUID.randomUUID();
      int wins = 0;
      if (world != null) {
         wins = RaidSaveData.get(world).getWinStreak();
      }

      int configHP = PixelmonRaidConfig.getInstance().getBossHP(PixelmonRaidConfig.getInstance().getRaidDifficulty(), wins);
      this.totalRaidHP = configHP;
      this.maxRaidHP = this.totalRaidHP;
   }

   public UUID getRaidId() {
      return this.raidId;
   }

   public ServerWorld getWorld() {
      return this.world;
   }

   public DamageTracker getDamageTracker() {
      return this.damageTracker;
   }

   public int getMaxRaidHP() {
      return this.maxRaidHP;
   }

   public int getTotalRaidHP() {
      return this.totalRaidHP;
   }

   public boolean isAutoRaidEnabled() {
      return PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
   }

   public void setTotalRaidHP(int hp) {
      this.totalRaidHP = Math.max(0, hp);
      if (this.totalRaidHP > this.maxRaidHP) {
         this.maxRaidHP = this.totalRaidHP;
      }
   }

   public void setCurrentBossName(String name) {
      if (name != null && !name.isEmpty()) {
         this.currentBossName = name;
      }
   }

   public String getCurrentBossName() {
      return this.currentBossName;
   }

   public Set<UUID> getPlayers() {
      return new HashSet<>(this.players);
   }

   public RaidSession.State getState() {
      return this.state;
   }

   public boolean isBattleActive() {
      return this.battleActive;
   }

   public void setBattleActive(boolean v) {
      this.battleActive = v;
   }

   public void skipTimer(int seconds) {
      long ticks = (long)seconds * 20L;
      if (this.state == RaidSession.State.IDLE) {
         long currentNext = RaidSaveData.get(this.world).getNextRaidTick();
         RaidSaveData.get(this.world).setNextRaidTick(currentNext - ticks);
      } else if (this.state == RaidSession.State.IN_BATTLE) {
         this.battleStartTick -= ticks;
      } else if (this.state == RaidSession.State.SUDDEN_DEATH) {
         this.suddenDeathStartTick -= ticks;
      }

      this.broadcastPoolUpdate();
   }

   public void extendTime(int seconds) {
      this.skipTimer(-seconds);
   }

   public void reduceTime(int seconds) {
      this.skipTimer(seconds);
   }

   public void resetTime() {
      if (this.state == RaidSession.State.IN_BATTLE) {
         this.battleStartTick = this.world.getGameTime();
      }

      if (this.state == RaidSession.State.SUDDEN_DEATH) {
         this.suddenDeathStartTick = this.world.getGameTime();
      }
   }

   public void setState(RaidSession.State s) {
      this.state = s;
      if (s != RaidSession.State.IN_BATTLE && s != RaidSession.State.SUDDEN_DEATH) {
         this.clearHolograms();
         if (s != RaidSession.State.IDLE || !PixelmonRaidConfig.getInstance().isShowIdleTimerBar()) {
            this.removeHealthBar();
         }
      } else if (s == RaidSession.State.IN_BATTLE) {
         this.battleStartTick = this.world.getGameTime();
      } else if (s == RaidSession.State.SUDDEN_DEATH) {
         this.suddenDeathStartTick = this.world.getGameTime();
      }

      if (s == RaidSession.State.COMPLETED) {
         this.rewardsDistributed = false;
         this.hasEnraged = false;
         this.isSpawning = false;
         this.spawnFailureCount = 0;
      }

      this.updateBossBarImmediate();
   }

   public BlockPos getCenter() {
      return RaidSaveData.get(this.world).getCenter();
   }

   public void setCenter(BlockPos pos) {
      RaidSaveData.get(this.world).setCenter(pos);
   }

   public BlockPos getPlayerSpawn() {
      return RaidSaveData.get(this.world).getPlayerSpawn();
   }

   public void setPlayerSpawn(BlockPos pos) {
      RaidSaveData.get(this.world).setPlayerSpawn(pos);
   }

   public Set<UUID> getBossEntityUUIDs() {
      Set<UUID> all = new HashSet<>(this.activeCopyUUIDs);
      if (this.templateUUID != null) {
         all.add(this.templateUUID);
      }
      return all;
   }

   public void registerTemplate(UUID id) {
      if (id != null) {
         this.templateUUID = id;
      }
   }

   public void registerCopy(UUID id) {
      if (id != null) {
         this.activeCopyUUIDs.add(id);
      }
   }

   public void unregisterCopy(UUID id) {
      if (id != null) {
         this.activeCopyUUIDs.remove(id);
      }
   }

   public void clearBossEntities() {
      this.templateUUID = null;
      this.activeCopyUUIDs.clear();
   }

   public boolean isTemplate(UUID id) {
      return id != null && id.equals(this.templateUUID);
   }

   public boolean isCopy(UUID id) {
      return id != null && this.activeCopyUUIDs.contains(id);
   }

   public boolean isRaidEntity(UUID id) {
      return this.isTemplate(id) || this.isCopy(id);
   }

   public boolean addPlayer(UUID playerId) {
      this.joinTimes.putIfAbsent(playerId, System.currentTimeMillis());
      boolean isNew = this.players.add(playerId);
      if (isNew) {
         RaidSaveData.get(this.world).incrementRaidsJoined(playerId);
      }

      return isNew;
   }

   public void removePlayer(UUID playerId) {
      this.players.remove(playerId);
   }

   public void setFatigue(UUID playerId, int seconds) {
      if (seconds > 0) {
         long expiry = System.currentTimeMillis() + (long)seconds * 1000L;
         this.fatigueMap.put(playerId, expiry);
      }
   }

   public boolean isFatigued(UUID playerId) {
      if (!this.fatigueMap.containsKey(playerId)) {
         return false;
      } else if (System.currentTimeMillis() > this.fatigueMap.get(playerId)) {
         this.fatigueMap.remove(playerId);
         return false;
      } else {
         return true;
      }
   }

   public void applyRejoinCooldown(UUID playerId) {
      long duration = (long)PixelmonRaidConfig.getInstance().getRejoinCooldownSeconds() * 1000L;
      if (duration > 0L) {
         this.rejoinCooldowns.put(playerId, System.currentTimeMillis() + duration);
      }
   }

   public void startPlayerBattleRequest(ServerPlayerEntity player) {
      if (this.state != RaidSession.State.IN_BATTLE && this.state != RaidSession.State.SUDDEN_DEATH) {
         player.sendMessage(new StringTextComponent("§cThe Raid is not active right now!"), Util.NIL_UUID);
      } else {
         if (this.rejoinCooldowns.containsKey(player.getUUID())) {
            long end = this.rejoinCooldowns.get(player.getUUID());
            if (System.currentTimeMillis() < end) {
               long left = (end - System.currentTimeMillis()) / 1000L;
               String msg = String.format(PixelmonRaidConfig.getInstance().getMsgCooldown(), left + "s");
               player.sendMessage(new StringTextComponent(msg), Util.NIL_UUID);
               player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
               return;
            }

            this.rejoinCooldowns.remove(player.getUUID());
         }

         if (!this.spawnDelays.containsKey(player.getUUID())) {
            this.addPlayer(player.getUUID());
            this.updateActionBars();
            this.updateBossBarImmediate();
            this.spawnDelays.put(player.getUUID(), 20);
            player.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgJoin()), Util.NIL_UUID);
         }
      }
   }

   private void executeStartBattle(UUID playerId) {
      ServerPlayerEntity player = this.world.getServer().getPlayerList().getPlayer(playerId);
      if (player != null) {
         this.pendingBattles.add(player.getUUID());
         PlayerPartyStorage storage = StorageProxy.getParty(player);
         boolean illegal = false;
         String banReason = "";
         Pokemon[] var6 = storage.getAll();

         for(int var8 = 0; var8 < var6.length; ++var8) {
            Pokemon p = var6[var8];
            if (p != null && !p.isEgg()) {
               if (p.isLegendary() || p.isMythical()) {
                  illegal = true;
                  banReason = "§cLegendary and Mythical Pokémon are BANNED!";
                  break;
               }

               if (p.getMoveset() != null) {
                  for(Attack atk : p.getMoveset()) {
                     if (atk != null && BANNED_MOVES.contains(atk.getMove().getAttackName())) {
                        illegal = true;
                        banReason = "§cYour " + p.getSpecies().getName() + " knows " + atk.getMove().getAttackName() + ", which is BANNED!";
                        break;
                     }
                  }
               }

               if (illegal) {
                  break;
               }
            }
         }

         if (illegal) {
            player.sendMessage(new StringTextComponent("§c§l❌ JOIN FAILED ❌"), Util.NIL_UUID);
            player.sendMessage(new StringTextComponent(banReason), Util.NIL_UUID);
            this.pendingBattles.remove(player.getUUID());
         } else {
            Pokemon activePokemon = null;
            Pokemon[] var30 = storage.getAll();

            for(int var33 = 0; var33 < var30.length; ++var33) {
               Pokemon p = var30[var33];
               if (p != null && !p.isEgg() && !p.isFainted()) {
                  activePokemon = p;
                  break;
               }
            }

            if (activePokemon == null) {
               player.sendMessage(new StringTextComponent("§cYou have no healthy Pokemon to fight!"), Util.NIL_UUID);
               this.pendingBattles.remove(player.getUUID());
            } else {
               try {
                  Entity template = null;
                  if (this.templateUUID != null) {
                     template = this.world.getEntity(this.templateUUID);
                  }

                  BlockPos center = this.getCenter();
                  double spawnZ;
                  double spawnX;
                  double spawnY;
                  if (template != null) {
                     spawnX = template.getX();
                     spawnY = template.getY() - 20.0D;
                     spawnZ = template.getZ();
                  } else {
                     spawnX = (double)center.getX();
                     spawnY = (double)center.getY() - 20.0D;
                     spawnZ = (double)center.getZ();
                  }

                  if (spawnY < 0.0D) {
                     spawnY = 5.0D;
                  }

                  if (!this.world.isLoaded(new BlockPos(spawnX, spawnY, spawnZ))) {
                     player.sendMessage(new StringTextComponent("§c[Raid] Area not loaded! Please get closer to the center."), Util.NIL_UUID);
                     this.pendingBattles.remove(player.getUUID());
                     return;
                  }

                  EntityType<?> type = ForgeRegistries.ENTITIES.getValue(new ResourceLocation("pixelmon", "pixelmon"));
                  if (type == null) {
                     this.pendingBattles.remove(player.getUUID());
                     return;
                  }

                  PixelmonEntity copy = (PixelmonEntity)type.create(this.world);
                  if (copy == null) {
                     this.pendingBattles.remove(player.getUUID());
                     return;
                  }

                  Pokemon cloned = null;
                  if (PixelmonSpecies.get(this.currentBossName).isPresent()) {
                     cloned = PokemonFactory.create((Species)((RegistryValue)PixelmonSpecies.get(this.currentBossName).get()).getValueUnsafe());
                  } else {
                     cloned = PokemonFactory.create((Species)PixelmonSpecies.CHARIZARD.getValueUnsafe());
                  }

                  cloned.setUUID(UUID.randomUUID());
                  cloned.setLevel(100);
                  try {
                     cloned.getStats().setSpeed(99999);
                  } catch (Throwable var26) {
                  }

                  int diffLevel = PixelmonRaidConfig.getInstance().getRaidDifficulty();
                  String heldItemStr = PixelmonRaidConfig.getInstance().getTierRewards(diffLevel).bossHeldItem;
                  if (heldItemStr != null && !heldItemStr.trim().isEmpty()) {
                     Item fallback;
                     try {
                        Item hItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(heldItemStr.trim()));
                        if (hItem != null && hItem != Items.AIR) {
                           cloned.setHeldItem(new ItemStack(hItem));
                        } else {
                           fallback = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pixelmon", "leftovers"));
                           if (fallback != null && fallback != Items.AIR) {
                              cloned.setHeldItem(new ItemStack(fallback));
                           }
                        }
                     } catch (Exception var27) {
                        try {
                           fallback = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pixelmon", "leftovers"));
                           if (fallback != null && fallback != Items.AIR) {
                              cloned.setHeldItem(new ItemStack(fallback));
                           }
                        } catch (Exception var25) {
                        }
                     }
                  }

                  copy.setPokemon(cloned);
                  copy.setPos(spawnX, spawnY, spawnZ);
                  copy.setYBodyRot(180.0F);
                  copy.setYHeadRot(180.0F);
                  copy.setCustomNameVisible(false);
                  copy.setPersistenceRequired();
                  try {
                     copy.getPokemon().setGrowth(EnumGrowth.Ginormous);
                  } catch (Throwable var24) {
                  }

                  copy.setInvisible(true);
                  copy.setInvulnerable(false);
                  copy.getPersistentData().putBoolean("pixelmonraid_boss", true);
                  copy.getPersistentData().putBoolean("pixelmonraid_copy", true);
                  copy.getPersistentData().putUUID("pixelmonraid_raidId", this.raidId);

                  if (this.hasEnraged) {
                     copy.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, 99999, 1));
                     copy.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, 99999, 0));
                  }

                  this.registerCopy(copy.getUUID());
                  this.setBattleActive(true);
                  this.world.addFreshEntity(copy);
                  this.sanitizeMoveset(copy.getPokemon());
                  copy.getPersistentData().putBoolean("pixelmonraid_boss", true);
                  copy.getPersistentData().putBoolean("pixelmonraid_copy", true);
                  copy.getPersistentData().putUUID("pixelmonraid_raidId", this.raidId);

                  try {
                     copy.revive();
                     copy.setHealth(copy.getMaxHealth());
                  } catch (Throwable var23) {
                  }

                  PlayerParticipant p1 = new PlayerParticipant(player, new Pokemon[]{activePokemon});
                  WildPixelmonParticipant p2 = new WildPixelmonParticipant(new PixelmonEntity[]{copy});
                  BattleRules rules = new BattleRules();
                  rules.set(BattleRuleRegistry.BATTLE_TYPE, BattleType.SINGLE);
                  BattleBuilder.builder().teamOne(new BattleParticipant[]{p1}).teamTwo(new BattleParticipant[]{p2}).rules(rules).noSelection().start();
                  this.updateActionBars();
                  this.updateBossBarImmediate();
                  this.pendingBattles.remove(player.getUUID());
               } catch (Throwable var28) {
                  var28.printStackTrace();
                  this.pendingBattles.remove(player.getUUID());
               }
            }
         }
      }
   }

   private void sanitizeMoveset(Pokemon pokemon) {
      try {
         List<String> bannedMoves = Arrays.asList("Roar", "Whirlwind", "Dragon Tail", "Circle Throw", "Teleport");
         for(int i = 0; i < 4; ++i) {
            Attack atk = pokemon.getMoveset().get(i);
            if (atk != null && bannedMoves.contains(atk.getMove().getAttackName())) {
               pokemon.getMoveset().set(i, new Attack("Hyper Beam"));
            }
         }
      } catch (Throwable var5) {
         var5.printStackTrace();
      }
   }

   public void broadcastPoolUpdate() {
      try {
         this.updateBossBarImmediate();
         this.updateActionBars();
         this.updateHologram();

         try {
            RaidSaveData.get(this.world).saveLastLeaderboard(this.damageTracker.getAllDamage(), this.world);
         } catch (Throwable var2) {
         }
      } catch (Throwable var3) {
         var3.printStackTrace();
      }
   }

   private void clearHolograms() {
      Iterator<UUID> var1 = this.hologramLines.iterator();
      while(var1.hasNext()) {
         UUID uuid = var1.next();
         Entity e = this.world.getEntity(uuid);
         if (e != null) {
            e.remove();
         }
      }

      this.hologramLines.clear();
      List<ArmorStandEntity> nearby = this.world.getEntitiesOfClass(ArmorStandEntity.class, (new AxisAlignedBB(this.getCenter())).inflate(10.0D));
      for(ArmorStandEntity as : nearby) {
         if (as.getPersistentData().getBoolean("pixelmonraid_hologram")) {
            as.remove();
         }
      }
   }

   private void updateHologram() {
      if (!PixelmonRaidConfig.getInstance().isHologramEnabled()) {
         if (!this.hologramLines.isEmpty()) {
            this.clearHolograms();
         }
      } else if (this.world != null && this.state != RaidSession.State.IDLE) {
         double x = PixelmonRaidConfig.getInstance().getHoloX();
         double y = PixelmonRaidConfig.getInstance().getHoloY();
         double z = PixelmonRaidConfig.getInstance().getHoloZ();
         Map<UUID, Integer> allDmg = this.damageTracker.getAllDamage();
         List<Entry<UUID, Integer>> sorted = new ArrayList<>(allDmg.entrySet());
         sorted.sort((a, b) -> {
            return Integer.compare(b.getValue(), a.getValue());
         });
         List<String> lines = new ArrayList<>();
         lines.add("§6§l⚔ RAID LEADERBOARD ⚔");

         int i;
         for(i = 0; i < 5; ++i) {
            if (i < sorted.size()) {
               Entry<UUID, Integer> entry = sorted.get(i);
               ServerPlayerEntity pl = this.world.getServer().getPlayerList().getPlayer(entry.getKey());
               String name = pl != null ? pl.getGameProfile().getName() : "Offline";
               String color = i == 0 ? "§6" : (i < 3 ? "§e" : "§f");
               lines.add(color + "#" + (i + 1) + " " + name + " §7- §c" + String.format("%,d", entry.getValue()));
            } else {
               lines.add("§8#" + (i + 1) + " ---");
            }
         }

         while(this.hologramLines.size() < lines.size()) {
            ArmorStandEntity as = new ArmorStandEntity(this.world, x, y, z);
            as.setInvisible(true);
            as.setCustomNameVisible(true);
            as.setNoGravity(true);
            as.setInvulnerable(true);
            this.world.addFreshEntity(as);
            as.getPersistentData().putBoolean("pixelmonraid_hologram", true);
            this.hologramLines.add(as.getUUID());
         }

         for(i = 0; i < this.hologramLines.size(); ++i) {
            Entity e = this.world.getEntity(this.hologramLines.get(i));
            if (e != null) {
               e.setPos(x, y - (double)i * 0.3D, z);
               if (i < lines.size()) {
                  e.setCustomName(new StringTextComponent(lines.get(i)));
               } else {
                  e.setCustomName(new StringTextComponent(""));
               }
            }
         }
      }
   }

   public void updateActionBars() {
      if (this.world != null) {
         int participantCount = this.players.size();
         Map<UUID, Integer> allDmg = this.damageTracker.getAllDamage();
         List<Entry<UUID, Integer>> sorted = new ArrayList<>(allDmg.entrySet());
         sorted.sort((a, b) -> {
            return Integer.compare(b.getValue(), a.getValue());
         });

         for(UUID pid : this.players) {
            ServerPlayerEntity pl = this.world.getServer().getPlayerList().getPlayer(pid);
            if (pl != null) {
               int myRank = -1;
               int myDmg = 0;
               for(int i = 0; i < sorted.size(); ++i) {
                  if (sorted.get(i).getKey().equals(pid)) {
                     myRank = i + 1;
                     myDmg = sorted.get(i).getValue();
                     break;
                  }
               }

               if (myRank != -1) {
                  String rankColor = myRank == 1 ? "§6" : (myRank <= 3 ? "§e" : "§f");
                  String msg = "§b\ud83d\udc65 Players: §f" + participantCount + " §8| §c⚔ Damage: " + rankColor + String.format("%,d", myDmg) + " (#" + myRank + ")";
                  pl.sendMessage(new StringTextComponent(msg), ChatType.GAME_INFO, Util.NIL_UUID);
               }
            }
         }
      }
   }

   public void updateBossBarImmediate() {
      long sdDurationTicks;
      long timeRemaining;
      if (this.state == RaidSession.State.IDLE) {
         if (!PixelmonRaidConfig.getInstance().isShowIdleTimerBar()) {
            this.removeHealthBar();
            return;
         }

         sdDurationTicks = RaidSaveData.get(this.world).getNextRaidTick();
         timeRemaining = Math.max(0L, sdDurationTicks - this.world.getGameTime());
         this.updateIdleBarInfo(timeRemaining);
      } else if (this.state == RaidSession.State.IN_BATTLE) {
         int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
         timeRemaining = 20L * (long)PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff) - (this.world.getGameTime() - this.battleStartTick);
         this.updateBossBarInfo(timeRemaining, false);
      } else if (this.state == RaidSession.State.SUDDEN_DEATH) {
         sdDurationTicks = 20L * (long)PixelmonRaidConfig.getInstance().getSuddenDeathDurationSeconds();
         timeRemaining = sdDurationTicks - (this.world.getGameTime() - this.suddenDeathStartTick);
         this.updateBossBarInfo(timeRemaining, true);
      } else {
         this.removeHealthBar();
      }
   }

   private CustomServerBossInfo getSingletonBar() {
      return this.world != null && this.world.getServer() != null ? this.world.getServer().getCustomBossEvents().get(PixelmonRaidMod.MAIN_BAR_ID) : null;
   }

   private void updateIdleBarInfo(long ticksRemaining) {
      CustomServerBossInfo bar = this.getSingletonBar();
      if (bar != null) {
         if (!this.isAutoRaidEnabled()) {
            bar.setName(new StringTextComponent(PixelmonRaidConfig.getInstance().getUiTimerBarPaused()));
            bar.setColor(Color.RED);
            bar.setOverlay(Overlay.NOTCHED_10);
            bar.setValue(100);
         } else {
            long totalInterval = 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
            float pct = (float)ticksRemaining / (float)Math.max(1L, totalInterval);
            int progress = (int)(pct * 100.0F);
            long sLeft = ticksRemaining / 20L;
            String timeStr = this.formatTimeCustom(sLeft);
            String title = PixelmonRaidConfig.getInstance().getUiTimerBarTitle().replace("%time%", timeStr);
            bar.setName(new StringTextComponent(title));
            bar.setColor(Color.BLUE);
            bar.setOverlay(Overlay.PROGRESS);
            bar.setValue(Math.max(0, Math.min(100, progress)));
         }

         if (!bar.isVisible()) {
            bar.setVisible(true);
         }

         for(ServerPlayerEntity p : this.world.getServer().getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(p)) {
               bar.addPlayer(p);
            }
         }
      }
   }

   private void updateBossBarInfo(long ticksRemaining, boolean isSuddenDeath) {
      CustomServerBossInfo bar = this.getSingletonBar();
      if (bar != null) {
         if (this.state != RaidSession.State.IN_BATTLE && this.state != RaidSession.State.SUDDEN_DEATH) {
            bar.setVisible(false);
            bar.removeAllPlayers();
         } else {
            long sLeft = Math.max(0L, ticksRemaining / 20L);
            String timeStr = this.formatTimeCustom(sLeft);
            String hpInfo = " §7| §c" + this.totalRaidHP + "§7/§c" + this.maxRaidHP + " HP";
            String timeInfo = " §7| §eTime: " + timeStr;
            String title = isSuddenDeath ? "§4§l☠ SUDDEN DEATH: " + this.currentBossName + " ☠" + hpInfo + timeInfo : "§d§lGlobal Boss: " + this.currentBossName + hpInfo + timeInfo;
            float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP);
            bar.setValue((int)(pct * 100.0F));

            if (isSuddenDeath) {
               bar.setColor(Color.PURPLE);
               bar.setOverlay(Overlay.NOTCHED_12);
            } else if (pct > 0.5F) {
               bar.setColor(Color.GREEN);
               bar.setOverlay(Overlay.PROGRESS);
            } else if (pct > 0.2F) {
               bar.setColor(Color.YELLOW);
               bar.setOverlay(Overlay.PROGRESS);
            } else {
               bar.setColor(Color.RED);
               bar.setOverlay(Overlay.NOTCHED_6);
            }

            if (!bar.isVisible()) {
               bar.setVisible(true);
            }

            bar.setMax(100);
            bar.setName(new StringTextComponent(title));
            for(ServerPlayerEntity p : this.world.getServer().getPlayerList().getPlayers()) {
               if (!bar.getPlayers().contains(p)) {
                  bar.addPlayer(p);
               }
            }
         }
      }
   }

   private String formatTimeCustom(long totalSeconds) {
      if (totalSeconds < 60L) {
         return totalSeconds + "s";
      } else {
         long mins = totalSeconds / 60L;
         long hours = mins / 60L;
         long days = hours / 24L;
         mins %= 60L;
         hours %= 24L;
         StringBuilder sb = new StringBuilder();
         if (days > 0L) {
            sb.append(days).append("d ");
         }
         if (hours > 0L) {
            sb.append(hours).append("h ");
         }
         if (mins > 0L) {
            sb.append(mins).append("m ");
         }
         if (days == 0L && hours == 0L) {
            sb.append(totalSeconds % 60L).append("s");
         }

         return sb.toString().trim();
      }
   }

   private void updateBossName() {
      if (this.templateUUID != null) {
         float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP) * 100.0F;
         String hpText = String.format("%.1f%%", pct);
         Entity e = this.world.getEntity(this.templateUUID);
         if (e != null) {
            String name = "§4§l☠ " + this.currentBossName + " | " + hpText + " HP ☠";
            e.setCustomName(new StringTextComponent(name));
            e.setInvulnerable(true);
         }
      }
   }

   public void removeHealthBar() {
      CustomServerBossInfo bar = this.getSingletonBar();
      if (bar != null) {
         bar.removeAllPlayers();
         bar.setVisible(false);
      }
   }

   public void clearSidebar() {
   }

   public void startBattleNow() {
      if (!this.isSpawning) {
         this.isSpawning = true;
         this.spawnFailureCount = 0;
         int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
         int wins = RaidSaveData.get(this.world).getWinStreak();
         int configHP = PixelmonRaidConfig.getInstance().getBossHP(diff, wins);
         this.totalRaidHP = configHP;
         this.maxRaidHP = configHP;
         this.setState(RaidSession.State.IN_BATTLE);
         this.damageTracker.clear();
         this.hasEnraged = false;
         boolean spawned = false;
         if (this.world.isLoaded(this.getCenter())) {
            spawned = RaidSpawner.spawnBoss(this);
         } else {
            spawned = true;
         }

         if (spawned) {
            this.battleStartTick = this.world.getGameTime();
            long durationTicks = 20L * (long)PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff);
            long cooldownTicks = 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
            RaidSaveData.get(this.world).setNextRaidTick(this.world.getGameTime() + durationTicks + cooldownTicks);
            int durationMins = (int)(durationTicks / 20L / 60L);
            String discordDesc = "**Boss:** " + this.currentBossName + "\n**Difficulty:** Level " + diff + "\n**HP:** " + String.format("%,d", this.maxRaidHP) + "\n**Time Limit:** " + durationMins + " Minutes\n\n*Type `/raid join` in-game to fight!*";
            DiscordHandler.sendEmbed("☠️ THE BEAST HAS AWAKENED!", discordDesc, 16711680);
            if (PixelmonRaidConfig.getInstance().isSoundEnabled()) {
               this.world.playSound((PlayerEntity)null, this.getCenter(), SoundEvents.WITHER_SPAWN, SoundCategory.HOSTILE, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0F);
            }

            this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgRaidStart()), ChatType.SYSTEM, Util.NIL_UUID);
         } else {
            this.forceResetTimer();
         }

         this.isSpawning = false;
      }
   }

   public void forceResetTimer() {
      this.cleanup();
      long now = this.world.getGameTime();
      long interval = 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
      RaidSaveData.get(this.world).setNextRaidTick(now + interval);
      this.setState(RaidSession.State.IDLE);
   }

   public void cleanup() {
      this.removeHealthBar();
      this.clearSidebar();
      this.clearHolograms();
      this.spawnDelays.clear();
      this.pendingBattles.clear();

      for (UUID id : new HashSet<>(this.activeCopyUUIDs)) {
         this.unregisterCopy(id);
         Entity e = this.world.getEntity(id);
         if (e != null) {
            e.remove();
         }
      }

      if (this.templateUUID != null) {
         Entity e = this.world.getEntity(this.templateUUID);
         if (e != null) {
            e.remove();
         }

         this.templateUUID = null;
      }

      this.clearBossEntities();
      try {
         RaidSpawner.unregisterSessionById(this.raidId);
      } catch (Throwable var5) {
      }

      try {
         PixelmonBossAttackListener.unregisterSessionTouched(this.raidId);
      } catch (Throwable var4) {
      }

      this.pendingBattles.clear();
      this.players.clear();
      this.damageTracker.clear();
      this.fatigueMap.clear();
      this.joinTimes.clear();
      this.setState(RaidSession.State.IDLE);
      this.isSpawning = false;
      this.setBattleActive(false);
      this.spawnFailureCount = 0;
   }

   public void finishRaid(boolean victory, UUID killerId) {
      for(ServerPlayerEntity pl : this.world.getServer().getPlayerList().getPlayers()) {
         try {
            BattleRegistry.getBattle(pl).endBattle();
         } catch (Throwable var6) {
         }
      }

      if (this.markRewardsDistributedIfNot()) {
         RaidRewardHandler.broadcastLeaderboard(this);
         RaidRewardHandler.distributeRewards(this, victory, killerId);
         RaidSaveData.get(this.world).saveLastLeaderboard(this.damageTracker.getAllDamage(), this.world);
         if (victory) {
            PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
            if (cfg.isSoundEnabled()) {
               this.world.playSound((PlayerEntity)null, this.getCenter(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, cfg.getSoundVolume(), 1.0F);
            }

            this.world.getPlayers((p) -> true).forEach((p) -> {
               p.sendMessage(new StringTextComponent(cfg.getMsgRaidWin()), p.getUUID());
            });

            RaidSaveData.get(this.world).incrementWinStreak();
            boolean actuallyLeveledUp = PixelmonRaidConfig.getInstance().incrementDifficultyOnWin();
            if (actuallyLeveledUp) {
               this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§e§l⚠ RAID DIFFICULTY INCREASED TO LEVEL " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + "!"), ChatType.SYSTEM, Util.NIL_UUID);
            } else {
               this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§b§l✯ MAXIMUM DIFFICULTY CONQUERED! ✯"), ChatType.SYSTEM, Util.NIL_UUID);
               this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§7You have proven yourselves worthy of the highest tier!"), ChatType.SYSTEM, Util.NIL_UUID);
            }
         } else {
            if (PixelmonRaidConfig.getInstance().isSoundEnabled()) {
               this.world.playSound((PlayerEntity)null, this.getCenter(), SoundEvents.WITHER_DEATH, SoundCategory.MASTER, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0F);
            }

            this.world.getPlayers((p) -> true).forEach((p) -> {
               p.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgRaidLoss()), p.getUUID());
            });

            RaidSaveData.get(this.world).resetWinStreak();
            PixelmonRaidConfig.getInstance().decrementDifficultyOnLoss();
            this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§c§l\ud83d\udd3b RAID FAILED! Difficulty Decreased to Level " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + " \ud83d\udd3b"), ChatType.SYSTEM, Util.NIL_UUID);
         }
      }

      long interval = 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
      RaidSaveData.get(this.world).setNextRaidTick(this.world.getGameTime() + interval);
      this.setState(RaidSession.State.COMPLETED);
   }

   public synchronized boolean markRewardsDistributedIfNot() {
      if (this.rewardsDistributed) {
         return false;
      } else {
         this.rewardsDistributed = true;
         return true;
      }
   }

   public void startWaitingNow(long currentTick) {
      this.cleanup();
      this.rewardsDistributed = false;
      this.startBattleNow();
   }

   public void tick(long tick) {
      if (this.state == RaidSession.State.IDLE && !this.isAutoRaidEnabled()) {
         if (tick % 20L == 0L) {
            this.updateBossBarImmediate();
         }
      } else if (this.state == RaidSession.State.PAUSED) {
         this.updateBossBarInfo(0L, false);
      } else {
         if (!this.spawnDelays.isEmpty()) {
            Iterator<Entry<UUID, Integer>> it = this.spawnDelays.entrySet().iterator();
            while(it.hasNext()) {
               Entry<UUID, Integer> entry = it.next();
               int val = entry.getValue() - 1;
               if (val <= 0) {
                  this.executeStartBattle(entry.getKey());
                  it.remove();
               } else {
                  entry.setValue(val);
               }
            }
         }

         if (tick % 100L == 0L) {
            this.cleanStaleCopies();
         }

         long intervalTicks;
         switch(this.state) {
            case IDLE:
               long next = RaidSaveData.get(this.world).getNextRaidTick();
               long now = this.world.getGameTime();
               long remaining = next - now;
               if (tick % 20L == 0L) {
                  this.updateBossBarImmediate();
               }

               if (System.currentTimeMillis() - this.lastBroadcastTime > 5000L) {
                  StringTextComponent baseMsg;
                  IFormattableTextComponent clickMsg;
                  if (remaining >= 5980L && remaining <= 6020L) {
                     baseMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgSpawning5Min() + " ");
                     clickMsg = (new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgClickToWarp())).withStyle(Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(5636095)).withUnderlined(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/raid warp")).withHoverEvent(new HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, new StringTextComponent("§eClick to teleport to the Raid!"))));
                     this.world.getServer().getPlayerList().broadcastMessage(baseMsg.append(clickMsg), ChatType.SYSTEM, Util.NIL_UUID);
                     this.lastBroadcastTime = System.currentTimeMillis();
                  }

                  if (remaining >= 1180L && remaining <= 1220L) {
                     baseMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgSpawning1Min() + " ");
                     clickMsg = (new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgClickToWarp())).withStyle(Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(5636095)).withUnderlined(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/raid warp")).withHoverEvent(new HoverEvent(net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT, new StringTextComponent("§eClick to teleport to the Raid!"))));
                     this.world.getServer().getPlayerList().broadcastMessage(baseMsg.append(clickMsg), ChatType.SYSTEM, Util.NIL_UUID);
                     this.lastBroadcastTime = System.currentTimeMillis();
                  }
               }

               if (next == -1L || next < tick && next != 0L) {
                  RaidSaveData.get(this.world).setNextRaidTick(tick + 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds());
               } else if (tick >= next) {
                  this.startBattleNow();
               }
               break;
            case IN_BATTLE:
               int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
               long durationTicks = 20L * (long)PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff);
               long elapsed = tick - this.battleStartTick;
               long rem = durationTicks - elapsed;
               int intervalSeconds = PixelmonRaidConfig.getInstance().getHpBroadcastIntervalSeconds();
               if (intervalSeconds > 0) {
                  intervalTicks = (long)intervalSeconds * 20L;
                  if (tick % intervalTicks == 0L && tick != this.lastHpBroadcastTick) {
                     this.lastHpBroadcastTick = tick;
                     float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP) * 100.0F;
                     String color = pct > 50.0F ? "§a" : (pct > 20.0F ? "§e" : "§c");
                     String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdate().replace("%boss%", this.currentBossName).replace("%color%", color).replace("%hp%", String.valueOf(this.totalRaidHP)).replace("%maxhp%", String.valueOf(this.maxRaidHP)).replace("%pct%", String.format("%.1f", pct));
                     this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(msg), ChatType.SYSTEM, Util.NIL_UUID);
                  }
               }

               if (rem <= 0L) {
                  this.setState(RaidSession.State.SUDDEN_DEATH);
               } else {
                  this.bossTickLogic(tick);
                  this.updateBossBarInfo(rem, false);
               }
               break;
            case SUDDEN_DEATH:
               intervalTicks = 20L * (long)PixelmonRaidConfig.getInstance().getSuddenDeathDurationSeconds();
               long sdRem = intervalTicks - (tick - this.suddenDeathStartTick);
               int sdInterval = PixelmonRaidConfig.getInstance().getHpBroadcastIntervalSeconds();
               if (sdInterval > 0) {
                  long sIntervalTicks = (long)sdInterval * 20L;
                  if (tick % sIntervalTicks == 0L && tick != this.lastHpBroadcastTick) {
                     this.lastHpBroadcastTick = tick;
                     float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP) * 100.0F;
                     String color = pct > 50.0F ? "§a" : (pct > 20.0F ? "§e" : "§c");
                     String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdateSuddenDeath().replace("%boss%", this.currentBossName).replace("%color%", color).replace("%hp%", String.valueOf(this.totalRaidHP)).replace("%maxhp%", String.valueOf(this.maxRaidHP)).replace("%pct%", String.format("%.1f", pct));
                     this.world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(msg), ChatType.SYSTEM, Util.NIL_UUID);
                  }
               }

               if (sdRem <= 0L) {
                  this.finishRaid(false, (UUID)null);
               } else {
                  this.bossTickLogic(tick);
                  this.updateBossBarInfo(sdRem, true);
               }
               break;
            case COMPLETED:
               this.cleanup();
               RaidSaveData.get(this.world).setNextRaidTick(tick + 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds());
               this.setState(RaidSession.State.IDLE);
         }
      }
   }

   private void cleanStaleCopies() {
      if (!this.activeCopyUUIDs.isEmpty()) {
         Iterator<UUID> it = this.activeCopyUUIDs.iterator();
         while(it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = this.world.getEntity(uuid);
            if (entity == null) {
               it.remove();
            } else if (entity instanceof PixelmonEntity) {
               PixelmonEntity pe = (PixelmonEntity)entity;
               if (pe.battleController == null && pe.tickCount > 300) {
                  pe.remove();
                  it.remove();
               }
            }
         }
      }
   }

   private void bossTickLogic(long tick) {
      boolean alive = false;
      Entity e;
      if (this.templateUUID != null) {
         e = this.world.getEntity(this.templateUUID);
         if (e != null && e.isAlive()) {
            alive = true;
         }
      }

      if (!alive && !this.isSpawning) {
         BlockPos center = this.getCenter();
         if (this.world.isLoaded(center) && !RaidSpawner.spawnBoss(this)) {
         }
      }

      if (tick % 10L == 0L) {
         this.updateBossName();
      }

      if (tick % 20L == 0L) {
         this.updateActionBars();
         this.updateHologram();
      }

      if ((this.state == RaidSession.State.IN_BATTLE || this.state == RaidSession.State.SUDDEN_DEATH) && this.templateUUID != null) {
         e = this.world.getEntity(this.templateUUID);
         if (e instanceof PixelmonEntity) {
            PixelmonEntity pe = (PixelmonEntity)e;
            try {
               float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP);
               float targetHealth = Math.max(1.0F, pe.getMaxHealth() * pct);
               pe.setHealth(targetHealth);
               if (tick % 20L == 0L && pe.getPokemon() != null) {
                  pe.getPokemon().setHealth((int)Math.max(1.0F, (float)pe.getPokemon().getMaxHealth() * pct));
               }
            } catch (Throwable var8) {
            }
         }
      }

      if ((this.state == RaidSession.State.SUDDEN_DEATH || (double)this.totalRaidHP < (double)this.maxRaidHP * 0.25D) && this.templateUUID != null) {
         e = this.world.getEntity(this.templateUUID);
         if (e != null) {
            this.world.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 5.0D, e.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.05D);
         }
      }

      if (!this.hasEnraged && (double)this.totalRaidHP <= (double)this.maxRaidHP * 0.5D) {
         this.hasEnraged = true;
         this.world.getPlayers((p) -> true).forEach((p) -> {
            p.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgEnrage()), p.getUUID());
         });
         this.world.playSound((PlayerEntity)null, this.getCenter(), SoundEvents.ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 100.0F, 1.0F);
      }
   }

   public static enum State {
      IDLE,
      IN_BATTLE,
      SUDDEN_DEATH,
      COMPLETED,
      PAUSED;
   }
}