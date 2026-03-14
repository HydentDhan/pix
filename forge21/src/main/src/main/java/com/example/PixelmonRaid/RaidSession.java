package com.example.PixelmonRaid;

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
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;

public class RaidSession {
    private final UUID raidId;
    private ServerLevel world;
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
    private long lastBroadcastTime;
    private long lastHpBroadcastTick;
    private static final List<String> BANNED_MOVES = Arrays.asList("Fissure", "Guillotine", "Horn Drill", "Sheer Cold", "Endeavor", "Pain Split", "Perish Song", "Destiny Bond", "Super Fang", "Nature's Madness", "Curse", "Toxic", "Leech Seed", "Spore", "Hypnosis", "Sleep Powder", "Yawn", "Lovely Kiss", "Secret Power", "Grass Whistle", "Sing", "Dark Void", "Dire Claw", "Psycho Shift");

    public RaidSession(ServerLevel world, BlockPos ignored) {
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

    public UUID getRaidId() { return this.raidId; }
    public ServerLevel getWorld() { return this.world; }
    public DamageTracker getDamageTracker() { return this.damageTracker; }
    public int getMaxRaidHP() { return this.maxRaidHP; }
    public int getTotalRaidHP() { return this.totalRaidHP; }
    public boolean isAutoRaidEnabled() { return PixelmonRaidConfig.getInstance().isAutoRaidEnabled(); }

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

    public String getCurrentBossName() { return this.currentBossName; }
    public Set<UUID> getPlayers() { return new HashSet<>(this.players); }
    public RaidSession.State getState() { return this.state; }
    public boolean isBattleActive() { return this.battleActive; }
    public void setBattleActive(boolean v) { this.battleActive = v; }

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

    public void extendTime(int seconds) { this.skipTimer(-seconds); }
    public void reduceTime(int seconds) { this.skipTimer(seconds); }

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
        }
        this.updateBossBarImmediate();
    }

    public BlockPos getCenter() { return RaidSaveData.get(this.world).getCenter(); }
    public void setCenter(BlockPos pos) { RaidSaveData.get(this.world).setCenter(pos); }
    public BlockPos getPlayerSpawn() { return RaidSaveData.get(this.world).getPlayerSpawn(); }
    public void setPlayerSpawn(BlockPos pos) { RaidSaveData.get(this.world).setPlayerSpawn(pos); }

    public Set<UUID> getBossEntityUUIDs() {
        Set<UUID> all = new HashSet<>(this.activeCopyUUIDs);
        if (this.templateUUID != null) {
            all.add(this.templateUUID);
        }
        return all;
    }

    public void registerTemplate(UUID id) { if (id != null) this.templateUUID = id; }
    public void registerCopy(UUID id) { if (id != null) this.activeCopyUUIDs.add(id); }
    public void unregisterCopy(UUID id) { if (id != null) this.activeCopyUUIDs.remove(id); }
    public void clearBossEntities() {
        this.templateUUID = null;
        this.activeCopyUUIDs.clear();
    }

    public boolean isTemplate(UUID id) { return id != null && id.equals(this.templateUUID); }
    public boolean isCopy(UUID id) { return id != null && this.activeCopyUUIDs.contains(id); }
    public boolean isRaidEntity(UUID id) { return this.isTemplate(id) || this.isCopy(id); }

    public boolean addPlayer(UUID playerId) {
        this.joinTimes.putIfAbsent(playerId, System.currentTimeMillis());
        boolean isNew = this.players.add(playerId);
        if (isNew) {
            RaidSaveData.get(this.world).incrementRaidsJoined(playerId);
        }
        return isNew;
    }

    public void removePlayer(UUID playerId) { this.players.remove(playerId); }

    public void setFatigue(UUID playerId, int seconds) {
        if (seconds > 0) {
            long expiry = System.currentTimeMillis() + (long)seconds * 1000L;
            this.fatigueMap.put(playerId, expiry);
        }
    }

    public boolean isFatigued(UUID playerId) {
        if (!this.fatigueMap.containsKey(playerId)) return false;
        if (System.currentTimeMillis() > this.fatigueMap.get(playerId)) {
            this.fatigueMap.remove(playerId);
            return false;
        }
        return true;
    }

    public void applyRejoinCooldown(UUID playerId) {
        long duration = (long)PixelmonRaidConfig.getInstance().getRejoinCooldownSeconds() * 1000L;
        if (duration > 0L) {
            this.rejoinCooldowns.put(playerId, System.currentTimeMillis() + duration);
        }
    }

    public void startPlayerBattleRequest(ServerPlayer player) {
        if (this.state != RaidSession.State.IN_BATTLE && this.state != RaidSession.State.SUDDEN_DEATH) {
            player.sendSystemMessage(Component.literal("§cThe Raid is not active right now!"));
        } else {
            if (this.rejoinCooldowns.containsKey(player.getUUID())) {
                long end = this.rejoinCooldowns.get(player.getUUID());
                if (System.currentTimeMillis() < end) {
                    long left = (end - System.currentTimeMillis()) / 1000L;
                    String msg = String.format(PixelmonRaidConfig.getInstance().getMsgCooldown(), left + "s");
                    player.sendSystemMessage(Component.literal(msg));
                    return;
                }
                this.rejoinCooldowns.remove(player.getUUID());
            }

            if (!this.spawnDelays.containsKey(player.getUUID())) {
                this.addPlayer(player.getUUID());
                this.updateActionBars();
                this.updateBossBarImmediate();
                this.spawnDelays.put(player.getUUID(), 20);
                player.sendSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgJoin()));
            }
        }
    }

    private void executeStartBattle(UUID playerId) {
        ServerPlayer player = this.world.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            this.pendingBattles.add(player.getUUID());
            PlayerPartyStorage storage = StorageProxy.getPartyNow(player);
            boolean illegal = false;
            String banReason = "";
            Pokemon[] var6 = storage.getAll();
            for (Pokemon p : var6) {
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
                    if (illegal) break;
                }
            }

            if (illegal) {
                player.sendSystemMessage(Component.literal("§c§l❌ JOIN FAILED ❌"));
                player.sendSystemMessage(Component.literal(banReason));
                this.pendingBattles.remove(player.getUUID());
            } else {

                List<Pokemon> teamList = new ArrayList<>();
                boolean hasHealthy = false;
                Pokemon[] var30 = storage.getAll();
                for (Pokemon p : var30) {
                    if (p != null && !p.isEgg()) {
                        teamList.add(p);
                        if (!p.isFainted()) {
                            hasHealthy = true;
                        }
                    }
                }

                if (!hasHealthy) {
                    player.sendSystemMessage(Component.literal("§cYou have no healthy Pokemon to fight!"));
                    this.pendingBattles.remove(player.getUUID());
                } else {
                    try {
                        Entity template = null;
                        if (this.templateUUID != null) {
                            template = this.world.getEntity(this.templateUUID);
                        }

                        BlockPos center = this.getCenter();
                        double spawnZ, spawnX, spawnY;
                        if (template != null) {
                            spawnX = template.getX();
                            spawnY = template.getY() - 20.0D;
                            spawnZ = template.getZ();
                        } else {
                            spawnX = (double)center.getX();
                            spawnY = (double)center.getY() - 20.0D;
                            spawnZ = (double)center.getZ();
                        }

                        if (spawnY < 0.0D) spawnY = 5.0D;
                        if (!this.world.isLoaded(BlockPos.containing(spawnX, spawnY, spawnZ))) {
                            player.sendSystemMessage(Component.literal("§c[Raid] Area not loaded! Please get closer to the center."));
                            this.pendingBattles.remove(player.getUUID());
                            return;
                        }

                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("pixelmon", "pixelmon"));
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
                            cloned = PokemonFactory.create(PixelmonSpecies.get(this.currentBossName).get().getValueUnsafe());
                        } else {
                            cloned = PokemonFactory.create(PixelmonSpecies.CHARIZARD.getValueUnsafe());
                        }

                        cloned.setUUID(UUID.randomUUID());
                        cloned.setLevel(100);
                        try { cloned.getStats().setSpeed(99999); } catch (Throwable var26) {}

                        int diffLevel = PixelmonRaidConfig.getInstance().getRaidDifficulty();
                        String heldItemStr = PixelmonRaidConfig.getInstance().getTierRewards(diffLevel).bossHeldItem;
                        if (heldItemStr != null && !heldItemStr.trim().isEmpty()) {
                            Item fallback;
                            try {
                                Item hItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(heldItemStr.trim()));
                                if (hItem != null && hItem != Items.AIR) {
                                    cloned.setHeldItem(new ItemStack(hItem));
                                } else {
                                    fallback = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pixelmon", "leftovers"));
                                    if (fallback != null && fallback != Items.AIR) {
                                        cloned.setHeldItem(new ItemStack(fallback));
                                    }
                                }
                            } catch (Exception var27) {
                                try {
                                    fallback = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("pixelmon", "leftovers"));
                                    if (fallback != null && fallback != Items.AIR) {
                                        cloned.setHeldItem(new ItemStack(fallback));
                                    }
                                } catch (Exception var25) {}
                            }
                        }

                        copy.setPokemon(cloned);
                        copy.setPos(spawnX, spawnY, spawnZ);
                        copy.setYBodyRot(180.0F);
                        copy.setYHeadRot(180.0F);
                        copy.setCustomNameVisible(false);

                        // FIX: NEVER let the copy be saved to the hard drive!
                        // copy.setPersistenceRequired();

                        copy.setInvisible(true);
                        copy.setInvulnerable(false);
                        copy.getPersistentData().putBoolean("pixelmonraid_boss", true);
                        copy.getPersistentData().putBoolean("pixelmonraid_copy", true);
                        copy.getPersistentData().putUUID("pixelmonraid_raidId", this.raidId);
                        if (this.hasEnraged) {
                            copy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 99999, 1));
                            copy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 99999, 0));
                        }

                        this.registerCopy(copy.getUUID());
                        this.setBattleActive(true);
                        this.world.addFreshEntity(copy);
                        this.sanitizeMoveset(copy.getPokemon());

                        try {
                            copy.revive();
                            copy.setHealth(copy.getMaxHealth());
                        } catch (Throwable var23) {}

                        PlayerParticipant p1 = new PlayerParticipant(player, teamList.toArray(new Pokemon[0]));
                        RaidBossParticipant p2 = new RaidBossParticipant(copy, this.raidId);
                        BattleRules rules = new BattleRules(java.util.List.of());

                        BattleBuilder.builder().registryAccess(this.world.registryAccess()).teamOne(new BattleParticipant[]{p1}).teamTwo(new BattleParticipant[]{p2}).rules(rules).noSelection().start();
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
            } catch (Throwable var2) {}
        } catch (Throwable var3) {
            var3.printStackTrace();
        }
    }

    private void clearHolograms() {
        for(UUID uuid : this.hologramLines) {
            Entity e = this.world.getEntity(uuid);
            if (e != null) {
                e.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        this.hologramLines.clear();
        List<ArmorStand> nearby = this.world.getEntitiesOfClass(ArmorStand.class, (new AABB(this.getCenter())).inflate(10.0D));
        for(ArmorStand as : nearby) {
            if (as.getPersistentData().getBoolean("pixelmonraid_hologram")) {
                as.remove(Entity.RemovalReason.DISCARDED);
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
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            List<String> lines = new ArrayList<>();
            lines.add("§6§l⚔ RAID LEADERBOARD ⚔");
            for(int i = 0; i < 5; ++i) {
                if (i < sorted.size()) {
                    Entry<UUID, Integer> entry = sorted.get(i);
                    ServerPlayer pl = this.world.getServer().getPlayerList().getPlayer(entry.getKey());
                    String name = pl != null ? pl.getGameProfile().getName() : "Offline";
                    String color = i == 0 ? "§6" : (i < 3 ? "§e" : "§f");
                    lines.add(color + "#" + (i + 1) + " " + name + " §7- §c" + String.format("%,d", entry.getValue()));
                } else {
                    lines.add("§8#" + (i + 1) + " ---");
                }
            }

            while(this.hologramLines.size() < lines.size()) {
                ArmorStand as = new ArmorStand(this.world, x, y, z);
                as.setInvisible(true);
                as.setCustomNameVisible(true);
                as.setNoGravity(true);
                as.setInvulnerable(true);
                this.world.addFreshEntity(as);
                as.getPersistentData().putBoolean("pixelmonraid_hologram", true);
                this.hologramLines.add(as.getUUID());
            }

            for(int i = 0; i < this.hologramLines.size(); ++i) {
                Entity e = this.world.getEntity(this.hologramLines.get(i));
                if (e != null) {
                    e.setPos(x, y - (double)i * 0.3D, z);
                    if (i < lines.size()) {
                        e.setCustomName(Component.literal(lines.get(i)));
                    } else {
                        e.setCustomName(Component.literal(""));
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
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for(UUID pid : this.players) {
                ServerPlayer pl = this.world.getServer().getPlayerList().getPlayer(pid);
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
                        String rankColor = myRank == 1 ?
                                "§6" : (myRank <= 3 ? "§e" : "§f");
                        String msg = "§b👥 Players: §f" + participantCount + " §8| §c⚔ Damage: " + rankColor + String.format("%,d", myDmg) + " (#" + myRank + ")";
                        pl.sendSystemMessage(Component.literal(msg), true);
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

    private CustomBossEvent getSingletonBar() {
        return this.world != null && this.world.getServer() != null ?
                this.world.getServer().getCustomBossEvents().get(PixelmonRaidMod.MAIN_BAR_ID) : null;
    }

    private void updateIdleBarInfo(long ticksRemaining) {
        CustomBossEvent bar = this.getSingletonBar();
        if (bar != null) {
            if (!this.isAutoRaidEnabled()) {
                bar.setName(Component.literal(PixelmonRaidConfig.getInstance().getUiTimerBarPaused()));
                bar.setColor(BossBarColor.RED);
                bar.setOverlay(BossBarOverlay.NOTCHED_10);
                bar.setProgress(1.0F);
            } else {
                long totalInterval = 20L * (long)PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
                float pct = (float)ticksRemaining / (float)Math.max(1L, totalInterval);
                long sLeft = ticksRemaining / 20L;
                String timeStr = this.formatTimeCustom(sLeft);
                String title = PixelmonRaidConfig.getInstance().getUiTimerBarTitle().replace("%time%", timeStr);
                bar.setName(Component.literal(title));
                bar.setColor(BossBarColor.BLUE);
                bar.setOverlay(BossBarOverlay.PROGRESS);
                bar.setProgress(Math.max(0.0F, Math.min(1.0F, pct)));
            }

            if (!bar.isVisible()) bar.setVisible(true);
            for(ServerPlayer p : this.world.getServer().getPlayerList().getPlayers()) {
                if (!bar.getPlayers().contains(p)) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    private void updateBossBarInfo(long ticksRemaining, boolean isSuddenDeath) {
        CustomBossEvent bar = this.getSingletonBar();
        if (bar != null) {
            if (this.state != RaidSession.State.IN_BATTLE && this.state != RaidSession.State.SUDDEN_DEATH) {
                bar.setVisible(false);
                bar.removeAllPlayers();
            } else {
                long sLeft = Math.max(0L, ticksRemaining / 20L);
                String timeStr = this.formatTimeCustom(sLeft);
                String hpInfo = " §7| §c" + this.totalRaidHP + "§7/§c" + this.maxRaidHP + " HP";
                String timeInfo = " §7| §eTime: " + timeStr;
                String title = isSuddenDeath ?
                        "§4§l☠ SUDDEN DEATH: " + this.currentBossName + " ☠" + hpInfo + timeInfo :
                        "§d§lGlobal Boss: " + this.currentBossName + hpInfo + timeInfo;
                float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP);
                bar.setProgress(pct);

                if (isSuddenDeath) {
                    bar.setColor(BossBarColor.PURPLE);
                    bar.setOverlay(BossBarOverlay.NOTCHED_12);
                } else if (pct > 0.5F) {
                    bar.setColor(BossBarColor.GREEN);
                    bar.setOverlay(BossBarOverlay.PROGRESS);
                } else if (pct > 0.2F) {
                    bar.setColor(BossBarColor.YELLOW);
                    bar.setOverlay(BossBarOverlay.PROGRESS);
                } else {
                    bar.setColor(BossBarColor.RED);
                    bar.setOverlay(BossBarOverlay.NOTCHED_6);
                }

                if (!bar.isVisible()) bar.setVisible(true);

                bar.setName(Component.literal(title));
                for(ServerPlayer p : this.world.getServer().getPlayerList().getPlayers()) {
                    if (!bar.getPlayers().contains(p)) {
                        bar.addPlayer(p);
                    }
                }
            }
        }
    }

    private String formatTimeCustom(long totalSeconds) {
        if (totalSeconds < 60L) return totalSeconds + "s";
        long mins = totalSeconds / 60L;
        long hours = mins / 60L;
        long days = hours / 24L;
        mins %= 60L;
        hours %= 24L;
        StringBuilder sb = new StringBuilder();
        if (days > 0L) sb.append(days).append("d ");
        if (hours > 0L) sb.append(hours).append("h ");
        if (mins > 0L) sb.append(mins).append("m ");
        if (days == 0L && hours == 0L) sb.append(totalSeconds % 60L).append("s");
        return sb.toString().trim();
    }

    private void updateBossName() {
        if (this.templateUUID != null) {
            float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP) * 100.0F;
            String hpText = String.format("%.1f%%", pct);
            Entity e = this.world.getEntity(this.templateUUID);
            if (e != null) {
                String name = "§4§l☠ " + this.currentBossName + " | " + hpText + " HP ☠";
                e.setCustomName(Component.literal(name));
                e.setInvulnerable(true);
            }
        }
    }

    public void removeHealthBar() {
        CustomBossEvent bar = this.getSingletonBar();
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
    }

    public void clearSidebar() {}

    public void startBattleNow() {
        if (!this.isSpawning) {
            this.isSpawning = true;
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
                    this.world.playSound((Player)null, this.getCenter(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0F);
                }

                this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgRaidStart()), false);
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
                e.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        if (this.templateUUID != null) {
            Entity e = this.world.getEntity(this.templateUUID);
            if (e != null) {
                e.remove(Entity.RemovalReason.DISCARDED);
            }
            this.templateUUID = null;
        }

        this.clearBossEntities();
        try { RaidSpawner.unregisterSessionById(this.raidId); } catch (Throwable var5) {}
        try { PixelmonBossAttackListener.unregisterSessionTouched(this.raidId);
        } catch (Throwable var4) {}

        this.pendingBattles.clear();
        this.players.clear();
        this.damageTracker.clear();
        this.fatigueMap.clear();
        this.joinTimes.clear();
        this.setState(RaidSession.State.IDLE);
        this.isSpawning = false;
        this.setBattleActive(false);
    }

    public void finishRaid(boolean victory, UUID killerId) {
        for(ServerPlayer pl : this.world.getServer().getPlayerList().getPlayers()) {
            try { BattleRegistry.getBattle(pl).endBattle();
            } catch (Throwable var6) {}
        }

        if (this.markRewardsDistributedIfNot()) {
            RaidRewardHandler.broadcastLeaderboard(this);
            RaidRewardHandler.distributeRewards(this, victory, killerId);
            RaidSaveData.get(this.world).saveLastLeaderboard(this.damageTracker.getAllDamage(), this.world);
            if (victory) {
                PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
                if (cfg.isSoundEnabled()) {
                    this.world.playSound((Player)null, this.getCenter(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, cfg.getSoundVolume(), 1.0F);
                }

                this.world.getPlayers((p) -> true).forEach((p) -> {
                    p.sendSystemMessage(Component.literal(cfg.getMsgRaidWin()));
                });
                RaidSaveData.get(this.world).incrementWinStreak();
                boolean actuallyLeveledUp = PixelmonRaidConfig.getInstance().incrementDifficultyOnWin();
                if (actuallyLeveledUp) {
                    this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e§l⚠ RAID DIFFICULTY INCREASED TO LEVEL " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + "!"), false);
                } else {
                    this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§b§l✯ MAXIMUM DIFFICULTY CONQUERED! ✯"), false);
                    this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§7You have proven yourselves worthy of the highest tier!"), false);
                }
            } else {
                if (PixelmonRaidConfig.getInstance().isSoundEnabled()) {
                    this.world.playSound((Player)null, this.getCenter(), SoundEvents.WITHER_DEATH, SoundSource.MASTER, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0F);
                }

                this.world.getPlayers((p) -> true).forEach((p) -> {
                    p.sendSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgRaidLoss()));
                });
                RaidSaveData.get(this.world).resetWinStreak();
                PixelmonRaidConfig.getInstance().decrementDifficultyOnLoss();
                this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§c§l🔻 RAID FAILED! Difficulty Decreased to Level " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + " 🔻"), false);
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
            if (tick % 20L == 0L) this.updateBossBarImmediate();
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

            if (tick % 100L == 0L) this.cleanStaleCopies();
            long intervalTicks;
            switch(this.state) {
                case IDLE:
                    long next = RaidSaveData.get(this.world).getNextRaidTick();
                    long now = this.world.getGameTime();
                    long remaining = next - now;
                    if (tick % 20L == 0L) this.updateBossBarImmediate();
                    if (System.currentTimeMillis() - this.lastBroadcastTime > 5000L) {
                        MutableComponent baseMsg;
                        MutableComponent clickMsg;
                        if (remaining >= 5980L && remaining <= 6020L) {
                            baseMsg = Component.literal(PixelmonRaidConfig.getInstance().getMsgSpawning5Min() + " ");
                            clickMsg = Component.literal(PixelmonRaidConfig.getInstance().getMsgClickToWarp())
                                    .withStyle(Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(5636095))
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raid warp"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eClick to teleport to the Raid!"))));
                            this.world.getServer().getPlayerList().broadcastSystemMessage(baseMsg.append(clickMsg), false);
                            this.lastBroadcastTime = System.currentTimeMillis();
                        }

                        if (remaining >= 1180L && remaining <= 1220L) {
                            baseMsg = Component.literal(PixelmonRaidConfig.getInstance().getMsgSpawning1Min() + " ");
                            clickMsg = Component.literal(PixelmonRaidConfig.getInstance().getMsgClickToWarp())
                                    .withStyle(Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(5636095))
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raid warp"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eClick to teleport to the Raid!"))));
                            this.world.getServer().getPlayerList().broadcastSystemMessage(baseMsg.append(clickMsg), false);
                            this.lastBroadcastTime = System.currentTimeMillis();
                        }
                    }

                    if (next == -1L || (next < tick && next != 0L)) {
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
                            String color = pct > 50.0F ?
                                    "§a" : (pct > 20.0F ? "§e" : "§c");
                            String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdate().replace("%boss%", this.currentBossName).replace("%color%", color).replace("%hp%", String.valueOf(this.totalRaidHP)).replace("%maxhp%", String.valueOf(this.maxRaidHP)).replace("%pct%", String.format("%.1f", pct));
                            this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
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
                            String color = pct > 50.0F ?
                                    "§a" : (pct > 20.0F ? "§e" : "§c");
                            String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdateSuddenDeath().replace("%boss%", this.currentBossName).replace("%color%", color).replace("%hp%", String.valueOf(this.totalRaidHP)).replace("%maxhp%", String.valueOf(this.maxRaidHP)).replace("%pct%", String.format("%.1f", pct));
                            this.world.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
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
                } else if (entity instanceof PixelmonEntity pe) {
                    if (pe.battleController == null && pe.tickCount > 300) {
                        pe.remove(Entity.RemovalReason.DISCARDED);
                        it.remove();
                    }
                }
            }
        }
    }

    private void bossTickLogic(long tick) {
        Entity e = null;
        if (this.templateUUID != null) {
            e = this.world.getEntity(this.templateUUID);
            // ONLY respawn if the entity physically exists but is dead (killed by a glitch/admin).
            // If it's just 'null', it means the chunk is safely unloaded. Do not panic!
            if (e != null && !e.isAlive() && !this.isSpawning) {
                RaidSpawner.spawnBoss(this);
            }
        } else if (!this.isSpawning && (this.state == RaidSession.State.IN_BATTLE || this.state == RaidSession.State.SUDDEN_DEATH)) {
            RaidSpawner.spawnBoss(this);
        }

        if (tick % 10L == 0L) this.updateBossName();
        if (tick % 20L == 0L) {
            this.updateActionBars();
            this.updateHologram();
        }

        if ((this.state == RaidSession.State.IN_BATTLE || this.state == RaidSession.State.SUDDEN_DEATH)) {
            if (this.templateUUID != null) {
                e = this.world.getEntity(this.templateUUID);
                if (e instanceof PixelmonEntity pe) {
                    try {
                        pe.setDeltaMovement(0, pe.getDeltaMovement().y, 0);
                        pe.fallDistance = 0.0F;

                        float pct = (float)this.totalRaidHP / (float)Math.max(1, this.maxRaidHP);
                        float targetHealth = Math.max(1.0F, pe.getMaxHealth() * pct);
                        pe.setHealth(targetHealth);

                        if (tick % 20L == 0L) {
                            if (pe.getPokemon() != null) {
                                pe.getPokemon().setHealth((int)Math.max(1.0F, (float)pe.getPokemon().getMaxHealth() * pct));
                            }

                            float scale = (float)PixelmonRaidConfig.getInstance().getBossScaleFactor();
                            pe.setPixelmonScale(scale);
                            pe.setInvulnerable(true);
                            pe.setPersistenceRequired();

                            pe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
                            pe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                            try {
                                pe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED).setBaseValue(0.0D);
                            } catch (Throwable t2) {}

                            BlockPos center = this.getCenter();
                            if (pe.distanceToSqr(center.getX() + 0.5D, pe.getY(), center.getZ() + 0.5D) > 4.0D) {
                                pe.setPos(center.getX() + 0.5D, pe.getY(), center.getZ() + 0.5D);
                            }
                        }
                    } catch (Throwable var8) {}
                }
            }

            for (UUID copyId : this.activeCopyUUIDs) {
                Entity copyEnt = this.world.getEntity(copyId);
                if (copyEnt instanceof PixelmonEntity pe && pe.getPokemon() != null) {
                    pe.setHealth(pe.getMaxHealth());
                    pe.getPokemon().heal();

                    if (tick % 20L == 0L) {
                        try {
                            float scale = (float)PixelmonRaidConfig.getInstance().getBossScaleFactor();
                            pe.setPixelmonScale(scale);
                        } catch (Throwable t) {}
                    }
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
                p.sendSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgEnrage()));
            });
            this.world.playSound((Player)null, this.getCenter(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 100.0F, 1.0F);
        }
    }

    public static enum State {
        IDLE, IN_BATTLE, SUDDEN_DEATH, COMPLETED, PAUSED;
    }
}