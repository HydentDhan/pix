package com.example.PixelmonRaid;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RaidSaveData extends WorldSavedData {
    private static final String DATA_NAME = "pixelmonraid_data";

    private long nextRaidTick = -1;
    private int winStreak = 0;
    private BlockPos center = new BlockPos(0, 100, 0);
    private BlockPos playerSpawn = new BlockPos(0, 100, 0);
    private final Map<UUID, Integer> playerTokens = new ConcurrentHashMap<>();

    private final List<String> lastLeaderboard = new ArrayList<>();

    private final Map<UUID, RaidPlayerStats> allTimeStats = new ConcurrentHashMap<>();

    public RaidSaveData() {
        super(DATA_NAME);
    }

    public static RaidSaveData get(ServerWorld world) {
        return world.getDataStorage().computeIfAbsent(RaidSaveData::new, DATA_NAME);
    }

    public static class RaidPlayerStats {
        public long totalDamage = 0;
        public int kills = 0;
        public int raidsJoined = 0;
        public String lastKnownName = "Unknown";
    }

    public void incrementRaidsJoined(UUID playerId) {
        RaidPlayerStats stats = allTimeStats.computeIfAbsent(playerId, k -> new RaidPlayerStats());
        stats.raidsJoined++;
        setDirty();
    }

    public void updateStats(UUID playerId, String name, long damage, boolean isKill) {
        RaidPlayerStats stats = allTimeStats.computeIfAbsent(playerId, k -> new RaidPlayerStats());
        stats.lastKnownName = name;
        stats.totalDamage += damage;
        if(isKill) stats.kills++;
        setDirty();
    }

    public List<Map.Entry<UUID, RaidPlayerStats>> getSortedAllTimeLeaderboard() {
        return allTimeStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalDamage, a.getValue().totalDamage))
                .collect(Collectors.toList());
    }


    public long getNextRaidTick() { return nextRaidTick; }
    public void setNextRaidTick(long tick) { this.nextRaidTick = tick; setDirty(); }

    public int getWinStreak() { return winStreak; }
    public void incrementWinStreak() { this.winStreak++; setDirty(); }
    public void resetWinStreak() { this.winStreak = 0; setDirty(); }

    public BlockPos getCenter() { return center; }
    public void setCenter(BlockPos pos) { this.center = pos; setDirty(); }

    public BlockPos getPlayerSpawn() { return playerSpawn; }
    public void setPlayerSpawn(BlockPos pos) { this.playerSpawn = pos; setDirty(); }

    public int getTokens(UUID id) { return playerTokens.getOrDefault(id, 0); }
    public void addTokens(UUID id, int amount) { playerTokens.put(id, getTokens(id) + amount); setDirty(); }

    public boolean removeTokens(UUID id, int amount) {
        int current = getTokens(id);
        if (current >= amount) {
            playerTokens.put(id, current - amount);
            setDirty();
            return true;
        }
        return false;
    }

    public void saveLastLeaderboard(Map<UUID, Integer> damageMap, ServerWorld world) {
        lastLeaderboard.clear();
        damageMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .forEach(e -> {
                    String name = "Unknown";
                    net.minecraft.entity.player.PlayerEntity pe = world.getPlayerByUUID(e.getKey());
                    if (pe != null) name = pe.getGameProfile().getName();
                    else {
                        RaidPlayerStats cached = allTimeStats.get(e.getKey());
                        if(cached != null) name = cached.lastKnownName;
                    }

                    lastLeaderboard.add(name + ":" + e.getValue());
                    updateStats(e.getKey(), name, e.getValue(), false);
                });
        setDirty();
    }

    public void recordKill(UUID killerId) {
        if(killerId != null && allTimeStats.containsKey(killerId)) {
            allTimeStats.get(killerId).kills++;
            setDirty();
        }
    }

    public List<String> getLastLeaderboard() { return new ArrayList<>(lastLeaderboard); }

    @Override
    public void load(CompoundNBT nbt) {
        nextRaidTick = nbt.getLong("NextRaidTick");
        winStreak = nbt.getInt("WinStreak");
        if (nbt.contains("CenterX")) center = new BlockPos(nbt.getInt("CenterX"), nbt.getInt("CenterY"), nbt.getInt("CenterZ"));
        if (nbt.contains("SpawnX")) playerSpawn = new BlockPos(nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ"));

        ListNBT tokenList = nbt.getList("Tokens", 10);
        playerTokens.clear();
        for (int i = 0; i < tokenList.size(); i++) {
            CompoundNBT t = tokenList.getCompound(i);
            playerTokens.put(t.getUUID("UUID"), t.getInt("Amount"));
        }

        ListNBT lbList = nbt.getList("LastLeaderboard", 8);
        lastLeaderboard.clear();
        for(int i=0; i<lbList.size(); i++) {
            lastLeaderboard.add(lbList.getString(i));
        }

        ListNBT statsList = nbt.getList("AllTimeStats", 10);
        allTimeStats.clear();
        for(int i=0; i<statsList.size(); i++) {
            CompoundNBT tag = statsList.getCompound(i);
            RaidPlayerStats s = new RaidPlayerStats();
            s.lastKnownName = tag.getString("Name");
            s.totalDamage = tag.getLong("Damage");
            s.kills = tag.getInt("Kills");
            s.raidsJoined = tag.getInt("Raids");
            allTimeStats.put(tag.getUUID("UUID"), s);
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        nbt.putLong("NextRaidTick", nextRaidTick);
        nbt.putInt("WinStreak", winStreak);
        nbt.putInt("CenterX", center.getX());
        nbt.putInt("CenterY", center.getY());
        nbt.putInt("CenterZ", center.getZ());
        nbt.putInt("SpawnX", playerSpawn.getX());
        nbt.putInt("SpawnY", playerSpawn.getY());
        nbt.putInt("SpawnZ", playerSpawn.getZ());

        ListNBT tokenList = new ListNBT();
        playerTokens.forEach((k, v) -> {
            CompoundNBT t = new CompoundNBT();
            t.putUUID("UUID", k);
            t.putInt("Amount", v);
            tokenList.add(t);
        });
        nbt.put("Tokens", tokenList);

        ListNBT lbList = new ListNBT();
        for(String s : lastLeaderboard) lbList.add(net.minecraft.nbt.StringNBT.valueOf(s));
        nbt.put("LastLeaderboard", lbList);

        ListNBT statsList = new ListNBT();
        allTimeStats.forEach((k, v) -> {
            CompoundNBT tag = new CompoundNBT();
            tag.putUUID("UUID", k);
            tag.putString("Name", v.lastKnownName);
            tag.putLong("Damage", v.totalDamage);
            tag.putInt("Kills", v.kills);
            tag.putInt("Raids", v.raidsJoined);
            statsList.add(tag);
        });
        nbt.put("AllTimeStats", statsList);

        return nbt;
    }
}