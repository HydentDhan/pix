package com.PixelmonRaid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class PixelmonRaidConfig {
   private static PixelmonRaidConfig instance;
   private static final File configFile = new File("config/pixelmonraid.yml");
   private static final Yaml yamlLoader = createYamlLoader();
   private static final Yaml yamlWriter = createYamlWriter();

   private boolean enableInternalShop = false;
   private String msgShopDisabled = "&cThe internal Raid Shop is currently disabled. Please use the main server shop!";
   private String msgRaidStart = "&d[Raid Boss] &fA wild boss has appeared! Type &e/raid join &fto fight!";
   private String msgRaidWin = "&6&l[★] RAID VICTORY! &eThe boss has been defeated!";
   private String msgRaidLoss = "&c&l[✖] RAID FAILED! &7The boss escaped...";
   private String msgEnrage = "&c&l[⚡] THE BOSS IS ENRAGED! [⚡]";
   private String msgJoin = "&e&l[⚠] BATTLE INITIATING... PREPARE YOURSELF! [⚠]";
   private String msgCooldown = "&c&l[⌛] WAITING FOR STAMINA... &7(%s)";
   private String msgKillshot = "&4&l[☠] %player% DEALT THE FINAL BLOW! [☠]";
   private String msgSpawning5Min = "&d[Raid Boss] &eA massive energy signature detected. Spawning in 5 minutes!";
   private String msgSpawning1Min = "&d[Raid Boss] &eThe rift is opening! Spawning in 1 minute!";
   private String msgClickToWarp = "&b[Click Here to Warp]";
   private String msgBossHpUpdate = "&d[Raid Boss] &7%boss% HP: %color%%hp%&7/&c%maxhp% &8(%pct%%)";
   private String msgBossHpUpdateSuddenDeath = "&4[Raid Boss] &c%boss% &7HP: %color%%hp%&7/&c%maxhp% &8(%pct%%)";
   private String msgReloadTimerSynced = "&a&l✔ Configuration Reloaded & Timer Synced!";
   private String msgReload = "&a&l✔ Configuration Reloaded!";
   private boolean showIdleTimerBar = true;
   private String uiTimerBarTitle = "&b&lNext Raid In: &f%time%";
   private String uiTimerBarPaused = "&c&lRaid Spawns Paused";
   private String discordWebhookUrl = "";
   private List<String> bossListLevel1 = new ArrayList<>(Arrays.asList("Bulbasaur", "Charmander", "Squirtle"));
   private List<String> bossListLevel2 = new ArrayList<>(Arrays.asList("Ivysaur", "Charmeleon", "Wartortle"));
   private List<String> bossListLevel3 = new ArrayList<>(Arrays.asList("Venusaur", "Charizard", "Blastoise"));
   private List<String> bossListLevel4 = new ArrayList<>(Arrays.asList("Dragonite", "Tyranitar", "Metagross"));
   private List<String> bossListLevel5 = new ArrayList<>(Arrays.asList("Mewtwo", "Rayquaza", "Eternatus"));
   private List<String> bossSpeciesList = new ArrayList<>(Arrays.asList("Charizard", "Blastoise", "Venusaur"));
   private List<String> bossSpeciesListTier5 = new ArrayList<>(Arrays.asList("Rayquaza", "Mewtwo"));
   private int raidDifficulty = 1;
   private boolean silentMode = false;
   private double bossScaleFactor = 10.0D;
   private boolean autoRaidEnabled = false;
   private int bossHpLevel1 = 20000;
   private int bossHpLevel2 = 45000;
   private int bossHpLevel3 = 70000;
   private int bossHpLevel4 = 95000;
   private int bossHpLevel5 = 120000;
   private double extraHpMultiplierPerWinAtMaxLevel = 0.1D;
   private boolean bossBarSpacer = true;
   private boolean soundsEnabled = true;
   private float soundVolume = 1.0F;
   private boolean hologramEnabled = true;
   private double holoX = 0.0D;
   private double holoY = 100.0D;
   private double holoZ = 0.0D;
   private String holoWorld = "minecraft:overworld";
   private boolean dynamicDifficulty = true;
   private double difficultyScalePerPlayer = 0.15D;
   private int raidIntervalSeconds = 300;
   private int baseSuddenDeathSeconds = 120;
   private int raidDurationSeconds = 300;
   private int rejoinCooldownSeconds = 10;
   private int hpBroadcastIntervalSeconds = 60;
   private String uiServerName = "&d&lMY SERVER RAIDS";
   private String uiThemeColor = "&5";
   private String uiLogoItem = "minecraft:nether_star";
   private String uiBorderItem = "minecraft:purple_stained_glass_pane";
   private String uiLeaderboardTitle = "&8&lAll-Time Raid Stats";
   private String uiCurrentLeaderboardTitle = "&8&lCurrent Raid Stats";
   private String uiLastLeaderboardTitle = "&8&lLast Raid Stats";
   private int raidDurationLevel1 = 300;
   private int raidDurationLevel2 = 360;
   private int raidDurationLevel3 = 420;
   private int raidDurationLevel4 = 480;
   private int raidDurationLevel5 = 600;
   private double damageCapPercentage = 0.1D;
   private List<ShopItem> raidTokenShop = new ArrayList<>();
   public TierRewardConfig tier1 = new TierRewardConfig();
   public TierRewardConfig tier2 = new TierRewardConfig();
   public TierRewardConfig tier3 = new TierRewardConfig();
   public TierRewardConfig tier4 = new TierRewardConfig();
   public TierRewardConfig tier5 = new TierRewardConfig();

   public static PixelmonRaidConfig getInstance() {
      if (instance == null) {
         instance = new PixelmonRaidConfig();
         instance.load();
      }
      return instance;
   }

   public static void loadConfig() {
      getInstance().load();
   }

   public static void reload() {
      if (instance != null) {
         instance.load();
      }
   }

   private static Yaml createYamlWriter() {
      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      options.setIndent(2);
      options.setIndicatorIndent(1);
      options.setIndentWithIndicator(true);
      options.setPrettyFlow(false);
      options.setWidth(Integer.MAX_VALUE);

      Representer representer = new Representer(options) {
         @Override
         protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            NodeTuple tuple = super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            if (propertyValue instanceof List) {
               Node valueNode = tuple.getValueNode();
               if (valueNode instanceof SequenceNode && !"raidTokenShop".equals(property.getName())) {
                  ((SequenceNode) valueNode).setFlowStyle(DumperOptions.FlowStyle.FLOW);
               }
            }
            return tuple;
         }
      };
      representer.addClassTag(PixelmonRaidConfig.class, Tag.MAP);
      Yaml yaml = new Yaml(representer, options);
      yaml.setBeanAccess(BeanAccess.FIELD);
      return yaml;
   }

   private static Yaml createYamlLoader() {
      Yaml yaml = new Yaml(new Constructor(PixelmonRaidConfig.class, new LoaderOptions()));
      yaml.setBeanAccess(BeanAccess.FIELD);
      return yaml;
   }

   private void load() {
      if (!configFile.exists()) {
         addDefaults();
         save();
         return;
      }
      try {
         Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
         Throwable var2 = null;
         try {
            PixelmonRaidConfig data = yamlLoader.load(reader);
            if (data == null) return;
            applyLoadedData(data);
         } catch (Throwable var27) {
            var2 = var27;
            throw var27;
         } finally {
            if (reader != null) {
               if (var2 != null) {
                  try {
                     reader.close();
                  } catch (Throwable var24) {
                     var2.addSuppressed(var24);
                  }
               } else {
                  reader.close();
               }
            }
         }
      } catch (Exception var29) {
         addDefaults();
         save();
      }
   }

   private void applyLoadedData(PixelmonRaidConfig data) {
      if (data.tier1 != null) {
         tier1 = data.tier1;
         tier2 = data.tier2;
         tier3 = data.tier3;
         tier4 = data.tier4;
         tier5 = data.tier5;
      }

      if (data.raidTokenShop != null && !data.raidTokenShop.isEmpty()) {
         raidTokenShop = data.raidTokenShop;
      } else if (raidTokenShop.isEmpty()) {
         addDefaultShop();
      }

      enableInternalShop = data.enableInternalShop;
      if (data.msgShopDisabled != null) msgShopDisabled = data.msgShopDisabled;
      if (data.msgRaidStart != null) msgRaidStart = data.msgRaidStart;
      if (data.msgRaidWin != null) msgRaidWin = data.msgRaidWin;
      if (data.msgRaidLoss != null) msgRaidLoss = data.msgRaidLoss;
      if (data.msgEnrage != null) msgEnrage = data.msgEnrage;
      if (data.msgJoin != null) msgJoin = data.msgJoin;
      if (data.msgCooldown != null) msgCooldown = data.msgCooldown;
      if (data.msgKillshot != null) msgKillshot = data.msgKillshot;
      if (data.msgSpawning5Min != null) msgSpawning5Min = data.msgSpawning5Min;
      if (data.msgSpawning1Min != null) msgSpawning1Min = data.msgSpawning1Min;
      if (data.msgClickToWarp != null) msgClickToWarp = data.msgClickToWarp;
      if (data.msgBossHpUpdate != null) msgBossHpUpdate = data.msgBossHpUpdate;
      if (data.msgBossHpUpdateSuddenDeath != null) msgBossHpUpdateSuddenDeath = data.msgBossHpUpdateSuddenDeath;
      if (data.msgReloadTimerSynced != null) msgReloadTimerSynced = data.msgReloadTimerSynced;
      if (data.msgReload != null) msgReload = data.msgReload;

      if (data.uiTimerBarTitle != null) {
         showIdleTimerBar = data.showIdleTimerBar;
         uiTimerBarTitle = data.uiTimerBarTitle;
      }
      if (data.uiTimerBarPaused != null) uiTimerBarPaused = data.uiTimerBarPaused;

      discordWebhookUrl = data.discordWebhookUrl;
      if (data.bossListLevel1 != null) bossListLevel1 = data.bossListLevel1;
      if (data.bossListLevel2 != null) bossListLevel2 = data.bossListLevel2;
      if (data.bossListLevel3 != null) bossListLevel3 = data.bossListLevel3;
      if (data.bossListLevel4 != null) bossListLevel4 = data.bossListLevel4;
      if (data.bossListLevel5 != null) bossListLevel5 = data.bossListLevel5;
      if (data.bossSpeciesList != null) bossSpeciesList = data.bossSpeciesList;
      if (data.bossSpeciesListTier5 != null) bossSpeciesListTier5 = data.bossSpeciesListTier5;

      raidDifficulty = data.raidDifficulty;
      silentMode = data.silentMode;
      if (data.bossScaleFactor > 0.0D) bossScaleFactor = data.bossScaleFactor;
      autoRaidEnabled = data.autoRaidEnabled;
      if (data.bossHpLevel1 > 0) bossHpLevel1 = data.bossHpLevel1;
      if (data.bossHpLevel2 > 0) bossHpLevel2 = data.bossHpLevel2;
      if (data.bossHpLevel3 > 0) bossHpLevel3 = data.bossHpLevel3;
      if (data.bossHpLevel4 > 0) bossHpLevel4 = data.bossHpLevel4;
      if (data.bossHpLevel5 > 0) bossHpLevel5 = data.bossHpLevel5;
      if (data.extraHpMultiplierPerWinAtMaxLevel >= 0.0D) extraHpMultiplierPerWinAtMaxLevel = data.extraHpMultiplierPerWinAtMaxLevel;

      soundsEnabled = data.soundsEnabled;
      if (data.soundVolume > 0.0F) soundVolume = data.soundVolume;
      bossBarSpacer = data.bossBarSpacer;
      hologramEnabled = data.hologramEnabled;
      holoX = data.holoX;
      holoY = data.holoY;
      holoZ = data.holoZ;
      if (data.holoWorld != null) holoWorld = data.holoWorld;

      raidIntervalSeconds = data.raidIntervalSeconds;
      baseSuddenDeathSeconds = data.baseSuddenDeathSeconds;
      raidDurationSeconds = data.raidDurationSeconds > 0 ? data.raidDurationSeconds : data.raidDurationLevel1;
      if (data.rejoinCooldownSeconds >= 0) rejoinCooldownSeconds = data.rejoinCooldownSeconds;
      if (data.hpBroadcastIntervalSeconds > 0) hpBroadcastIntervalSeconds = data.hpBroadcastIntervalSeconds;

      dynamicDifficulty = data.dynamicDifficulty;
      if (data.difficultyScalePerPlayer > 0.0D) difficultyScalePerPlayer = data.difficultyScalePerPlayer;

      if (data.uiServerName != null) uiServerName = data.uiServerName;
      if (data.uiThemeColor != null) uiThemeColor = data.uiThemeColor;
      if (data.uiLogoItem != null) uiLogoItem = data.uiLogoItem;
      if (data.uiBorderItem != null) uiBorderItem = data.uiBorderItem;
      if (data.uiLeaderboardTitle != null) uiLeaderboardTitle = data.uiLeaderboardTitle;
      if (data.uiCurrentLeaderboardTitle != null) uiCurrentLeaderboardTitle = data.uiCurrentLeaderboardTitle;
      if (data.uiLastLeaderboardTitle != null) uiLastLeaderboardTitle = data.uiLastLeaderboardTitle;

      if (data.raidDurationLevel1 > 0) raidDurationLevel1 = data.raidDurationLevel1;
      if (data.raidDurationLevel2 > 0) raidDurationLevel2 = data.raidDurationLevel2;
      if (data.raidDurationLevel3 > 0) raidDurationLevel3 = data.raidDurationLevel3;
      if (data.raidDurationLevel4 > 0) raidDurationLevel4 = data.raidDurationLevel4;
      if (data.raidDurationLevel5 > 0) raidDurationLevel5 = data.raidDurationLevel5;

      damageCapPercentage = data.damageCapPercentage;
   }

   private void addDefaultShop() {
      ShopItem s1 = new ShopItem();
      s1.itemID = "pixelmon:rare_candy";
      s1.price = 1;
      ShopItem s2 = new ShopItem();
      s2.itemID = "pixelmon:poke_ball";
      s2.price = 50;
      s2.nbt = "{PokeBallID:\"master_ball\"}";
      raidTokenShop.add(s1);
      raidTokenShop.add(s2);
   }

   private void addDefaults() {
      String[] bossHeldItems = {"pixelmon:oran_berry", "pixelmon:sitrus_berry", "pixelmon:lum_berry", "pixelmon:life_orb", "pixelmon:leftovers"};
      for (int i = 1; i <= 5; i++) {
         TierRewardConfig tier = new TierRewardConfig();
         RankReward r1 = new RankReward();
         r1.items.add("pixelmon:rare_candy 3");
         r1.commands.add("economy give %player% 3000");
         RankReward r2 = new RankReward();
         r2.items.add("pixelmon:rare_candy 2");
         r2.commands.add("economy give %player% 2000");
         RankReward r3 = new RankReward();
         r3.items.add("pixelmon:rare_candy 1");
         r3.commands.add("economy give %player% 1000");
         tier.winners.put("Winner_1", r1);
         tier.winners.put("Winner_2", r2);
         tier.winners.put("Winner_3", r3);
         tier.participation.items.add("pixelmon:potion 5");
         tier.participation.commands.add("economy give %player% 200");
         tier.killshot.items.add("pixelmon:poke_ball 1 {PokeBallID:\"master_ball\"}");
         tier.killshot.commands.add("economy give %player% 5000");
         tier.minTokens = i;
         tier.maxTokens = i + 2;
         tier.bossHeldItem = bossHeldItems[i - 1];
         setTier(i, tier);
      }
      addDefaultShop();
   }

   private void setTier(int i, TierRewardConfig tier) {
      switch (i) {
      case 1: tier1 = tier; break;
      case 2: tier2 = tier; break;
      case 3: tier3 = tier; break;
      case 4: tier4 = tier; break;
      case 5: tier5 = tier; break;
      }
   }

   public TierRewardConfig getTierRewards(int level) {
      switch (level) {
      case 1: return tier1;
      case 2: return tier2;
      case 3: return tier3;
      case 4: return tier4;
      case 5: return tier5;
      default: return tier1;
      }
   }

   public void save() {
      try {
         Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8);
         Throwable var2 = null;
         try {
            yamlWriter.dump(this, writer);
         } catch (Throwable var12) {
            var2 = var12;
            throw var12;
         } finally {
            if (writer != null) {
               if (var2 != null) {
                  try {
                     writer.close();
                  } catch (Throwable var11) {
                     var2.addSuppressed(var11);
                  }
               } else {
                  writer.close();
               }
            }
         }
      } catch (IOException var14) {
         var14.printStackTrace();
      }
   }

   private String formatColor(String str) {
      return str != null && !str.isEmpty() ? str.replace("&", "§") : "";
   }

   public boolean isInternalShopEnabled() { return enableInternalShop; }
   public String getMsgShopDisabled() { return formatColor(msgShopDisabled); }
   public String getMsgSpawning5Min() { return formatColor(msgSpawning5Min); }
   public String getMsgSpawning1Min() { return formatColor(msgSpawning1Min); }
   public String getMsgClickToWarp() { return formatColor(msgClickToWarp); }
   public String getMsgBossHpUpdate() { return formatColor(msgBossHpUpdate); }
   public String getMsgBossHpUpdateSuddenDeath() { return formatColor(msgBossHpUpdateSuddenDeath); }
   public String getMsgReloadTimerSynced() { return formatColor(msgReloadTimerSynced); }
   public String getMsgReload() { return formatColor(msgReload); }
   public boolean isShowIdleTimerBar() { return showIdleTimerBar; }
   public String getUiTimerBarTitle() { return formatColor(uiTimerBarTitle); }
   public String getUiTimerBarPaused() { return formatColor(uiTimerBarPaused); }
   public boolean isAutoRaidEnabled() { return autoRaidEnabled; }

   public void setAutoRaidEnabled(boolean v) {
      autoRaidEnabled = v;
      save();
   }

   public boolean isBossBarSpacerEnabled() { return bossBarSpacer; }
   public boolean isHologramEnabled() { return hologramEnabled; }

   public void setHologramEnabled(boolean v) {
      hologramEnabled = v;
      save();
   }

   public double getHoloX() { return holoX; }
   public double getHoloY() { return holoY; }
   public double getHoloZ() { return holoZ; }
   public String getHoloWorld() { return holoWorld; }

   public void setHoloLocation(double x, double y, double z, String world) {
      holoX = x;
      holoY = y;
      holoZ = z;
      holoWorld = world;
      save();
   }

   public String getMsgRaidStart() { return formatColor(msgRaidStart); }
   public String getMsgRaidWin() { return formatColor(msgRaidWin); }
   public String getMsgRaidLoss() { return formatColor(msgRaidLoss); }
   public String getMsgEnrage() { return formatColor(msgEnrage); }
   public String getMsgJoin() { return formatColor(msgJoin); }
   public String getMsgCooldown() { return formatColor(msgCooldown); }
   public String getMsgKillshot() { return formatColor(msgKillshot); }
   public boolean isDynamicDifficulty() { return dynamicDifficulty; }
   public double getDifficultyScale() { return difficultyScalePerPlayer; }
   public int getRejoinCooldownSeconds() { return rejoinCooldownSeconds; }
   public String getUiServerName() { return formatColor(uiServerName); }
   public String getUiThemeColor() { return formatColor(uiThemeColor); }
   public String getUiLogoItem() { return uiLogoItem; }
   public String getUiBorderItem() { return uiBorderItem; }
   public String getUiLeaderboardTitle() { return formatColor(uiLeaderboardTitle); }
   public String getUiCurrentLeaderboardTitle() { return formatColor(uiCurrentLeaderboardTitle); }
   public String getUiLastLeaderboardTitle() { return formatColor(uiLastLeaderboardTitle); }

   public void setRaidDurationSeconds(int seconds) {
      raidDurationSeconds = seconds;
      save();
   }

   public int getRaidDurationSeconds() {
      return raidDurationSeconds > 0 ? raidDurationSeconds : 300;
   }

   public int getRaidDurationForDifficulty(int difficulty) {
      switch (difficulty) {
      case 1: return raidDurationLevel1 > 0 ? raidDurationLevel1 : raidDurationSeconds;
      case 2: return raidDurationLevel2 > 0 ? raidDurationLevel2 : raidDurationSeconds;
      case 3: return raidDurationLevel3 > 0 ? raidDurationLevel3 : raidDurationSeconds;
      case 4: return raidDurationLevel4 > 0 ? raidDurationLevel4 : raidDurationSeconds;
      case 5: return raidDurationLevel5 > 0 ? raidDurationLevel5 : raidDurationSeconds;
      default: return raidDurationSeconds > 0 ? raidDurationSeconds : 300;
      }
   }

   public void setSuddenDeathSeconds(int seconds) {
      baseSuddenDeathSeconds = seconds;
      save();
   }

   public int getSuddenDeathDurationSeconds() {
      return baseSuddenDeathSeconds > 0 ? baseSuddenDeathSeconds : 120;
   }

   public void setRaidIntervalSeconds(int seconds) {
      raidIntervalSeconds = seconds;
      save();
   }

   public int getRaidIntervalSeconds() { return raidIntervalSeconds; }
   public int getHpBroadcastIntervalSeconds() { return hpBroadcastIntervalSeconds; }

   public boolean incrementDifficultyOnWin() {
      if (raidDifficulty < 5) {
         ++raidDifficulty;
         save();
         return true;
      }
      return false;
   }

   public void decrementDifficultyOnLoss() {
      if (raidDifficulty > 1) {
         --raidDifficulty;
         save();
      }
   }

   public void setRaidDifficulty(int d) {
      raidDifficulty = d;
      save();
   }

   public void setSilentMode(boolean silent) {
      silentMode = silent;
      save();
   }

   public String getDiscordWebhookUrl() { return discordWebhookUrl; }

   public String getRandomBossSpecies(int difficulty) {
      List<String> pool;
      switch (difficulty) {
      case 1: pool = bossListLevel1; break;
      case 2: pool = bossListLevel2; break;
      case 3: pool = bossListLevel3; break;
      case 4: pool = bossListLevel4; break;
      case 5: pool = bossListLevel5; break;
      default: pool = bossListLevel3;
      }
      return pool != null && !pool.isEmpty() ? pool.get(new Random().nextInt(pool.size())) : "Charizard";
   }

   public int getRaidDifficulty() { return raidDifficulty; }

   public int getBossHP(int difficulty, int winStreak) {
      int hp;
      switch (difficulty) {
      case 1: hp = bossHpLevel1 > 0 ? bossHpLevel1 : 20000; break;
      case 2: hp = bossHpLevel2 > 0 ? bossHpLevel2 : 45000; break;
      case 3: hp = bossHpLevel3 > 0 ? bossHpLevel3 : 70000; break;
      case 4: hp = bossHpLevel4 > 0 ? bossHpLevel4 : 95000; break;
      case 5: hp = bossHpLevel5 > 0 ? bossHpLevel5 : 120000; break;
      default: hp = 20000;
      }
      if (difficulty >= 5 && winStreak > 0 && extraHpMultiplierPerWinAtMaxLevel > 0.0D) {
         hp += (int)((double)hp * (double)winStreak * extraHpMultiplierPerWinAtMaxLevel);
      }
      return hp;
   }

   public boolean isSilentMode() { return silentMode; }
   public double getBossScaleFactor() { return bossScaleFactor; }
   public boolean isSoundEnabled() { return soundsEnabled; }
   public float getSoundVolume() { return soundVolume; }
   public double getDamageCapPercentage() { return damageCapPercentage; }
   public List<ShopItem> getRaidTokenShop() { return raidTokenShop; }

   public static class TierRewardConfig {
      public String bossHeldItem = "";
      public Map<String, RankReward> winners = new LinkedHashMap<>();
      public RankReward participation = new RankReward();
      public RankReward killshot = new RankReward();
      public int minTokens = 1;
      public int maxTokens = 3;
      public int tokenDropChance = 30;

      public int getRankCount() { return winners.size(); }

      public void addRank() {
         int next = winners.size() + 1;
         winners.put("Winner_" + next, new RankReward());
      }

      public void removeLastRank() {
         int size = winners.size();
         if (size > 1) {
            winners.remove("Winner_" + size);
         }
      }
   }

   public static class RankReward {
      public List<String> items = new ArrayList<>();
      public List<String> commands = new ArrayList<>();
   }

   public static class ShopItem {
      public String itemID = "minecraft:paper";
      public int price = 100;
      public int displayCount = 1;
      public String nbt = "";
      public boolean isCommand = false;
      public List<String> commands = new ArrayList<>();
   }
}