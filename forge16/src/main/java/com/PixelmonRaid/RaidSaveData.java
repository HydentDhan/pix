package com.PixelmonRaid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

public class RaidSaveData extends WorldSavedData {
   private static final String DATA_NAME = "pixelmonraid_data";
   public static final int CURRENT_DATA_VERSION = 2;
   private int dataVersion = 2;
   private long nextRaidTick = -1L;
   private int winStreak = 0;
   private BlockPos center = new BlockPos(0, 100, 0);
   private BlockPos playerSpawn = new BlockPos(0, 100, 0);
   private final Map<UUID, Integer> playerTokens = new ConcurrentHashMap<>();
   private final List<String> lastLeaderboard = new ArrayList<>();
   private final Map<UUID, RaidSaveData.RaidPlayerStats> allTimeStats = new ConcurrentHashMap<>();

   public RaidSaveData() {
      super("pixelmonraid_data");
   }

   public static RaidSaveData get(ServerWorld world) {
      return (RaidSaveData)world.getDataStorage().computeIfAbsent(RaidSaveData::new, "pixelmonraid_data");
   }

   public void incrementRaidsJoined(UUID playerId) {
      RaidSaveData.RaidPlayerStats stats = (RaidSaveData.RaidPlayerStats)this.allTimeStats.computeIfAbsent(playerId, (k) -> {
         return new RaidSaveData.RaidPlayerStats();
      });
      ++stats.raidsJoined;
      this.setDirty();
   }

   public void updateStats(UUID playerId, String name, long damage, boolean isKill) {
      RaidSaveData.RaidPlayerStats stats = (RaidSaveData.RaidPlayerStats)this.allTimeStats.computeIfAbsent(playerId, (k) -> {
         return new RaidSaveData.RaidPlayerStats();
      });
      stats.lastKnownName = name;
      stats.totalDamage += damage;
      if (isKill) {
         ++stats.kills;
      }

      this.setDirty();
   }

   public List<Entry<UUID, RaidSaveData.RaidPlayerStats>> getSortedAllTimeLeaderboard() {
      return (List)this.allTimeStats.entrySet().stream().sorted((a, b) -> {
         return Long.compare(((RaidSaveData.RaidPlayerStats)b.getValue()).totalDamage, ((RaidSaveData.RaidPlayerStats)a.getValue()).totalDamage);
      }).collect(Collectors.toList());
   }

   public long getNextRaidTick() {
      return this.nextRaidTick;
   }

   public void setNextRaidTick(long tick) {
      this.nextRaidTick = tick;
      this.setDirty();
   }

   public int getWinStreak() {
      return this.winStreak;
   }

   public void incrementWinStreak() {
      ++this.winStreak;
      this.setDirty();
   }

   public void resetWinStreak() {
      this.winStreak = 0;
      this.setDirty();
   }

   public BlockPos getCenter() {
      return this.center;
   }

   public void setCenter(BlockPos pos) {
      this.center = pos;
      this.setDirty();
   }

   public BlockPos getPlayerSpawn() {
      return this.playerSpawn;
   }

   public void setPlayerSpawn(BlockPos pos) {
      this.playerSpawn = pos;
      this.setDirty();
   }

   public int getTokens(UUID id) {
      return (Integer)this.playerTokens.getOrDefault(id, 0);
   }

   public void addTokens(UUID id, int amount) {
      this.playerTokens.put(id, this.getTokens(id) + amount);
      this.setDirty();
   }

   public boolean removeTokens(UUID id, int amount) {
      int current = this.getTokens(id);
      if (current >= amount) {
         this.playerTokens.put(id, current - amount);
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public void saveLastLeaderboard(Map<UUID, Integer> damageMap, ServerWorld world) {
      this.lastLeaderboard.clear();
      damageMap.entrySet().stream().sorted(Entry.comparingByValue(Comparator.reverseOrder())).limit(5L).forEach((e) -> {
         String name = "Unknown";
         PlayerEntity pe = world.getPlayerByUUID((UUID)e.getKey());
         if (pe != null) {
            name = pe.getGameProfile().getName();
         } else {
            RaidSaveData.RaidPlayerStats cached = (RaidSaveData.RaidPlayerStats)this.allTimeStats.get(e.getKey());
            if (cached != null) {
               name = cached.lastKnownName;
            }
         }

         this.lastLeaderboard.add(name + ":" + e.getValue());
         this.updateStats((UUID)e.getKey(), name, (long)(Integer)e.getValue(), false);
      });
      this.setDirty();
   }

   public void recordKill(UUID killerId) {
      if (killerId != null && this.allTimeStats.containsKey(killerId)) {
         ++((RaidSaveData.RaidPlayerStats)this.allTimeStats.get(killerId)).kills;
         this.setDirty();
      }
   }

   public List<String> getLastLeaderboard() {
      return new ArrayList<>(this.lastLeaderboard);
   }

   public void load(CompoundNBT nbt) {
      this.dataVersion = nbt.contains("DataVersion") ? nbt.getInt("DataVersion") : 1;
      this.nextRaidTick = nbt.getLong("NextRaidTick");
      this.winStreak = nbt.getInt("WinStreak");
      if (nbt.contains("CenterX")) {
         this.center = new BlockPos(nbt.getInt("CenterX"), nbt.getInt("CenterY"), nbt.getInt("CenterZ"));
      }

      if (nbt.contains("SpawnX")) {
         this.playerSpawn = new BlockPos(nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ"));
      }

      this.playerTokens.clear();

      ListNBT statsList;
      int i;
      CompoundNBT tag;
      try {
         statsList = nbt.getList("Tokens", 10);
         for(i = 0; i < statsList.size(); ++i) {
            tag = statsList.getCompound(i);
            this.playerTokens.put(tag.getUUID("UUID"), tag.getInt("Amount"));
         }
      } catch (Exception var8) {
         System.err.println("[PixelmonRaid] Could not load old Token data due to format change. Resetting tokens.");
      }

      this.lastLeaderboard.clear();

      try {
         statsList = nbt.getList("LastLeaderboard", 8);
         for(i = 0; i < statsList.size(); ++i) {
            this.lastLeaderboard.add(statsList.getString(i));
         }
      } catch (Exception var7) {
         System.err.println("[PixelmonRaid] Could not load old Leaderboard data. Resetting leaderboard strings.");
      }

      this.allTimeStats.clear();

      try {
         statsList = nbt.getList("AllTimeStats", 10);
         for(i = 0; i < statsList.size(); ++i) {
            tag = statsList.getCompound(i);
            RaidSaveData.RaidPlayerStats s = new RaidSaveData.RaidPlayerStats();
            s.lastKnownName = tag.getString("Name");
            s.totalDamage = tag.getLong("Damage");
            s.kills = tag.getInt("Kills");
            s.raidsJoined = tag.getInt("Raids");
            this.allTimeStats.put(tag.getUUID("UUID"), s);
         }
      } catch (Exception var6) {
         System.err.println("[PixelmonRaid] Could not load old Player Stats data. Resetting all-time stats.");
      }

      if (this.dataVersion < 2) {
         System.out.println("[PixelmonRaid] Migrated save data to version 2");
         this.dataVersion = 2;
         this.setDirty();
      }

   }

   public CompoundNBT save(CompoundNBT nbt) {
      nbt.putInt("DataVersion", 2);
      nbt.putLong("NextRaidTick", this.nextRaidTick);
      nbt.putInt("WinStreak", this.winStreak);
      nbt.putInt("CenterX", this.center.getX());
      nbt.putInt("CenterY", this.center.getY());
      nbt.putInt("CenterZ", this.center.getZ());
      nbt.putInt("SpawnX", this.playerSpawn.getX());
      nbt.putInt("SpawnY", this.playerSpawn.getY());
      nbt.putInt("SpawnZ", this.playerSpawn.getZ());

      ListNBT tokenList = new ListNBT();
      this.playerTokens.forEach((k, v) -> {
         CompoundNBT t = new CompoundNBT();
         t.putUUID("UUID", k);
         t.putInt("Amount", v);
         tokenList.add(t);
      });
      nbt.put("Tokens", tokenList);

      ListNBT lbList = new ListNBT();
      Iterator<String> var4 = this.lastLeaderboard.iterator();
      while(var4.hasNext()) {
         String s = var4.next();
         lbList.add(StringNBT.valueOf(s));
      }

      nbt.put("LastLeaderboard", lbList);

      ListNBT statsList = new ListNBT();
      this.allTimeStats.forEach((k, v) -> {
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

   public static class RaidPlayerStats {
      public long totalDamage = 0L;
      public int kills = 0;
      public int raidsJoined = 0;
      public String lastKnownName = "Unknown";
   }
}