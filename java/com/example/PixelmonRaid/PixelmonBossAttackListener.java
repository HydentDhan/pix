package com.example.PixelmonRaid;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.DropEvent;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.api.events.battles.AttackEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleStartedEvent;
import com.pixelmonmod.pixelmon.api.events.battles.BattleEndEvent;
import com.pixelmonmod.pixelmon.api.events.battles.TurnEndEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.DynamaxEvent;
import com.pixelmonmod.pixelmon.api.events.MegaEvolutionEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.particles.ParticleTypes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PixelmonBossAttackListener {

    private static final Map<UUID, UUID> playerActiveRaidMap = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ItemStack>> heldItemSnapshots = new ConcurrentHashMap<>();
    private static final List<String> OHKO_MOVES = Arrays.asList("guillotine", "fissure", "sheer cold", "horn drill");
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        Pixelmon.EVENT_BUS.register(PixelmonBossAttackListener.class);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(PixelmonBossAttackListener.class);
        registered = true;
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
        if (event.pokemon != null && event.pokemon.getEntity() != null) {
            if (event.pokemon.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
                event.setCanceled(true);
                event.setExperience(0);
            }
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
    public static void onDynamax(DynamaxEvent.BattleEvolve event) {
        if (event.pw != null && event.pw.getParticipant() instanceof PlayerParticipant) {
            PlayerParticipant pp = (PlayerParticipant) event.pw.getParticipant();
            if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
                event.setCanceled(true);
                pp.player.sendMessage(new StringTextComponent("§5§l[Raid] §dThe Boss's aura suppresses your Dynamax Band!"), Util.NIL_UUID);
            }
        }
    }

    @SubscribeEvent
    public static void onMegaEvolve(MegaEvolutionEvent.Battle event) {
        try {
            if (event.getPixelmonWrapper() != null && event.getPixelmonWrapper().getParticipant() instanceof PlayerParticipant) {
                PlayerParticipant pp = (PlayerParticipant) event.getPixelmonWrapper().getParticipant();
                if (playerActiveRaidMap.containsKey(pp.player.getUUID())) {
                    event.setCanceled(true);
                    pp.player.sendMessage(new StringTextComponent("§5§l[Raid] §dThe Boss's aura suppresses your Key Stone!"), Util.NIL_UUID);
                }
            }
        } catch (Exception e) {}
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackUse(AttackEvent.Use event) {
        try {
            if (event.user == null || event.target == null) return;

            boolean isBossTarget = false;

            if (event.user.getParticipant() instanceof PlayerParticipant) {
                ServerPlayerEntity p = ((PlayerParticipant)event.user.getParticipant()).player;
                if (playerActiveRaidMap.containsKey(p.getUUID())) {
                    isBossTarget = true;
                }
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
                            ((PlayerParticipant) attacker.getParticipant()).player.sendMessage(new StringTextComponent("§c§lIT MISSED! §7(OHKO Moves are Banned)"), Util.NIL_UUID);
                        }
                        return;
                    }
                    if (moveName.startsWith("Max ") || moveName.startsWith("G-Max ")) {
                        isBanned = true; banReason = "§cDynamax Moves are BANNED!";
                    }
                }

                if (!isBanned) {
                    if (attacker.isDynamax > 0) { isBanned = true; banReason = "§cDynamax is BANNED!"; }
                    else if (attacker.entity instanceof PixelmonEntity) {
                        if (((PixelmonEntity)attacker.entity).getPokemon().isMega()) {
                            isBanned = true; banReason = "§cMega Evolution is BANNED!";
                        }
                    }
                }

                if (isBanned) {
                    if (attacker.getParticipant() instanceof PlayerParticipant) {
                        PlayerParticipant pp = (PlayerParticipant) attacker.getParticipant();
                        pp.player.sendMessage(new StringTextComponent("§c§l✖ ATTACK NULLIFIED! ✖"), Util.NIL_UUID);
                        pp.player.sendMessage(new StringTextComponent(banReason), Util.NIL_UUID);
                        event.setCanceled(true);
                    }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackDamage(AttackEvent.Damage event) {
        try {
            if (event.target == null || event.user == null) return;

            BattleParticipant userParticipant = event.user.getParticipant();
            BattleParticipant targetParticipant = event.target.getParticipant();

            if (userParticipant instanceof WildPixelmonParticipant && targetParticipant instanceof PlayerParticipant) {
                ServerPlayerEntity player = ((PlayerParticipant) targetParticipant).player;

                if (playerActiveRaidMap.containsKey(player.getUUID())) {
                    UUID raidId = playerActiveRaidMap.get(player.getUUID());
                    RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
                    if (session != null) {
                        double multiplier = (session.getState() == RaidSession.State.SUDDEN_DEATH) ? 5.0 : 2.0;
                        event.damage = (float)(event.damage * multiplier);
                    }
                }
                return;
            }

            
            if (userParticipant instanceof PlayerParticipant && targetParticipant instanceof WildPixelmonParticipant) {
                ServerPlayerEntity player = ((PlayerParticipant) userParticipant).player;

                
                if (playerActiveRaidMap.containsKey(player.getUUID())) {
                    UUID raidId = playerActiveRaidMap.get(player.getUUID());
                    RaidSession session = RaidSpawner.getSessionByRaidId(raidId);

                    if (session != null && session.getPlayers().contains(player.getUUID())) {
                        if (event.getAttack() != null && event.getAttack().getMove() != null) {
                            if (OHKO_MOVES.contains(event.getAttack().getMove().getAttackName().toLowerCase())) {
                                event.setCanceled(true);
                                event.damage = 0;
                                return;
                            }
                        }

                        if (session.isFatigued(player.getUUID())) {
                            event.damage = 0;
                            return;
                        }

                        session.addPlayer(player.getUUID());
                        if (event.damage > 0) {
                            int rawDmg = (int) Math.round(event.damage);
                            if (PixelmonRaidConfig.getInstance().isDynamicDifficulty()) {
                                int playerCount = session.getPlayers().size();
                                if (playerCount > 1) {
                                    double scale = PixelmonRaidConfig.getInstance().getDifficultyScale();
                                    double reduction = 1.0 + ((playerCount - 1) * scale);
                                    rawDmg = (int) (rawDmg / reduction);
                                }
                            }
                            if (rawDmg > 10000) rawDmg = 10000;
                            if (session.getState() == RaidSession.State.SUDDEN_DEATH) rawDmg *= 5;

                            double capPercent = PixelmonRaidConfig.getInstance().getDamageCapPercentage();
                            int maxRaidHP = Math.max(1, session.getMaxRaidHP());
                            int limit = (int) (maxRaidHP * capPercent);
                            if (limit < 50) limit = 50;
                            if (rawDmg > limit) rawDmg = limit;

                            session.getDamageTracker().addDamage(player.getUUID(), rawDmg);
                            int newVal = Math.max(0, session.getTotalRaidHP() - rawDmg);
                            session.setTotalRaidHP(newVal);

                            try { session.broadcastPoolUpdate(); } catch (Throwable ex) {}

                            float hpPct = (float)newVal / (float)maxRaidHP * 100f;
                            player.sendMessage(new StringTextComponent("§d§lRaid Boss: §e" + newVal + "/" + maxRaidHP + " HP §7(" + String.format("%.1f", hpPct) + "%) §c[-" + rawDmg + "]"), ChatType.GAME_INFO, Util.NIL_UUID);

                            if (event.target.entity != null && event.target.entity.level instanceof ServerWorld) {
                                ((ServerWorld) event.target.entity.level).sendParticles(ParticleTypes.EXPLOSION,
                                        event.target.entity.getX(), event.target.entity.getY() + 1, event.target.entity.getZ(),
                                        3, 0.5, 0.5, 0.5, 0.05);
                            }

                            if (newVal <= 0) {
                                try {
                                    session.finishRaid(true, player.getUUID());
                                    session.broadcastPoolUpdate();
                                } catch (Throwable ex) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity() == null || event.getEntity().getCommandSenderWorld().isClientSide) return;
            if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") ||
                    event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {

                event.setCanceled(true);
                if (event.getEntity() instanceof LivingEntity) {
                    ((LivingEntity) event.getEntity()).setHealth(((LivingEntity) event.getEntity()).getMaxHealth());
                }
                if (event.getEntity() instanceof PixelmonEntity) {
                    PixelmonEntity pe = (PixelmonEntity) event.getEntity();
                    if (pe.getPokemon() != null) pe.getPokemon().heal();
                }
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onBattleStarted(BattleStartedEvent event) {
        try {
            boolean isRaid = false;
            UUID raidId = null;
            ServerPlayerEntity player = null;

            for (BattleParticipant bp : event.getBattleController().participants) {
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
                if (bp instanceof PlayerParticipant) player = ((PlayerParticipant) bp).player;
            }

            if (isRaid && player != null) {
                
                if (raidId != null) playerActiveRaidMap.put(player.getUUID(), raidId);
                List<ItemStack> items = new ArrayList<>();
                for(Pokemon p : com.pixelmonmod.pixelmon.api.storage.StorageProxy.getParty(player).getAll()) {
                    if(p != null) items.add(p.getHeldItem().copy());
                    else items.add(ItemStack.EMPTY);
                }
                heldItemSnapshots.put(player.getUUID(), items);
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onBattleEnd(BattleEndEvent event) {
        try {
            Set<UUID> playersInBattle = new HashSet<>();
            for (BattleParticipant bp : event.getBattleController().participants) {
                if (bp instanceof PlayerParticipant) {
                    playersInBattle.add(((PlayerParticipant) bp).player.getUUID());
                }
                if (bp instanceof WildPixelmonParticipant) {
                    PixelmonEntity entity = (PixelmonEntity) bp.getEntity();
                    if (entity != null && entity.level instanceof ServerWorld) {
                        if (entity.getPersistentData().contains("pixelmonraid_raidId")) {
                            UUID rid = entity.getPersistentData().getUUID("pixelmonraid_raidId");
                            RaidSession session = RaidSpawner.getSessionByRaidId(rid);
                            if (session != null && session.isCopy(entity.getUUID())) {
                                session.unregisterCopy(entity.getUUID());
                                entity.setPos(entity.getX(), -1000, entity.getZ());
                                entity.getServer().execute(() -> { if (entity.isAlive()) entity.remove(); });
                            }
                        }
                    }
                }
            }

            for (UUID pid : playersInBattle) {
                
                if (playerActiveRaidMap.containsKey(pid)) {
                    UUID raidId = playerActiveRaidMap.remove(pid);
                    RaidSession session = RaidSpawner.getSessionByRaidId(raidId);
                    if (session != null) {
                        session.applyRejoinCooldown(pid);
                    }
                }

                if (heldItemSnapshots.containsKey(pid)) {
                    ServerPlayerEntity p = event.getBattleController().participants.get(0).getEntity().level.getServer().getPlayerList().getPlayer(pid);
                    if (p != null) {
                        List<ItemStack> saved = heldItemSnapshots.remove(pid);
                        if (saved != null) {
                            com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage party = com.pixelmonmod.pixelmon.api.storage.StorageProxy.getParty(p);
                            Pokemon[] all = party.getAll();
                            for(int i=0; i<all.length && i<saved.size(); i++) {
                                if(all[i] != null) {
                                    all[i].setHeldItem(saved.get(i));
                                    party.set(i, all[i]); 
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onTurnEnd(TurnEndEvent event) {
        try {
            BattleController bc = event.getBattleController();
            if (bc == null) return;
            for (BattleParticipant bp : bc.participants) {
                if (bp instanceof WildPixelmonParticipant) {
                    Entity entity = bp.getEntity();
                    if (entity != null && entity.getPersistentData().getBoolean("pixelmonraid_boss")) {
                        if (entity instanceof PixelmonEntity) {
                            try { ((PixelmonEntity) entity).getPokemon().heal(); } catch (Throwable t) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        try {
            if (event.getEntity() == null || event.getEntity().getCommandSenderWorld().isClientSide) return;
            if (event.getEntity().getPersistentData().getBoolean("pixelmonraid_template") ||
                    event.getEntity().getPersistentData().getBoolean("pixelmonraid_copy")) {
                event.setCanceled(true);
                event.setAmount(0);
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            if (event.getWorld().isClientSide) return;
            if (event.getTarget() == null || !(event.getPlayer() instanceof ServerPlayerEntity)) return;

            if (event.getTarget().getPersistentData().getBoolean("pixelmonraid_template")) {
                ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
                Entity target = event.getTarget();
                ServerWorld world = (ServerWorld) event.getWorld();

                RaidSession session = null;
                if (target.getPersistentData().contains("pixelmonraid_raidId")) {
                    session = RaidSpawner.getSessionByRaidId(target.getPersistentData().getUUID("pixelmonraid_raidId"));
                }
                if (session == null) session = RaidSpawner.getSessionSafe(world);
                if (session != null) {
                    if (!session.isTemplate(target.getUUID())) session.registerTemplate(target.getUUID());
                    event.setCanceled(true);

                    if (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH) {
                        player.sendMessage(new StringTextComponent("§c⚠ Do not click the boss! Use §e/raid join §cto enter the battle!"), Util.NIL_UUID);
                    } else {
                        player.sendMessage(new StringTextComponent("§cRaid is not active currently."), Util.NIL_UUID);
                    }
                }
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        try {
            if (event.getWorld().isClientSide || !(event.getWorld() instanceof ServerWorld)) return;
            if (!event.getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) return;

            if (!event.getWorld().dimension().location().toString().equals("minecraft:overworld")) {
                event.getEntity().remove(); return;
            }

            ServerWorld world = (ServerWorld) event.getWorld();
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
                if (session.getState() == RaidSession.State.IDLE) event.getEntity().remove();
                else session.registerCopy(event.getEntity().getUUID());
            }
        } catch (Throwable t) {}
    }

    @SubscribeEvent
    public static void onCaptureAttempt(CaptureEvent.StartCapture event) {
        try {
            if (event.getPokemon() != null && event.getPokemon().getEntity() != null) {
                if (event.getPokemon().getEntity().getPersistentData().getBoolean("pixelmonraid_boss")) {
                    event.setCanceled(true);
                    if (event.getPlayer() != null)
                        event.getPlayer().sendMessage(new StringTextComponent("§c§l✖ RAID BOSS PROTECTED ✖"), Util.NIL_UUID);
                }
            }
        } catch (Throwable t) {}
    }
}
