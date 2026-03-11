package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.DropEvent;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent.StartCapture;
import com.pixelmonmod.pixelmon.api.events.DynamaxEvent.BattleEvolve;
import com.pixelmonmod.pixelmon.api.events.MegaEvolutionEvent.Battle;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.TurnEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.Damage;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent.Use;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PixelmonBossAttackListener {
   private static final Map<UUID, UUID> playerActiveRaidMap = new ConcurrentHashMap();
   private static final Map<UUID, List<ItemStack>> heldItemSnapshots = new ConcurrentHashMap();
   private static final List<String> OHKO_MOVES = Arrays.asList("guillotine", "fissure", "sheer cold", "horn drill");
   private static boolean registered = false;

   public static void register() {
      if (!registered) {
         Pixelmon.EVENT_BUS.register(PixelmonBossAttackListener.class);
         MinecraftForge.EVENT_BUS.register(PixelmonBossAttackListener.class);
         registered = true;
      }
   }

   public static void unregisterSessionTouched(UUID raidId) {}

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onPixelmonDrop(DropEvent event) {
      if (event.entity != null && event.entity.getPersistentData().getBoolean("pixelmonraid_boss")) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onPixelmonExp(ExperienceGainEvent event) {
      if (event.pokemon != null && event.pokemon.getEntity() != null && event.pokemon.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
         event.setCanceled(true);
         event.setExperience(0);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLivingDrops(LivingDropsEvent event) {
      if (event.getEntity() != null && event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
         event.setCanceled(true);
         event.getDrops().clear();
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
   public static void onDynamax(BattleEvolve event) {
      if (event.pw != null && event.pw.getParticipant() instanceof PlayerParticipant) {
         PlayerParticipant pp = (PlayerParticipant)event.pw.getParticipant();
         if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
            event.setCanceled(true);
            pp.player.sendMessage(new StringTextComponent("§5§l[Raid] §dThe Boss's aura suppresses your Dynamax Band!"), Util.NIL_UUID);
         }
      }
   }

   @SubscribeEvent
   public static void onMegaEvolve(Battle event) {
      try {
         if (event.getPixelmonWrapper() != null && event.getPixelmonWrapper().getParticipant() instanceof PlayerParticipant) {
            PlayerParticipant pp = (PlayerParticipant)event.getPixelmonWrapper().getParticipant();
            if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
               event.setCanceled(true);
               pp.player.sendMessage(new StringTextComponent("§5§l[Raid] §dThe Boss's aura suppresses your Key Stone!"), Util.NIL_UUID);
            }
         }
      } catch (Exception var2) {}
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttackUse(Use event) {
      try {
         if (event.user == null || event.target == null) return;
         boolean isBossTarget = false;
         if (event.user.getParticipant() instanceof PlayerParticipant) {
            ServerPlayerEntity p = ((PlayerParticipant)event.user.getParticipant()).player;
            if (playerActiveRaidMap.containsKey(p.getUUID())) isBossTarget = true;
         }

         if (isBossTarget) {
            PixelmonWrapper attacker = event.user;
            boolean isBanned = false;
            String banReason = "";
            if (event.getAttack() != null && event.getAttack().getMove() != null) {
               String moveName = event.getAttack().getMove().getAttackName();
               String lowerName = moveName.toLowerCase();
               if (OHKO_MOVES.contains(lowerName)) {
                  event.setCanceled(true);
                  if (attacker.getParticipant() instanceof PlayerParticipant) {
                     ((PlayerParticipant)attacker.getParticipant()).player.sendMessage(new StringTextComponent("§c§lIT MISSED! §7(OHKO Moves are Banned)"), Util.NIL_UUID);
                  }
                  return;
               }
               if (moveName.startsWith("Max ") || moveName.startsWith("G-Max ")) {
                  isBanned = true;
                  banReason = "§cDynamax Moves are BANNED!";
               }
            }

            if (!isBanned) {
               if (attacker.isDynamax > 0) {
                  isBanned = true;
                  banReason = "§cDynamax is BANNED!";
               } else if (attacker.entity instanceof PixelmonEntity && attacker.entity.getPokemon().isMega()) {
                  isBanned = true;
                  banReason = "§cMega Evolution is BANNED!";
               }
            }

            if (isBanned && attacker.getParticipant() instanceof PlayerParticipant) {
               PlayerParticipant pp = (PlayerParticipant)attacker.getParticipant();
               pp.player.sendMessage(new StringTextComponent("§c§l✖ ATTACK NULLIFIED! ✖"), Util.NIL_UUID);
               pp.player.sendMessage(new StringTextComponent(banReason), Util.NIL_UUID);
               event.setCanceled(true);
            }
         }
      } catch (Throwable var7) { var7.printStackTrace(); }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttackDamage(Damage event) {
      try {
         if (event.target == null || event.user == null) return;

         BattleParticipant userParticipant = event.user.getParticipant();
         BattleParticipant targetParticipant = event.target.getParticipant();
         ServerPlayerEntity player;
         UUID raidId;
         RaidSession session;

         if (userParticipant instanceof WildPixelmonParticipant && targetParticipant instanceof PlayerParticipant) {
            player = ((PlayerParticipant)targetParticipant).player;
            if (playerActiveRaidMap.containsKey(player.getUUID())) {
               raidId = (UUID)playerActiveRaidMap.get(player.getUUID());
               session = RaidSpawner.getSessionByRaidId(raidId);
               if (session != null) {
                  double multiplier = session.getState() == RaidSession.State.SUDDEN_DEATH ? 5.0D : 2.0D;
                  event.damage = (double)((float)(event.damage * multiplier));
               }
            }
            return;
         }

         if (userParticipant instanceof PlayerParticipant && targetParticipant instanceof WildPixelmonParticipant) {
            player = ((PlayerParticipant)userParticipant).player;
            if (playerActiveRaidMap.containsKey(player.getUUID())) {
               raidId = (UUID)playerActiveRaidMap.get(player.getUUID());
               session = RaidSpawner.getSessionByRaidId(raidId);
               if (session != null && session.getPlayers().contains(player.getUUID())) {
                  if (event.getAttack() != null && event.getAttack().getMove() != null && OHKO_MOVES.contains(event.getAttack().getMove().getAttackName().toLowerCase())) {
                     event.setCanceled(true);
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

                     try { session.broadcastPoolUpdate(); } catch (Throwable var18) {}

                     float hpPct = (float)newVal / (float)maxRaidHP * 100.0F;
                     player.sendMessage(new StringTextComponent("§d§lRaid Boss: §e" + newVal + "/" + maxRaidHP + " HP §7(" + String.format("%.1f", hpPct) + "%) §c[-" + rawDmg + "]"), ChatType.GAME_INFO, Util.NIL_UUID);

                     if (event.target.entity != null && event.target.entity.level instanceof ServerWorld) {
                        ((ServerWorld)event.target.entity.level).sendParticles(ParticleTypes.DAMAGE_INDICATOR, event.target.entity.getX(), event.target.entity.getY() + 1.0D, event.target.entity.getZ(), 3, 0.5D, 0.5D, 0.5D, 0.05D);
                     }

                     float damagePercentage = (float)rawDmg / (float)maxRaidHP;
                     float localMaxHp = 100.0F;
                     if (event.target.entity instanceof LivingEntity) {
                        localMaxHp = event.target.entity.getMaxHealth();
                     }

                     float fakeScaledDamage = localMaxHp * damagePercentage;
                     if (newVal > 0 && event.target.entity instanceof LivingEntity) {
                        float currentLocalHp = event.target.entity.getHealth();
                        if (fakeScaledDamage >= currentLocalHp) {
                           fakeScaledDamage = Math.max(0.1F, currentLocalHp - 1.0F);
                        }
                     }

                     event.damage = (double)fakeScaledDamage;
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
      } catch (Throwable var19) { var19.printStackTrace(); }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLivingDeath(LivingDeathEvent event) {
      try {
         if (event.getEntity() == null || event.getEntity().level.isClientSide) return;

         if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") || event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
            event.setCanceled(true);
            if (event.getEntity() instanceof LivingEntity) {
               ((LivingEntity)event.getEntity()).setHealth(((LivingEntity)event.getEntity()).getMaxHealth());
            }

            if (event.getEntity() instanceof PixelmonEntity) {
               PixelmonEntity pe = (PixelmonEntity)event.getEntity();
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
         ServerPlayerEntity player = null;
         Iterator var4 = event.getBattleController().participants.iterator();

         while(var4.hasNext()) {
            BattleParticipant bp = (BattleParticipant)var4.next();
            if (bp instanceof WildPixelmonParticipant) {
               Entity entity = bp.getEntity();
               if (entity != null && entity.getPersistentData().contains("pixelmonraid_raidId")) {
                  isRaid = true;
                  raidId = entity.getPersistentData().getUUID("pixelmonraid_raidId");
                  RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
                  if (session != null) {
                     session.registerCopy(entity.getUUID());
                  }
               }
            }

            if (bp instanceof PlayerParticipant) {
               player = ((PlayerParticipant)bp).player;
            }
         }

         if (isRaid && player != null) {
            if (raidId != null) {
               playerActiveRaidMap.put(player.getUUID(), raidId);
            }

            List<ItemStack> items = new ArrayList();
            Pokemon[] var11 = StorageProxy.getParty(player).getAll();
            int var12 = var11.length;

            for(int var13 = 0; var13 < var12; ++var13) {
               Pokemon p = var11[var13];
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
         Set<UUID> playersInBattle = new HashSet();
         Iterator var2 = event.getBattleController().participants.iterator();

         while(var2.hasNext()) {
            BattleParticipant bp = (BattleParticipant)var2.next();
            if (bp instanceof PlayerParticipant) {
               playersInBattle.add(((PlayerParticipant)bp).player.getUUID());
            }

            if (bp instanceof WildPixelmonParticipant) {
               PixelmonEntity entity = (PixelmonEntity)bp.getEntity();
               if (entity != null && entity.level instanceof ServerWorld && entity.getPersistentData().contains("pixelmonraid_raidId")) {
                  UUID rid = entity.getPersistentData().getUUID("pixelmonraid_raidId");
                  RaidSession session = RaidSpawner.getSessionByRaidId(rid);
                  if (session != null && session.isCopy(entity.getUUID())) {
                     session.unregisterCopy(entity.getUUID());
                     entity.setPos(entity.getX(), -1000.0D, entity.getZ());
                     entity.getServer().execute(() -> {
                        if (entity.isAlive()) {
                           entity.remove();
                        }
                     });
                  }
               }
            }
         }

         var2 = playersInBattle.iterator();
         while(true) {
            ServerPlayerEntity p;
            List saved;
            do {
               UUID pid;
               do {
                  do {
                     if (!var2.hasNext()) return;
                     pid = (UUID)var2.next();
                     if (playerActiveRaidMap.containsKey(pid)) {
                        UUID raidId = (UUID)playerActiveRaidMap.remove(pid);
                        RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
                        if (session != null) {
                           session.applyRejoinCooldown(pid);
                        }
                     }
                  } while(!heldItemSnapshots.containsKey(pid));
                  p = ((BattleParticipant)event.getBattleController().participants.get(0)).getEntity().level.getServer().getPlayerList().getPlayer(pid);
               } while(p == null);

               saved = (List)heldItemSnapshots.remove(pid);
            } while(saved == null);

            PlayerPartyStorage party = StorageProxy.getParty(p);
            Pokemon[] all = party.getAll();

            for(int i = 0; i < all.length && i < saved.size(); ++i) {
               if (all[i] != null) {
                  all[i].setHeldItem((ItemStack)saved.get(i));
                  party.set(i, all[i]);
               }
            }
         }
      } catch (Throwable var9) {}
   }

   @SubscribeEvent
   public static void onTurnEnd(TurnEndEvent event) {
      try {
         BattleController bc = event.getBattleController();
         if (bc == null) return;
      } catch (Throwable var2) {}
   }

   @SubscribeEvent
   public static void onLivingHurt(LivingHurtEvent event) {
      try {
         if (event.getEntity() == null || event.getEntity().level.isClientSide) return;
         if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") || event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
            event.setCanceled(true);
            event.setAmount(0.0F);
         }
      } catch (Throwable var2) {}
   }

   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      try {
         if (event.getWorld().isClientSide) return;
         if (event.getTarget() == null || !(event.getPlayer() instanceof ServerPlayerEntity)) return;

         if (event.getTarget().getPersistentData().getBoolean("pixelmonraid_template")) {
            ServerPlayerEntity player = (ServerPlayerEntity)event.getPlayer();
            Entity target = event.getTarget();
            ServerWorld world = (ServerWorld)event.getWorld();
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
                  player.sendMessage(new StringTextComponent("§cRaid is not active currently."), Util.NIL_UUID);
               } else {
                  player.sendMessage(new StringTextComponent("§c⚠ Do not click the boss! Use §e/raid join §cto enter the battle!"), Util.NIL_UUID);
               }
            }
         }
      } catch (Throwable var5) {}
   }

   @SubscribeEvent
   public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
      try {
         if (event.getWorld().isClientSide || !(event.getWorld() instanceof ServerWorld)) return;
         if (!event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) return;

         if (!((ServerWorld)event.getWorld()).dimension().location().toString().equals("minecraft:overworld")) {
            event.getEntity().remove();
            return;
         }

         ServerWorld world = (ServerWorld)event.getWorld();
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
               event.getEntity().remove();
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
            event.setCanceled(true);
            if (event.getPlayer() != null) {
               event.getPlayer().sendMessage(new StringTextComponent("§c§l✖ RAID BOSS PROTECTED ✖"), Util.NIL_UUID);
            }
         }
      } catch (Throwable var2) {}
   }
}