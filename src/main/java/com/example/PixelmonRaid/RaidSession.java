package com.example.PixelmonRaid;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.server.CustomServerBossInfo;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.world.BossInfo;

import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.Color;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.enums.EnumGrowth;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRules;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RaidSession {
    public enum State { IDLE, IN_BATTLE, SUDDEN_DEATH, COMPLETED, PAUSED }

    private final UUID raidId;
    private ServerWorld world;
    private State state = State.IDLE;

    private long battleStartTick;
    private long suddenDeathStartTick;
    private boolean isSpawning = false;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private UUID templateUUID = null;
    private final Set<UUID> activeCopyUUIDs = ConcurrentHashMap.newKeySet();
    private final DamageTracker damageTracker = new DamageTracker();
    private final Map<UUID, Long> joinTimes = new HashMap<>();
    private final Map<UUID, Long> fatigueMap = new HashMap<>();
    private final Map<UUID, Integer> spawnDelays = new ConcurrentHashMap<>();
    private final Set<UUID> pendingBattles = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> rejoinCooldowns = new ConcurrentHashMap<>();
    private final List<UUID> hologramLines = new ArrayList<>();

    private boolean rewardsDistributed = false;
    private boolean hasEnraged = false;

    private int totalRaidHP;
    private int maxRaidHP;
    private String currentBossName = "Charizard";
    private volatile boolean battleActive = false;

    private int spawnFailureCount = 0;
    private long lastBroadcastTime = 0;
    private long lastHpBroadcastTick = -1;

    private static final List<String> BANNED_MOVES = Arrays.asList(
            "Fissure", "Guillotine", "Horn Drill", "Sheer Cold",
            "Endeavor", "Pain Split", "Perish Song", "Destiny Bond",
            "Super Fang", "Nature's Madness", "Curse", "Toxic", "Leech Seed",
            "Spore", "Hypnosis", "Sleep Powder", "Yawn", "Lovely Kiss",
            "Secret Power", "Grass Whistle", "Sing", "Dark Void",
            "Dire Claw", "Psycho Shift"
    );

    public RaidSession(ServerWorld world, BlockPos ignored) {
        this.world = world;
        this.raidId = UUID.randomUUID();
        int wins = 0;
        if (world != null) wins = RaidSaveData.get(world).getWinStreak();
        int configHP = PixelmonRaidConfig.getInstance().getBossHP(PixelmonRaidConfig.getInstance().getRaidDifficulty(), wins);
        this.totalRaidHP = configHP;
        this.maxRaidHP = this.totalRaidHP;
    }

    public UUID getRaidId() { return raidId; }
    public ServerWorld getWorld() { return world; }
    public DamageTracker getDamageTracker() { return damageTracker; }
    public int getMaxRaidHP() { return maxRaidHP; }
    public int getTotalRaidHP() { return totalRaidHP; }

    public boolean isAutoRaidEnabled() { return PixelmonRaidConfig.getInstance().isAutoRaidEnabled(); }

    public void setTotalRaidHP(int hp) {
        this.totalRaidHP = Math.max(0, hp);
        if (this.totalRaidHP > this.maxRaidHP) this.maxRaidHP = this.totalRaidHP;
    }

    public void setCurrentBossName(String name) { if (name != null && !name.isEmpty()) this.currentBossName = name; }
    public String getCurrentBossName() { return currentBossName; }
    public Set<UUID> getPlayers() { return new HashSet<>(players); }
    public State getState() { return state; }
    public boolean isBattleActive() { return battleActive; }
    public void setBattleActive(boolean v) { this.battleActive = v; }

    public void skipTimer(int seconds) {
        long ticks = seconds * 20L;
        if (state == State.IDLE) {
            long currentNext = RaidSaveData.get(world).getNextRaidTick();
            RaidSaveData.get(world).setNextRaidTick(currentNext - ticks);
        } else if (state == State.IN_BATTLE) {
            this.battleStartTick -= ticks;
        } else if (state == State.SUDDEN_DEATH) {
            this.suddenDeathStartTick -= ticks;
        }
        broadcastPoolUpdate();
    }

    public void extendTime(int seconds) { skipTimer(-seconds); }
    public void reduceTime(int seconds) { skipTimer(seconds); }
    public void resetTime() {
        if (state == State.IN_BATTLE) this.battleStartTick = world.getGameTime();
        if (state == State.SUDDEN_DEATH) this.suddenDeathStartTick = world.getGameTime();
    }

    public void setState(State s) {
        this.state = s;
        if (s != State.IN_BATTLE && s != State.SUDDEN_DEATH) {
            clearHolograms();
            if (s != State.IDLE || !PixelmonRaidConfig.getInstance().isShowIdleTimerBar()) {
                removeHealthBar();
            }
        }
        else if (s == State.IN_BATTLE) { this.battleStartTick = world.getGameTime(); }
        else if (s == State.SUDDEN_DEATH) { this.suddenDeathStartTick = world.getGameTime(); }

        if (s == State.COMPLETED) { rewardsDistributed = false; hasEnraged = false; isSpawning = false; spawnFailureCount = 0; }
        updateBossBarImmediate();
    }

    public BlockPos getCenter() { return RaidSaveData.get(world).getCenter(); }
    public void setCenter(BlockPos pos) { RaidSaveData.get(world).setCenter(pos); }
    public BlockPos getPlayerSpawn() { return RaidSaveData.get(world).getPlayerSpawn(); }
    public void setPlayerSpawn(BlockPos pos) { RaidSaveData.get(world).setPlayerSpawn(pos); }

    public Set<UUID> getBossEntityUUIDs() { Set<UUID> all = new HashSet<>(activeCopyUUIDs); if (templateUUID != null) all.add(templateUUID); return all; }
    public void registerTemplate(UUID id) { if (id != null) this.templateUUID = id; }
    public void registerCopy(UUID id) { if (id != null) activeCopyUUIDs.add(id); }
    public void unregisterCopy(UUID id) { if (id != null) activeCopyUUIDs.remove(id); }
    public void clearBossEntities() { templateUUID = null; activeCopyUUIDs.clear(); }
    public boolean isTemplate(UUID id) { return id != null && id.equals(templateUUID); }
    public boolean isCopy(UUID id) { return id != null && activeCopyUUIDs.contains(id); }
    public boolean isRaidEntity(UUID id) { return isTemplate(id) || isCopy(id); }

    public boolean addPlayer(UUID playerId) {
        joinTimes.putIfAbsent(playerId, System.currentTimeMillis());
        boolean isNew = players.add(playerId);
        if (isNew) RaidSaveData.get(world).incrementRaidsJoined(playerId);
        return isNew;
    }

    public void removePlayer(UUID playerId) { players.remove(playerId); }

    public void setFatigue(UUID playerId, int seconds) { if (seconds <= 0) return;
        long expiry = System.currentTimeMillis() + (seconds * 1000L); fatigueMap.put(playerId, expiry);
    }
    public boolean isFatigued(UUID playerId) { if (!fatigueMap.containsKey(playerId)) return false; if (System.currentTimeMillis() > fatigueMap.get(playerId)) { fatigueMap.remove(playerId); return false; } return true; }

    public void applyRejoinCooldown(UUID playerId) {
        long duration = PixelmonRaidConfig.getInstance().getRejoinCooldownSeconds() * 1000L;
        if (duration > 0) rejoinCooldowns.put(playerId, System.currentTimeMillis() + duration);
    }

    public void startPlayerBattleRequest(ServerPlayerEntity player) {
        if (state != State.IN_BATTLE && state != State.SUDDEN_DEATH) {
            player.sendMessage(new StringTextComponent("§cThe Raid is not active right now!"), player.getUUID());
            return;
        }

        if (rejoinCooldowns.containsKey(player.getUUID())) {
            long end = rejoinCooldowns.get(player.getUUID());
            if (System.currentTimeMillis() < end) {
                long left = (end - System.currentTimeMillis()) / 1000L;
                String msg = String.format(PixelmonRaidConfig.getInstance().getMsgCooldown(), left + "s");
                player.sendMessage(new StringTextComponent(msg), player.getUUID());
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                return;
            } else {
                rejoinCooldowns.remove(player.getUUID());
            }
        }

        if (spawnDelays.containsKey(player.getUUID())) return;

        addPlayer(player.getUUID());
        updateActionBars();
        updateBossBarImmediate();

        spawnDelays.put(player.getUUID(), 20);
        player.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgJoin()), player.getUUID());
    }

    private void executeStartBattle(UUID playerId) {
        ServerPlayerEntity player = world.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return;

        pendingBattles.add(player.getUUID());
        PlayerPartyStorage storage = StorageProxy.getParty(player);

        boolean illegal = false;
        String banReason = "";
        for (Pokemon p : storage.getAll()) {
            if (p != null && !p.isEgg()) {
                if (p.isLegendary() || p.isMythical()) {
                    illegal = true;
                    banReason = "§cLegendary and Mythical Pokémon are BANNED!";
                    break;
                }
                if (p.getMoveset() != null) {
                    for (Attack atk : p.getMoveset()) {
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
            player.sendMessage(new StringTextComponent("§c§l❌ JOIN FAILED ❌"), player.getUUID());
            player.sendMessage(new StringTextComponent(banReason), player.getUUID());
            pendingBattles.remove(player.getUUID());
            return;
        }

        Pokemon activePokemon = null;
        for (Pokemon p : storage.getAll()) {
            if (p != null && !p.isEgg() && !p.isFainted()) {
                activePokemon = p;
                break;
            }
        }

        if (activePokemon == null) {
            player.sendMessage(new StringTextComponent("§cYou have no healthy Pokemon to fight!"), player.getUUID());
            pendingBattles.remove(player.getUUID());
            return;
        }

        try {
            Entity template = null;
            if (templateUUID != null) template = world.getEntity(templateUUID);
            double spawnX, spawnY, spawnZ;

            BlockPos center = getCenter();
            if (template != null) {
                spawnX = template.getX();
                spawnY = template.getY() - 20.0;
                spawnZ = template.getZ();
            } else {
                spawnX = center.getX();
                spawnY = center.getY() - 20.0;
                spawnZ = center.getZ();
            }
            if (spawnY < 0) spawnY = 5.0;
            if (!world.isLoaded(new BlockPos(spawnX, spawnY, spawnZ))) {
                player.sendMessage(new StringTextComponent("§c[Raid] Area not loaded! Please get closer to the center."), player.getUUID());
                pendingBattles.remove(player.getUUID());
                return;
            }

            net.minecraft.entity.EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITIES.getValue(new net.minecraft.util.ResourceLocation("pixelmon", "pixelmon"));
            if (type == null) { pendingBattles.remove(player.getUUID()); return; }
            PixelmonEntity copy = (PixelmonEntity) type.create(world);
            if (copy == null) { pendingBattles.remove(player.getUUID()); return; }

            Pokemon cloned = null;
            if (PixelmonSpecies.get(currentBossName).isPresent()) {
                cloned = PokemonFactory.create(PixelmonSpecies.get(currentBossName).get().getValueUnsafe());
            } else {
                cloned = PokemonFactory.create(PixelmonSpecies.CHARIZARD.getValueUnsafe());
            }

            cloned.setUUID(UUID.randomUUID());
            cloned.setLevel(100);
            try { cloned.getStats().setSpeed(99999); } catch (Throwable ignored) {}

            // --- SAFELY EQUIP CUSTOM BOSS HELD ITEM ---
            int diffLevel = PixelmonRaidConfig.getInstance().getRaidDifficulty();
            String heldItemStr = PixelmonRaidConfig.getInstance().getTierRewards(diffLevel).bossHeldItem;
            if (heldItemStr != null && !heldItemStr.trim().isEmpty()) {
                try {
                    net.minecraft.item.Item hItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(heldItemStr.trim()));
                    if (hItem != null && hItem != net.minecraft.item.Items.AIR) {
                        cloned.setHeldItem(new net.minecraft.item.ItemStack(hItem));
                    } else {
                        // Fallback safely to Leftovers if they spelled it wrong
                        net.minecraft.item.Item fallback = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("pixelmon", "leftovers"));
                        if (fallback != null && fallback != net.minecraft.item.Items.AIR) cloned.setHeldItem(new net.minecraft.item.ItemStack(fallback));
                    }
                } catch (Exception e) {
                    try {
                        net.minecraft.item.Item fallback = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("pixelmon", "leftovers"));
                        if (fallback != null && fallback != net.minecraft.item.Items.AIR) cloned.setHeldItem(new net.minecraft.item.ItemStack(fallback));
                    } catch (Exception ignored) {}
                }
            }

            copy.setPokemon(cloned);
            copy.setPos(spawnX, spawnY, spawnZ);
            copy.setYBodyRot(180.0F);
            copy.setYHeadRot(180.0F);
            copy.setNoAi(false);
            copy.setPersistenceRequired();
            try { copy.getPokemon().setGrowth(EnumGrowth.Ginormous); } catch (Throwable ex) {}

            copy.setInvisible(true);
            copy.setCustomNameVisible(false);

            copy.getPersistentData().putBoolean("pixelmonraid_boss", true);
            copy.getPersistentData().putBoolean("pixelmonraid_copy", true);
            copy.getPersistentData().putUUID("pixelmonraid_raidId", RaidSession.this.raidId);
            if (hasEnraged) {
                copy.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, 99999, 1));
                copy.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, 99999, 0));
            }

            registerCopy(copy.getUUID());
            setBattleActive(true);
            world.addFreshEntity(copy);

            sanitizeMoveset(copy.getPokemon());
            copy.getPersistentData().putBoolean("pixelmonraid_boss", true);
            copy.getPersistentData().putBoolean("pixelmonraid_copy", true);
            copy.getPersistentData().putUUID("pixelmonraid_raidId", RaidSession.this.raidId);

            try { copy.revive(); copy.setHealth(copy.getMaxHealth()); } catch (Throwable t) {}

            PlayerParticipant p1 = new PlayerParticipant(player, activePokemon);
            WildPixelmonParticipant p2 = new WildPixelmonParticipant(copy);

            BattleRules rules = new BattleRules();
            rules.set(BattleRuleRegistry.BATTLE_TYPE, BattleType.SINGLE);
            BattleBuilder.builder()
                    .teamOne(p1)
                    .teamTwo(p2)
                    .rules(rules)
                    .noSelection()
                    .start();

            updateActionBars();
            updateBossBarImmediate();
            pendingBattles.remove(player.getUUID());
        } catch (Throwable t) {
            t.printStackTrace();
            pendingBattles.remove(player.getUUID());
        }
    }

    private void sanitizeMoveset(Pokemon pokemon) {
        try {
            List<String> bannedMoves = Arrays.asList("Roar", "Whirlwind", "Dragon Tail", "Circle Throw", "Teleport");
            for (int i = 0; i < 4; i++) {
                Attack atk = pokemon.getMoveset().get(i);
                if (atk != null && bannedMoves.contains(atk.getMove().getAttackName())) {
                    pokemon.getMoveset().set(i, new Attack("Hyper Beam"));
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    public void broadcastPoolUpdate() {
        try {
            updateBossBarImmediate();
            updateActionBars();
            updateHologram();
            try { RaidSaveData.get(world).saveLastLeaderboard(damageTracker.getAllDamage(), world); } catch (Throwable ignored) {}
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void clearHolograms() {
        for (UUID uuid : hologramLines) {
            Entity e = world.getEntity(uuid);
            if (e != null) e.remove();
        }
        hologramLines.clear();

        List<ArmorStandEntity> nearby = world.getEntitiesOfClass(ArmorStandEntity.class, new net.minecraft.util.math.AxisAlignedBB(getCenter()).inflate(10));
        for(ArmorStandEntity as : nearby) {
            if(as.getPersistentData().getBoolean("pixelmonraid_hologram")) as.remove();
        }
    }

    private void updateHologram() {
        if (!PixelmonRaidConfig.getInstance().isHologramEnabled()) {
            if (!hologramLines.isEmpty()) clearHolograms();
            return;
        }

        if (world == null || state == State.IDLE) return;
        double x = PixelmonRaidConfig.getInstance().getHoloX();
        double y = PixelmonRaidConfig.getInstance().getHoloY();
        double z = PixelmonRaidConfig.getInstance().getHoloZ();

        Map<UUID, Integer> allDmg = damageTracker.getAllDamage();
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(allDmg.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> lines = new ArrayList<>();
        lines.add("§6§l⚔ RAID LEADERBOARD ⚔");
        for (int i = 0; i < 5; i++) {
            if (i < sorted.size()) {
                Map.Entry<UUID, Integer> entry = sorted.get(i);
                ServerPlayerEntity pl = world.getServer().getPlayerList().getPlayer(entry.getKey());
                String name = (pl != null) ? pl.getGameProfile().getName() : "Offline";
                String color = (i == 0) ? "§6" : (i < 3 ? "§e" : "§f");
                lines.add(color + "#" + (i+1) + " " + name + " §7- §c" + String.format("%,d", entry.getValue()));
            } else {
                lines.add("§8#" + (i+1) + " ---");
            }
        }

        while (hologramLines.size() < lines.size()) {
            ArmorStandEntity as = new ArmorStandEntity(world, x, y, z);
            as.setInvisible(true);
            as.setNoGravity(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
            world.addFreshEntity(as);
            as.getPersistentData().putBoolean("pixelmonraid_hologram", true);
            hologramLines.add(as.getUUID());
        }

        for (int i = 0; i < hologramLines.size(); i++) {
            Entity e = world.getEntity(hologramLines.get(i));
            if (e != null) {
                e.setPos(x, y - (i * 0.3), z);
                if (i < lines.size()) {
                    e.setCustomName(new StringTextComponent(lines.get(i)));
                } else {
                    e.setCustomName(new StringTextComponent(""));
                }
            }
        }
    }

    public void updateActionBars() {
        if (world == null) return;
        int participantCount = players.size();

        Map<UUID, Integer> allDmg = damageTracker.getAllDamage();
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(allDmg.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (UUID pid : players) {
            ServerPlayerEntity pl = world.getServer().getPlayerList().getPlayer(pid);
            if (pl != null) {
                int myRank = -1;
                int myDmg = 0;
                for(int i=0; i<sorted.size(); i++) {
                    if(sorted.get(i).getKey().equals(pid)) {
                        myRank = i + 1;
                        myDmg = sorted.get(i).getValue();
                        break;
                    }
                }

                if(myRank != -1) {
                    String rankColor = (myRank == 1) ? "§6" : (myRank <= 3 ? "§e" : "§f");
                    String msg = "§b👥 Players: §f" + participantCount + " §8| §c⚔ Damage: " + rankColor + String.format("%,d", myDmg) + " (#" + myRank + ")";
                    pl.sendMessage(new StringTextComponent(msg), ChatType.GAME_INFO, Util.NIL_UUID);
                }
            }
        }
    }

    public void updateBossBarImmediate() {
        if (state == State.IDLE) {
            if (!PixelmonRaidConfig.getInstance().isShowIdleTimerBar()) {
                removeHealthBar();
                return;
            }
            long next = RaidSaveData.get(world).getNextRaidTick();
            long rem = Math.max(0, next - world.getGameTime());
            updateIdleBarInfo(rem);
        }
        else if(state == State.IN_BATTLE) {
            int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
            long timeRemaining = (20L * PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff)) - (world.getGameTime() - battleStartTick);
            updateBossBarInfo(timeRemaining, false);
        }
        else if (state == State.SUDDEN_DEATH) {
            long sdDurationTicks = 20L * PixelmonRaidConfig.getInstance().getSuddenDeathDurationSeconds();
            long timeRemaining = sdDurationTicks - (world.getGameTime() - suddenDeathStartTick);
            updateBossBarInfo(timeRemaining, true);
        }
        else {
            removeHealthBar();
        }
    }

    private CustomServerBossInfo getSingletonBar() {
        if (world == null || world.getServer() == null) return null;
        return world.getServer().getCustomBossEvents().get(PixelmonRaidMod.MAIN_BAR_ID);
    }

    private void updateIdleBarInfo(long ticksRemaining) {
        CustomServerBossInfo bar = getSingletonBar();
        if (bar == null) return;

        if (!isAutoRaidEnabled()) {
            bar.setName(new StringTextComponent(PixelmonRaidConfig.getInstance().getUiTimerBarPaused()));
            bar.setColor(BossInfo.Color.RED);
            bar.setOverlay(BossInfo.Overlay.NOTCHED_10);
            bar.setValue(100);
        } else {
            long totalInterval = 20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
            float pct = (float) ticksRemaining / (float) Math.max(1, totalInterval);
            int progress = (int) (pct * 100f);

            long sLeft = ticksRemaining / 20L;
            String timeStr = formatTimeCustom(sLeft);
            String title = PixelmonRaidConfig.getInstance().getUiTimerBarTitle().replace("%time%", timeStr);

            bar.setName(new StringTextComponent(title));
            bar.setColor(BossInfo.Color.BLUE);
            bar.setOverlay(BossInfo.Overlay.PROGRESS);
            bar.setValue(Math.max(0, Math.min(100, progress)));
        }

        if (!bar.isVisible()) bar.setVisible(true);
        for (ServerPlayerEntity p : world.getServer().getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    private void updateBossBarInfo(long ticksRemaining, boolean isSuddenDeath) {
        CustomServerBossInfo bar = getSingletonBar();
        if (bar == null) return;

        if (state != State.IN_BATTLE && state != State.SUDDEN_DEATH) {
            bar.setVisible(false);
            bar.removeAllPlayers();
            return;
        }

        long sLeft = Math.max(0, ticksRemaining / 20L);
        String timeStr = formatTimeCustom(sLeft);

        String hpInfo = " §7| §c" + totalRaidHP + "§7/§c" + maxRaidHP + " HP";
        String timeInfo = " §7| §eTime: " + timeStr;

        String title = isSuddenDeath
                ? "§4§l☠ SUDDEN DEATH: " + currentBossName + " ☠" + hpInfo + timeInfo
                : "§d§lGlobal Boss: " + currentBossName + hpInfo + timeInfo;
        float pct = (float)totalRaidHP / (float)Math.max(1, maxRaidHP);
        bar.setValue((int)(pct * 100));
        if (isSuddenDeath) {
            bar.setColor(BossInfo.Color.PURPLE);
            bar.setOverlay(BossInfo.Overlay.NOTCHED_12);
        } else {
            if (pct > 0.5f) {
                bar.setColor(BossInfo.Color.GREEN);
                bar.setOverlay(BossInfo.Overlay.PROGRESS);
            } else if (pct > 0.2f) {
                bar.setColor(BossInfo.Color.YELLOW);
                bar.setOverlay(BossInfo.Overlay.PROGRESS);
            } else {
                bar.setColor(BossInfo.Color.RED);
                bar.setOverlay(BossInfo.Overlay.NOTCHED_6);
            }
        }

        if (!bar.isVisible()) bar.setVisible(true);
        bar.setMax(100);
        bar.setName(new StringTextComponent(title));

        for (ServerPlayerEntity p : world.getServer().getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    private String formatTimeCustom(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long mins = totalSeconds / 60;
        long hours = mins / 60;
        long days = hours / 24;
        mins %= 60;
        hours %= 24;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (days == 0 && hours == 0) sb.append(totalSeconds % 60).append("s");
        return sb.toString().trim();
    }

    private void updateBossName() {
        if (templateUUID == null) return;
        float pct = (float)totalRaidHP / (float)Math.max(1, maxRaidHP) * 100f;
        String hpText = String.format("%.1f%%", pct);

        Entity e = world.getEntity(templateUUID);
        if (e != null) {
            String name = "§4§l☠ " + currentBossName + " | " + hpText + " HP ☠";
            e.setCustomName(new StringTextComponent(name));
            e.setCustomNameVisible(true);
        }
    }

    public void removeHealthBar() {
        CustomServerBossInfo bar = getSingletonBar();
        if (bar != null) { bar.removeAllPlayers(); bar.setVisible(false); }
    }

    public void clearSidebar() { }

    public void startBattleNow() {
        if (isSpawning) return;
        isSpawning = true;
        spawnFailureCount = 0;

        int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
        int wins = RaidSaveData.get(world).getWinStreak();
        int configHP = PixelmonRaidConfig.getInstance().getBossHP(diff, wins);
        this.totalRaidHP = configHP;
        this.maxRaidHP = configHP;

        setState(State.IN_BATTLE);
        damageTracker.clear();
        this.hasEnraged = false;

        boolean spawned = false;
        if (world.isLoaded(getCenter())) {
            spawned = RaidSpawner.spawnBoss(this);
        } else {
            spawned = true;
        }

        if (spawned) {
            this.battleStartTick = world.getGameTime();
            long durationTicks = 20L * PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff);
            long cooldownTicks = 20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
            RaidSaveData.get(world).setNextRaidTick(world.getGameTime() + durationTicks + cooldownTicks);
            int durationMins = (int) (durationTicks / 20 / 60);
            String discordDesc = "**Boss:** " + currentBossName + "\n" +
                    "**Difficulty:** Level " + diff + "\n" +
                    "**HP:** " + String.format("%,d", maxRaidHP) + "\n" +
                    "**Time Limit:** " + durationMins + " Minutes\n\n" +
                    "*Type `/raid join` in-game to fight!*";
            DiscordHandler.sendEmbed("☠️ THE BEAST HAS AWAKENED!", discordDesc, 0xFF0000);

            if(PixelmonRaidConfig.getInstance().isSoundEnabled()) {
                world.playSound(null, getCenter(), SoundEvents.WITHER_SPAWN, SoundCategory.HOSTILE, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0f);
            }

            world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgRaidStart()), ChatType.SYSTEM, Util.NIL_UUID);
        } else {
            forceResetTimer();
        }
        isSpawning = false;
    }

    public void forceResetTimer() {
        cleanup();
        long now = world.getGameTime();
        long interval = 20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
        RaidSaveData.get(world).setNextRaidTick(now + interval);
        setState(State.IDLE);
    }

    public void cleanup() {
        removeHealthBar();
        clearSidebar();
        clearHolograms();
        spawnDelays.clear();
        pendingBattles.clear();
        for (UUID id : new HashSet<>(activeCopyUUIDs)) {
            unregisterCopy(id);
            Entity e = world.getEntity(id);
            if (e != null) e.remove();
        }
        if (templateUUID != null) {
            Entity e = world.getEntity(templateUUID);
            if (e != null) e.remove();
            templateUUID = null;
        }
        clearBossEntities();
        try { RaidSpawner.unregisterSessionById(this.raidId); } catch (Throwable ex) {}
        try { PixelmonBossAttackListener.unregisterSessionTouched(this.raidId); } catch (Throwable ignored) {}
        pendingBattles.clear();
        players.clear();
        damageTracker.clear();
        fatigueMap.clear();
        joinTimes.clear();
        setState(State.IDLE);
        isSpawning = false;
        setBattleActive(false);
        spawnFailureCount = 0;
    }

    public void finishRaid(boolean victory, UUID killerId) {
        for(ServerPlayerEntity pl : world.getServer().getPlayerList().getPlayers()) { try { BattleRegistry.getBattle(pl).endBattle(); } catch(Throwable t){} }

        if(markRewardsDistributedIfNot()) {
            RaidRewardHandler.broadcastLeaderboard(this);
            RaidRewardHandler.distributeRewards(this, victory, killerId);
            RaidSaveData.get(world).saveLastLeaderboard(damageTracker.getAllDamage(), world);

            if(victory) {
                PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
                if(cfg.isSoundEnabled()) world.playSound(null, getCenter(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, cfg.getSoundVolume(), 1.0f);
                world.getPlayers(p->true).forEach(p->p.sendMessage(new StringTextComponent(cfg.getMsgRaidWin()), p.getUUID()));
                RaidSaveData.get(world).incrementWinStreak();

                boolean actuallyLeveledUp = PixelmonRaidConfig.getInstance().incrementDifficultyOnWin();
                if (actuallyLeveledUp) {
                    world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§e§l⚠ RAID DIFFICULTY INCREASED TO LEVEL " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + "!"), ChatType.SYSTEM, Util.NIL_UUID);
                } else {
                    world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§b§l✯ MAXIMUM DIFFICULTY CONQUERED! ✯"), ChatType.SYSTEM, Util.NIL_UUID);
                    world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§7You have proven yourselves worthy of the highest tier!"), ChatType.SYSTEM, Util.NIL_UUID);
                }
            } else {
                if(PixelmonRaidConfig.getInstance().isSoundEnabled()) world.playSound(null, getCenter(), SoundEvents.NOTE_BLOCK_PLING, SoundCategory.MASTER, PixelmonRaidConfig.getInstance().getSoundVolume(), 1.0f);
                world.getPlayers(p->true).forEach(p->p.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgRaidLoss()), p.getUUID()));
                RaidSaveData.get(world).resetWinStreak();
                PixelmonRaidConfig.getInstance().decrementDifficultyOnLoss();
                world.getServer().getPlayerList().broadcastMessage(new StringTextComponent("§c§l🔻 RAID FAILED! Difficulty Decreased to Level " + PixelmonRaidConfig.getInstance().getRaidDifficulty() + " 🔻"), ChatType.SYSTEM, Util.NIL_UUID);
            }
        }
        long interval = 20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds();
        RaidSaveData.get(world).setNextRaidTick(world.getGameTime() + interval);
        setState(State.COMPLETED);
    }

    public synchronized boolean markRewardsDistributedIfNot() { if (this.rewardsDistributed) return false; this.rewardsDistributed = true; return true; }

    public void startWaitingNow(long currentTick) {
        cleanup();
        this.rewardsDistributed = false;
        startBattleNow();
    }

    public void tick(long tick) {
        if (state == State.IDLE && !isAutoRaidEnabled()) {
            if (tick % 20 == 0) updateBossBarImmediate();
            return;
        }
        if (state == State.PAUSED) { updateBossBarInfo(0, false); return; }

        if (!spawnDelays.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = spawnDelays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int val = entry.getValue() - 1;
                if (val <= 0) {
                    executeStartBattle(entry.getKey());
                    it.remove();
                } else {
                    entry.setValue(val);
                }
            }
        }

        if (tick % 100 == 0) cleanStaleCopies();
        switch (state) {
            case IDLE:
                long next = RaidSaveData.get(world).getNextRaidTick();
                long now = world.getGameTime();
                long remaining = next - now;

                if (tick % 20 == 0) {
                    updateBossBarImmediate();
                }

                if (System.currentTimeMillis() - lastBroadcastTime > 5000) {
                    if (remaining >= 5980 && remaining <= 6020) {
                        StringTextComponent baseMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgSpawning5Min() + " ");
                        IFormattableTextComponent clickMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgClickToWarp())
                                .setStyle(Style.EMPTY
                                        .withColor(Color.fromRgb(0x55FFFF))
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raid warp"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("§eClick to teleport to the Raid!")))
                                );
                        world.getServer().getPlayerList().broadcastMessage(baseMsg.append(clickMsg), ChatType.SYSTEM, Util.NIL_UUID);
                        lastBroadcastTime = System.currentTimeMillis();
                    }
                    if (remaining >= 1180 && remaining <= 1220) {
                        StringTextComponent baseMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgSpawning1Min() + " ");
                        IFormattableTextComponent clickMsg = new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgClickToWarp())
                                .setStyle(Style.EMPTY
                                        .withColor(Color.fromRgb(0x55FFFF))
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raid warp"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("§eClick to teleport to the Raid!")))
                                );
                        world.getServer().getPlayerList().broadcastMessage(baseMsg.append(clickMsg), ChatType.SYSTEM, Util.NIL_UUID);
                        lastBroadcastTime = System.currentTimeMillis();
                    }
                }

                if (next == -1 || (next < tick && next != 0)) RaidSaveData.get(world).setNextRaidTick(tick + (20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds()));
                else if (tick >= next) startBattleNow();
                break;
            case IN_BATTLE:
                int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
                long durationTicks = 20L * PixelmonRaidConfig.getInstance().getRaidDurationForDifficulty(diff);
                long elapsed = tick - this.battleStartTick;
                long rem = durationTicks - elapsed;
                int intervalSeconds = PixelmonRaidConfig.getInstance().getHpBroadcastIntervalSeconds();
                if (intervalSeconds > 0) {
                    long intervalTicks = intervalSeconds * 20L;
                    if (tick % intervalTicks == 0 && tick != lastHpBroadcastTick) {
                        lastHpBroadcastTick = tick;
                        float pct = (float)totalRaidHP / (float)Math.max(1, maxRaidHP) * 100f;
                        String color = pct > 50 ? "§a" : (pct > 20 ? "§e" : "§c");

                        String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdate()
                                .replace("%boss%", currentBossName)
                                .replace("%color%", color)
                                .replace("%hp%", String.valueOf(totalRaidHP))
                                .replace("%maxhp%", String.valueOf(maxRaidHP))
                                .replace("%pct%", String.format("%.1f", pct));

                        world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(msg), ChatType.SYSTEM, Util.NIL_UUID);
                    }
                }

                if (rem <= 0) { setState(State.SUDDEN_DEATH); }
                else { bossTickLogic(tick); updateBossBarInfo(rem, false); }
                break;
            case SUDDEN_DEATH:
                long sdDurationTicks = 20L * PixelmonRaidConfig.getInstance().getSuddenDeathDurationSeconds();
                long sdRem = sdDurationTicks - (tick - this.suddenDeathStartTick);

                int sdInterval = PixelmonRaidConfig.getInstance().getHpBroadcastIntervalSeconds();
                if (sdInterval > 0) {
                    long intervalTicks = sdInterval * 20L;
                    if (tick % intervalTicks == 0 && tick != lastHpBroadcastTick) {
                        lastHpBroadcastTick = tick;
                        float pct = (float)totalRaidHP / (float)Math.max(1, maxRaidHP) * 100f;
                        String color = pct > 50 ? "§a" : (pct > 20 ? "§e" : "§c");

                        String msg = PixelmonRaidConfig.getInstance().getMsgBossHpUpdateSuddenDeath()
                                .replace("%boss%", currentBossName)
                                .replace("%color%", color)
                                .replace("%hp%", String.valueOf(totalRaidHP))
                                .replace("%maxhp%", String.valueOf(maxRaidHP))
                                .replace("%pct%", String.format("%.1f", pct));

                        world.getServer().getPlayerList().broadcastMessage(new StringTextComponent(msg), ChatType.SYSTEM, Util.NIL_UUID);
                    }
                }

                if (sdRem <= 0) finishRaid(false, null);
                else { bossTickLogic(tick); updateBossBarInfo(sdRem, true); }
                break;
            case COMPLETED:
                cleanup();
                RaidSaveData.get(world).setNextRaidTick(tick + (20L * PixelmonRaidConfig.getInstance().getRaidIntervalSeconds()));
                setState(State.IDLE);
                break;
        }
    }

    private void cleanStaleCopies() {
        if (activeCopyUUIDs.isEmpty()) return;
        Iterator<UUID> it = activeCopyUUIDs.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = world.getEntity(uuid);
            if (entity == null) { it.remove(); continue; }
            if (entity instanceof PixelmonEntity) {
                PixelmonEntity pe = (PixelmonEntity) entity;
                if (pe.battleController == null && pe.tickCount > 300) { pe.remove(); it.remove(); }
            }
        }
    }

    private void bossTickLogic(long tick) {
        boolean alive = false;
        if (templateUUID != null) {
            Entity e = world.getEntity(templateUUID);
            if (e != null && e.isAlive()) alive = true;
        }

        if (!alive && !isSpawning) {
            BlockPos center = getCenter();
            if (world.isLoaded(center)) {
                if (!RaidSpawner.spawnBoss(this)) { }
            }
        }

        if(tick%10==0) updateBossName();
        if(tick%20==0) {
            updateActionBars();
            updateHologram();
        }

        if (state == State.IN_BATTLE || state == State.SUDDEN_DEATH) {
            if (templateUUID != null) {
                Entity e = world.getEntity(templateUUID);
                if (e instanceof PixelmonEntity) {
                    PixelmonEntity pe = (PixelmonEntity) e;
                    try {
                        float pct = (float) totalRaidHP / Math.max(1, maxRaidHP);
                        float targetHealth = Math.max(1.0f, pe.getMaxHealth() * pct);
                        pe.setHealth(targetHealth);
                        if (tick % 20 == 0 && pe.getPokemon() != null) {
                            pe.getPokemon().setHealth((int) Math.max(1.0f, pe.getPokemon().getMaxHealth() * pct));
                        }
                    } catch (Throwable ex) {}
                }
            }
        }

        if (state==State.SUDDEN_DEATH || totalRaidHP < maxRaidHP*0.25) {
            if (templateUUID != null) {
                Entity e = world.getEntity(templateUUID);
                if(e!=null) world.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY()+5, e.getZ(), 10, 0.5, 0.5, 0.5, 0.05);
            }
        }
        if (!hasEnraged && totalRaidHP <= (maxRaidHP * 0.50)) {
            hasEnraged = true;
            world.getPlayers(p -> true).forEach(p -> p.sendMessage(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgEnrage()), p.getUUID()));
            world.playSound(null, getCenter(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 100.0f, 1.0f);
        }
    }
}