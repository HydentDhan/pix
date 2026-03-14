package com.PixelmonRaid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent.StartCapture;
import com.pixelmonmod.pixelmon.api.events.DropEvent;
import com.pixelmonmod.pixelmon.api.events.DynamaxEvent;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.api.events.MegaEvolutionEvent;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.Damage;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.Use;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.TurnEndEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;

public class PixelmonBossAttackListener {
   private static final Map<UUID, UUID> playerActiveRaidMap = new ConcurrentHashMap<>();
   private static final Map<UUID, List<ItemStack>> heldItemSnapshots = new ConcurrentHashMap<>();
   private static final List<String> OHKO_MOVES = Arrays.asList("guillotine", "fissure", "sheer cold", "horn drill");
   private static boolean registered = false;

   public static void register() {
      if (!registered) {
         Pixelmon.EVENT_BUS.register(PixelmonBossAttackListener.class);
         NeoForge.EVENT_BUS.register(PixelmonBossAttackListener.class);
         registered = true;
      }
   }

   public static void unregisterSessionTouched(UUID raidId) {}

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onPixelmonDrop(DropEvent event) {
      if (event.entity != null && event.entity.getPersistentData().getBoolean("pixelmonraid_boss")) {
         if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onPixelmonExp(ExperienceGainEvent event) {
      if (event.pokemon != null && event.pokemon.getEntity() != null && event.pokemon.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
         if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
         event.setExperience(0);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLivingDrops(LivingDropsEvent event) {
      if (event.getEntity() != null && event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLivingXP(LivingExperienceDropEvent event) {
      if (event.getEntity() != null && event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
         event.setCanceled(true);
         event.setDroppedExperience(0);
      }
   }

   @SubscribeEvent
   public static void onDynamax(DynamaxEvent.BattleEvolve.Pre event) {
      try {
         PixelmonWrapper wrapper = null;
         Class<?> clazz = event.getClass();
         while (clazz != null && wrapper == null) {
            try {
               java.lang.reflect.Field f = clazz.getDeclaredField("pw");
               f.setAccessible(true);
               wrapper = (PixelmonWrapper) f.get(event);
            } catch (Exception ex) {
               clazz = clazz.getSuperclass();
            }
         }

         if (wrapper != null && wrapper.getParticipant() instanceof PlayerParticipant pp) {
            if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
               if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
               pp.player.sendSystemMessage(Component.literal("§5§l[Raid] §dThe Boss's aura suppresses your Dynamax Band!"));
            }
         }
      } catch (Exception e) {}
   }

   @SubscribeEvent
   public static void onMegaEvolve(MegaEvolutionEvent.Battle.Pre event) {
      try {
         PixelmonWrapper wrapper = null;
         Class<?> clazz = event.getClass();
         while (clazz != null && wrapper == null) {
            try {
               java.lang.reflect.Field f = clazz.getDeclaredField("pw");
               f.setAccessible(true);
               wrapper = (PixelmonWrapper) f.get(event);
            } catch (Exception ex) {
               clazz = clazz.getSuperclass();
            }
         }

         if (wrapper != null && wrapper.getParticipant() instanceof PlayerParticipant pp) {
            if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
               if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
               pp.player.sendSystemMessage(Component.literal("§5§l[Raid] §dThe Boss's aura suppresses your Key Stone!"));
            }
         }
      } catch (Exception var2) {}
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttackUse(Use event) {
      try {
         if (event.user == null || event.target == null) return;
         boolean isBossTarget = false;
         if (event.user.getParticipant() instanceof PlayerParticipant pp) {
            if (playerActiveRaidMap.containsKey(pp.player.getUUID())) isBossTarget = true;
         }

         if (isBossTarget) {
            PixelmonWrapper attacker = event.user;
            boolean isBanned = false;
            String banReason = "";
            if (event.getAttack() != null && event.getAttack().getMove() != null) {
               String moveName = event.getAttack().getMove().getAttackName();
               String lowerName = moveName.toLowerCase();
               if (OHKO_MOVES.contains(lowerName)) {
                  if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
                  if (attacker.getParticipant() instanceof PlayerParticipant pp) {
                     pp.player.sendSystemMessage(Component.literal("§c§lIT MISSED! §7(OHKO Moves are Banned)"));
                  }
                  return;
               }
               if (moveName.startsWith("Max ") || moveName.startsWith("G-Max ")) {
                  isBanned = true;
                  banReason = "§cDynamax Moves are BANNED!";
               }
            }

            if (!isBanned) {
               if (attacker.getEntity() instanceof PixelmonEntity pe && pe.getPokemon().getDynamaxLevel() > 0) {
                  isBanned = true;
                  banReason = "§cDynamax is BANNED!";
               } else if (attacker.getEntity() instanceof PixelmonEntity pe && pe.getPokemon().isMega()) {
                  isBanned = true;
                  banReason = "§cMega Evolution is BANNED!";
               }
            }

            if (isBanned && attacker.getParticipant() instanceof PlayerParticipant pp) {
               pp.player.sendSystemMessage(Component.literal("§c§l✖ ATTACK NULLIFIED! ✖"));
               pp.player.sendSystemMessage(Component.literal(banReason));
               if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
            }
         }
      } catch (Throwable var7) {}
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttackDamage(Damage event) {
      try {
         if (event.target == null || event.user == null) return;
         BattleParticipant userParticipant = event.user.getParticipant();
         BattleParticipant targetParticipant = event.target.getParticipant();
         ServerPlayer player;
         UUID raidId;
         RaidSession session;

         // SAFEGUARD: If the boss hits itself in Confusion, cancel it entirely!
         if (userParticipant instanceof WildPixelmonParticipant && targetParticipant instanceof WildPixelmonParticipant wpTarget) {
            if (wpTarget instanceof RaidBossParticipant || (wpTarget.getEntity() != null && wpTarget.getEntity().getPersistentData().getBoolean("pixelmonraid_copy"))) {
               if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
               event.damage = 0.0D;
               return;
            }
         }

         if (userParticipant instanceof WildPixelmonParticipant wpUser && targetParticipant instanceof PlayerParticipant pp) {
            player = pp.player;
            raidId = null;
            if (wpUser instanceof RaidBossParticipant rbp) {
               raidId = rbp.getRaidId();
            } else if (playerActiveRaidMap.containsKey(player.getUUID())) {
               raidId = playerActiveRaidMap.get(player.getUUID());
            }

            if (raidId != null) {
               session = RaidSpawner.getSessionByRaidId(raidId);
               if (session != null) {
                  double multiplier = session.getState() == RaidSession.State.SUDDEN_DEATH ? 5.0D : 2.0D;
                  event.damage = (double)((float)(event.damage * multiplier));
               }
            }
            return;
         }

         if (userParticipant instanceof PlayerParticipant pp && targetParticipant instanceof WildPixelmonParticipant wpTarget) {
            player = pp.player;
            raidId = null;

            // FOOLPROOF EXTRACTION: Directly grab the ID from our custom class
            if (wpTarget instanceof RaidBossParticipant rbp) {
               raidId = rbp.getRaidId();
            } else if (playerActiveRaidMap.containsKey(player.getUUID())) {
               raidId = playerActiveRaidMap.get(player.getUUID());
            }

            if (raidId != null) {
               session = RaidSpawner.getSessionByRaidId(raidId);
               if (session != null && session.getPlayers().contains(player.getUUID())) {
                  if (event.getAttack() != null && event.getAttack().getMove() != null && OHKO_MOVES.contains(event.getAttack().getMove().getAttackName().toLowerCase())) {
                     if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
                     event.damage = 0.0D;
                     return;
                  }

                  if (session.isFatigued(player.getUUID())) {
                     event.damage = 0.0D;
                     return;
                  }

                  session.addPlayer(player.getUUID());
                  if (event.damage > 0.0D) {
                     int rawDmg = (int)Math.round(event.damage);
                     if (PixelmonRaidConfig.getInstance().isDynamicDifficulty()) {
                        int playerCount = session.getPlayers().size();
                        if (playerCount > 1) {
                           double scale = PixelmonRaidConfig.getInstance().getDifficultyScale();
                           double reduction = 1.0D + (double)(playerCount - 1) * scale;
                           rawDmg = (int)((double)rawDmg / reduction);
                        }
                     }

                     if (rawDmg > 10000) rawDmg = 10000;
                     if (session.getState() == RaidSession.State.SUDDEN_DEATH) rawDmg *= 5;

                     double capPercent = PixelmonRaidConfig.getInstance().getDamageCapPercentage();
                     int maxRaidHP = Math.max(1, session.getMaxRaidHP());
                     int limit = (int)((double)maxRaidHP * capPercent);
                     if (limit < 50) limit = 50;
                     if (rawDmg > limit) rawDmg = limit;
                     session.getDamageTracker().addDamage(player.getUUID(), rawDmg);
                     int newVal = Math.max(0, session.getTotalRaidHP() - rawDmg);
                     session.setTotalRaidHP(newVal);

                     try { session.broadcastPoolUpdate();
                     } catch (Throwable var18) {}

                     float hpPct = (float)newVal / (float)maxRaidHP * 100.0F;
                     player.sendSystemMessage(Component.literal("§d§lRaid Boss: §e" + newVal + "/" + maxRaidHP + " HP §7(" + String.format("%.1f", hpPct) + "%) §c[-" + rawDmg + "]"));
                     if (event.target.getEntity() != null && event.target.getEntity().level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, event.target.getEntity().getX(), event.target.getEntity().getY() + 1.0D, event.target.getEntity().getZ(), 3, 0.5D, 0.5D, 0.5D, 0.05D);
                     }

                     // THE ULTIMATE SHIELD: Cancel physical damage so it doesn't hurt the physical copy!
                     if (event instanceof net.neoforged.bus.api.ICancellableEvent c) {
                        c.setCanceled(true);
                     }
                     event.damage = 0.0D;
                     // Properly cast to PixelmonEntity first to get the Pokemon
                     if (event.target != null && event.target.getEntity() instanceof PixelmonEntity pe) {
                        if (pe.getPokemon() != null) {
                           pe.getPokemon().heal();
                        }
                     }

                     if (newVal <= 0) {
                        try {
                           session.finishRaid(true, player.getUUID());
                           session.broadcastPoolUpdate();
                        } catch (Throwable var17) {}
                     }
                  }
               }
            }
         }
      } catch (Throwable var19) {}
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLivingDeath(LivingDeathEvent event) {
      try {
         if (event.getEntity() == null || event.getEntity().level().isClientSide) return;
         if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") || event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
            event.setCanceled(true);
            if (event.getEntity() instanceof LivingEntity le) {
               le.setHealth(le.getMaxHealth());
            }

            if (event.getEntity() instanceof PixelmonEntity pe) {
               if (pe.getPokemon() != null) {
                  pe.getPokemon().heal();
               }
            }
         }
      } catch (Throwable var2) {}
   }

   @SubscribeEvent
   public static void onBattleStarted(BattleStartedEvent event) {
      try {
         boolean isRaid = false;
         UUID raidId = null;
         ServerPlayer player = null;
         Iterator<BattleParticipant> var4 = event.getBattleController().participants.iterator();
         while(var4.hasNext()) {
            BattleParticipant bp = var4.next();
            if (bp instanceof WildPixelmonParticipant wp) {
               Entity entity = bp.getEntity();

               if (wp instanceof RaidBossParticipant rbp) {
                  isRaid = true;
                  raidId = rbp.getRaidId();
               } else if (entity != null && entity.getPersistentData().contains("pixelmonraid_raidId")) {
                  isRaid = true;
                  raidId = entity.getPersistentData().getUUID("pixelmonraid_raidId");
               }

               if (isRaid) {
                  RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
                  if (session != null && entity != null) {
                     session.registerCopy(entity.getUUID());
                  }

                  // NEW: God-Mode HP Injection!
                  for (PixelmonWrapper wrapper : wp.allPokemon) {
                     try {
                        java.lang.reflect.Field maxHpField = PixelmonWrapper.class.getDeclaredField("maxHP");
                        maxHpField.setAccessible(true);
                        maxHpField.set(wrapper, 999999);
                        wrapper.setHealth(999999);
                     } catch (Exception ex) {}
                  }
               }
            }
            if (bp instanceof PlayerParticipant pp) {
               player = pp.player;
            }
         }

         if (isRaid && player != null) {
            if (raidId != null) {
               playerActiveRaidMap.put(player.getUUID(), raidId);
            }
            List<ItemStack> items = new ArrayList<>();
            Pokemon[] var11 = StorageProxy.getPartyNow(player).getAll();
            for (Pokemon p : var11) {
               if (p != null) {
                  items.add(p.getHeldItem().copy());
               } else {
                  items.add(ItemStack.EMPTY);
               }
            }
            heldItemSnapshots.put(player.getUUID(), items);
         }
      } catch (Throwable var9) {}
   }

   @SubscribeEvent
   public static void onBattleEnd(BattleEndEvent event) {
      try {
         Set<UUID> playersInBattle = new HashSet<>();
         Iterator<BattleParticipant> var2 = event.getBattleController().participants.iterator();

         while(var2.hasNext()) {
            BattleParticipant bp = var2.next();
            if (bp instanceof PlayerParticipant pp) {
               playersInBattle.add(pp.player.getUUID());
            }

            if (bp instanceof WildPixelmonParticipant) {
               PixelmonEntity entity = (PixelmonEntity)bp.getEntity();
               if (entity != null && entity.level() instanceof ServerLevel) {
                  UUID rid = null;
                  if (bp instanceof RaidBossParticipant rbp) {
                     rid = rbp.getRaidId();
                  } else if (entity.getPersistentData().contains("pixelmonraid_raidId")) {
                     rid = entity.getPersistentData().getUUID("pixelmonraid_raidId");
                  }

                  if (rid != null) {
                     RaidSession session = RaidSpawner.getSessionByRaidId(rid);
                     if (session != null && session.isCopy(entity.getUUID())) {
                        session.unregisterCopy(entity.getUUID());
                        entity.setPos(entity.getX(), -1000.0D, entity.getZ());
                        entity.getServer().execute(() -> {
                           if (entity.isAlive()) {
                              entity.remove(Entity.RemovalReason.DISCARDED);
                           }
                        });
                     }
                  }
               }
            }
         }

         for(UUID pid : playersInBattle) {
            if (playerActiveRaidMap.containsKey(pid)) {
               UUID raidId = playerActiveRaidMap.remove(pid);
               RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
               if (session != null) {
                  session.applyRejoinCooldown(pid);
               }
            }
            if(heldItemSnapshots.containsKey(pid)) {
               ServerPlayer p = event.getBattleController().participants.get(0).getEntity().level().getServer().getPlayerList().getPlayer(pid);
               if (p != null) {
                  List<ItemStack> saved = heldItemSnapshots.remove(pid);
                  if (saved != null) {
                     PlayerPartyStorage party = StorageProxy.getPartyNow(p);
                     Pokemon[] all = party.getAll();
                     for(int i = 0; i < all.length && i < saved.size(); ++i) {
                        if (all[i] != null) {
                           all[i].setHeldItem(saved.get(i));
                           party.set(i, all[i]);
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable var9) {}
   }

   @SubscribeEvent
   public static void onTurnEnd(TurnEndEvent event) {
      try {
         if (event.getBattleController() != null) {
            for (BattleParticipant bp : event.getBattleController().participants) {
               if (bp instanceof WildPixelmonParticipant wp) {
                  boolean isBoss = (wp instanceof RaidBossParticipant);
                  for (PixelmonWrapper wrapper : wp.allPokemon) {
                     if (isBoss || (wrapper.getEntity() != null && wrapper.getEntity().getPersistentData().getBoolean("pixelmonraid_copy"))) {
                        // Restore HP and wipe Confusion, Poison, Burn, and Leech Seed!
                        wrapper.setHealth(wrapper.getMaxHealth());
                        wrapper.clearStatus();
                        if (wrapper.getEntity() instanceof PixelmonEntity pe && pe.getPokemon() != null) {
                           pe.getPokemon().heal();
                        }
                     }
                  }
               }
            }
         }
      } catch (Exception e) {}
   }

   @SubscribeEvent
   public static void onLivingHurt(LivingDamageEvent.Pre event) {
      try {
         if (event.getEntity() == null || event.getEntity().level().isClientSide) return;
         if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") || event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
            event.setNewDamage(0.0F);
            if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
         }
      } catch (Throwable var2) {}
   }

   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      try {
         if (event.getLevel().isClientSide) return;
         if (event.getTarget() == null || !(event.getEntity() instanceof ServerPlayer)) return;

         if (event.getTarget().getPersistentData().getBoolean("pixelmonraid_template")) {
            ServerPlayer player = (ServerPlayer)event.getEntity();
            Entity target = event.getTarget();
            ServerLevel world = (ServerLevel)event.getLevel();
            RaidSession session = null;
            if (target.getPersistentData().contains("pixelmonraid_raidId")) {
               session = RaidSpawner.getSessionByRaidId(target.getPersistentData().getUUID("pixelmonraid_raidId"));
            }
            if (session == null) {
               session = RaidSpawner.getSessionSafe(world);
            }

            if (session != null) {
               if (!session.isTemplate(target.getUUID())) {
                  session.registerTemplate(target.getUUID());
               }

               event.setCanceled(true);
               if (session.getState() != RaidSession.State.IN_BATTLE && session.getState() != RaidSession.State.SUDDEN_DEATH) {
                  player.sendSystemMessage(Component.literal("§cRaid is not active currently."));
               } else {
                  player.sendSystemMessage(Component.literal("§c⚠ Do not click the boss! Use §e/raid join §cto enter the battle!"));
               }
            }
         }
      } catch (Throwable var5) {}
   }

   @SubscribeEvent
   public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
      try {
         if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel)) return;
         if (!event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) return;

         if (!((ServerLevel)event.getLevel()).dimension().location().toString().equals("minecraft:overworld")) {
            event.getEntity().remove(Entity.RemovalReason.DISCARDED);
            return;
         }

         UUID raidId = null;
         if (event.getEntity().getPersistentData().contains("pixelmonraid_raidId")) {
            raidId = event.getEntity().getPersistentData().getUUID("pixelmonraid_raidId");
         }

         if (raidId == null) return;

         RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
         if (session == null) return;

         if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template")) {
            session.registerTemplate(event.getEntity().getUUID());
         } else if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
            if (session.getState() == RaidSession.State.IDLE) {
               event.getEntity().remove(Entity.RemovalReason.DISCARDED);
            } else {
               session.registerCopy(event.getEntity().getUUID());
            }
         }
      } catch (Throwable var4) {}
   }

   @SubscribeEvent
   public static void onCaptureAttempt(StartCapture event) {
      try {
         if (event.getPokemon() != null && event.getPokemon().getEntity() != null && event.getPokemon().getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
            if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
            if (event.getPlayer() != null) {
               event.getPlayer().sendSystemMessage(Component.literal("§c§l✖ RAID BOSS PROTECTED ✖"));
            }
         }
      } catch (Throwable var2) {}
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDamageDealt(com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.DamageDealt event) {
      try {
         if (event.target != null && event.target.getParticipant() instanceof RaidBossParticipant) {
            event.target.setHealth(event.target.getMaxHealth());
            if (event.target.getEntity() instanceof PixelmonEntity pe && pe.getPokemon() != null) {
               pe.getPokemon().heal();
            }
         }
      } catch (Exception e) {}
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onRecoil(com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.Recoil event) {
      try {
         if (event.user != null && event.user.getParticipant() instanceof RaidBossParticipant) {
            event.user.setHealth(event.user.getMaxHealth());
            if (event.user.getEntity() instanceof PixelmonEntity pe && pe.getPokemon() != null) {
               pe.getPokemon().heal();
            }
         }
      } catch (Exception e) {}
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onFaintPre(com.pixelmonmod.pixelmon.api.events.PixelmonFaintEvent.Pre event) {
      try {
         if (event.getPokemon() != null && event.getPokemon().getEntity() != null) {
            if (event.getPokemon().getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
               // The game is trying to kill the physical copy. We intercept and cancel it!
               if (event instanceof net.neoforged.bus.api.ICancellableEvent c) {
                  c.setCanceled(true);
               }

               // Instantly revive and fully heal it back to max
               event.getPokemon().heal();
               if (event.getPokemon().getEntity() instanceof net.minecraft.world.entity.LivingEntity le) {
                  le.setHealth(le.getMaxHealth());
               }
            }
         }
      } catch (Exception e) {}
   }
}