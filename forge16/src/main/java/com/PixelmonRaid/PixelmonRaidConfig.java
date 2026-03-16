package com.PixelmonRaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PixelmonRaidConfig {
   private static PixelmonRaidConfig instance;
   private static final File configFile = new File("config/pixelmonraid.json");
   private static final File backupFile = new File("config/pixelmonraid_backup.json");
   private static final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
   public static final int CURRENT_CONFIG_VERSION = 14;
   private int configVersion = 14;
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
   private List<String> bossListLevel1 = new ArrayList(Arrays.asList("Bulbasaur", "Charmander", "Squirtle"));
   private List<String> bossListLevel2 = new ArrayList(Arrays.asList("Ivysaur", "Charmeleon", "Wartortle"));
   private List<String> bossListLevel3 = new ArrayList(Arrays.asList("Venusaur", "Charizard", "Blastoise"));
   private List<String> bossListLevel4 = new ArrayList(Arrays.asList("Dragonite", "Tyranitar", "Metagross"));
   private List<String> bossListLevel5 = new ArrayList(Arrays.asList("Mewtwo", "Rayquaza", "Eternatus"));
   private List<String> bossSpeciesList = new ArrayList(Arrays.asList("Charizard", "Blastoise", "Venusaur"));
   private List<String> bossSpeciesListTier5 = new ArrayList(Arrays.asList("Rayquaza", "Mewtwo"));
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
   private List<PixelmonRaidConfig.ShopItem> raidTokenShop = new ArrayList();
   private List<String> raidShopItems = null;
   @SerializedName("Boss_Difficulty_1")
   public PixelmonRaidConfig.TierRewardConfig tier1 = new PixelmonRaidConfig.TierRewardConfig();
   @SerializedName("Boss_Difficulty_2")
   public PixelmonRaidConfig.TierRewardConfig tier2 = new PixelmonRaidConfig.TierRewardConfig();
   @SerializedName("Boss_Difficulty_3")
   public PixelmonRaidConfig.TierRewardConfig tier3 = new PixelmonRaidConfig.TierRewardConfig();
   @SerializedName("Boss_Difficulty_4")
   public PixelmonRaidConfig.TierRewardConfig tier4 = new PixelmonRaidConfig.TierRewardConfig();
   @SerializedName("Boss_Difficulty_5")
   public PixelmonRaidConfig.TierRewardConfig tier5 = new PixelmonRaidConfig.TierRewardConfig();
   @SerializedName("tierRewards")
   private Map<String, PixelmonRaidConfig.LegacyTier> legacyTierRewards = null;
   private String moneyCommand = "economy give %player% %amount%";

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

   private void load() {
      if (!configFile.exists()) {
         this.addDefaults();
         this.save();
      } else {
         try {
            Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
            Throwable var2 = null;

            try {
               PixelmonRaidConfig data = (PixelmonRaidConfig)gson.fromJson(reader, PixelmonRaidConfig.class);
               if (data != null) {
                  boolean needsUpdate = false;
                  if (data.configVersion < 14) {
                     try {
                        Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                     } catch (IOException var26) {
                     }

                     System.out.println("[PixelmonRaid] Upgrading JSON to Version 14 (Internal Shop Toggle)...");
                     if (data.raidShopItems != null && !data.raidShopItems.isEmpty()) {
                        Iterator var5 = data.raidShopItems.iterator();

                        while(var5.hasNext()) {
                           String entry = (String)var5.next();

                           try {
                              String[] parts = entry.split(" ", 3);
                              PixelmonRaidConfig.ShopItem si = new PixelmonRaidConfig.ShopItem();
                              si.itemID = parts[0];
                              si.price = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                              if (parts.length > 2) {
                                 String rest = parts[2];
                                 if (rest.contains("{")) {
                                    String cStr = rest.substring(0, rest.indexOf("{")).trim();
                                    if (!cStr.isEmpty()) {
                                       si.displayCount = Integer.parseInt(cStr);
                                    }

                                    si.nbt = rest.substring(rest.indexOf("{"));
                                 } else {
                                    si.displayCount = Integer.parseInt(rest.trim());
                                 }
                              }

                              this.raidTokenShop.add(si);
                           } catch (Exception var25) {
                           }
                        }
                     } else if (data.raidTokenShop != null && !data.raidTokenShop.isEmpty()) {
                        this.raidTokenShop = data.raidTokenShop;
                     } else {
                        this.addDefaultShop();
                     }

                     if (data.tier1 != null) {
                        this.tier1 = data.tier1;
                        this.tier2 = data.tier2;
                        this.tier3 = data.tier3;
                        this.tier4 = data.tier4;
                        this.tier5 = data.tier5;
                     } else {
                        this.addDefaults();
                     }

                     this.raidShopItems = null;
                     this.configVersion = 14;
                     needsUpdate = true;
                  } else {
                     this.configVersion = data.configVersion;
                     if (data.tier1 != null) {
                        this.tier1 = data.tier1;
                        this.tier2 = data.tier2;
                        this.tier3 = data.tier3;
                        this.tier4 = data.tier4;
                        this.tier5 = data.tier5;
                     }

                     if (data.raidTokenShop != null) {
                        this.raidTokenShop = data.raidTokenShop;
                     }
                  }

                  this.enableInternalShop = data.enableInternalShop;
                  if (data.msgShopDisabled != null) {
                     this.msgShopDisabled = data.msgShopDisabled;
                  }

                  if (data.msgRaidStart != null) {
                     this.msgRaidStart = data.msgRaidStart;
                  }

                  if (data.msgRaidWin != null) {
                     this.msgRaidWin = data.msgRaidWin;
                  }

                  if (data.msgRaidLoss != null) {
                     this.msgRaidLoss = data.msgRaidLoss;
                  }

                  if (data.msgEnrage != null) {
                     this.msgEnrage = data.msgEnrage;
                  }

                  if (data.msgJoin != null) {
                     this.msgJoin = data.msgJoin;
                  }

                  if (data.msgCooldown != null) {
                     this.msgCooldown = data.msgCooldown;
                  }

                  if (data.msgKillshot != null) {
                     this.msgKillshot = data.msgKillshot;
                  }

                  if (data.msgSpawning5Min != null) {
                     this.msgSpawning5Min = data.msgSpawning5Min;
                  }

                  if (data.msgSpawning1Min != null) {
                     this.msgSpawning1Min = data.msgSpawning1Min;
                  }

                  if (data.msgClickToWarp != null) {
                     this.msgClickToWarp = data.msgClickToWarp;
                  }

                  if (data.msgBossHpUpdate != null) {
                     this.msgBossHpUpdate = data.msgBossHpUpdate;
                  }

                  if (data.msgBossHpUpdateSuddenDeath != null) {
                     this.msgBossHpUpdateSuddenDeath = data.msgBossHpUpdateSuddenDeath;
                  }

                  if (data.msgReloadTimerSynced != null) {
                     this.msgReloadTimerSynced = data.msgReloadTimerSynced;
                  }

                  if (data.msgReload != null) {
                     this.msgReload = data.msgReload;
                  }

                  if (data.uiTimerBarTitle != null) {
                     this.showIdleTimerBar = data.showIdleTimerBar;
                     this.uiTimerBarTitle = data.uiTimerBarTitle;
                  }

                  if (data.uiTimerBarPaused != null) {
                     this.uiTimerBarPaused = data.uiTimerBarPaused;
                  }

                  this.discordWebhookUrl = data.discordWebhookUrl;
                  if (data.bossListLevel1 != null) {
                     this.bossListLevel1 = data.bossListLevel1;
                  }

                  if (data.bossListLevel2 != null) {
                     this.bossListLevel2 = data.bossListLevel2;
                  }

                  if (data.bossListLevel3 != null) {
                     this.bossListLevel3 = data.bossListLevel3;
                  }

                  if (data.bossListLevel4 != null) {
                     this.bossListLevel4 = data.bossListLevel4;
                  }

                  if (data.bossListLevel5 != null) {
                     this.bossListLevel5 = data.bossListLevel5;
                  }

                  if (data.bossSpeciesList != null) {
                     this.bossSpeciesList = data.bossSpeciesList;
                  }

                  if (data.bossSpeciesListTier5 != null) {
                     this.bossSpeciesListTier5 = data.bossSpeciesListTier5;
                  }

                  this.raidDifficulty = data.raidDifficulty;
                  this.silentMode = data.silentMode;
                  if (data.bossScaleFactor > 0.0D) {
                     this.bossScaleFactor = data.bossScaleFactor;
                  }

                  this.autoRaidEnabled = data.autoRaidEnabled;
                  if (data.bossHpLevel1 > 0) {
                     this.bossHpLevel1 = data.bossHpLevel1;
                  }

                  if (data.bossHpLevel2 > 0) {
                     this.bossHpLevel2 = data.bossHpLevel2;
                  }

                  if (data.bossHpLevel3 > 0) {
                     this.bossHpLevel3 = data.bossHpLevel3;
                  }

                  if (data.bossHpLevel4 > 0) {
                     this.bossHpLevel4 = data.bossHpLevel4;
                  }

                  if (data.bossHpLevel5 > 0) {
                     this.bossHpLevel5 = data.bossHpLevel5;
                  }

                  if (data.extraHpMultiplierPerWinAtMaxLevel >= 0.0D) {
                     this.extraHpMultiplierPerWinAtMaxLevel = data.extraHpMultiplierPerWinAtMaxLevel;
                  }

                  this.soundsEnabled = data.soundsEnabled;
                  if (data.soundVolume > 0.0F) {
                     this.soundVolume = data.soundVolume;
                  }

                  this.bossBarSpacer = data.bossBarSpacer;
                  this.hologramEnabled = data.hologramEnabled;
                  this.holoX = data.holoX;
                  this.holoY = data.holoY;
                  this.holoZ = data.holoZ;
                  if (data.holoWorld != null) {
                     this.holoWorld = data.holoWorld;
                  }

                  this.raidIntervalSeconds = data.raidIntervalSeconds;
                  this.baseSuddenDeathSeconds = data.baseSuddenDeathSeconds;
                  this.raidDurationSeconds = data.raidDurationSeconds > 0 ? data.raidDurationSeconds : data.raidDurationLevel1;
                  if (data.rejoinCooldownSeconds >= 0) {
                     this.rejoinCooldownSeconds = data.rejoinCooldownSeconds;
                  }

                  if (data.hpBroadcastIntervalSeconds > 0) {
                     this.hpBroadcastIntervalSeconds = data.hpBroadcastIntervalSeconds;
                  }

                  this.dynamicDifficulty = data.dynamicDifficulty;
                  if (data.difficultyScalePerPlayer > 0.0D) {
                     this.difficultyScalePerPlayer = data.difficultyScalePerPlayer;
                  }

                  if (data.uiServerName != null) {
                     this.uiServerName = data.uiServerName;
                  }

                  if (data.uiThemeColor != null) {
                     this.uiThemeColor = data.uiThemeColor;
                  }

                  if (data.uiLogoItem != null) {
                     this.uiLogoItem = data.uiLogoItem;
                  }

                  if (data.uiBorderItem != null) {
                     this.uiBorderItem = data.uiBorderItem;
                  }

                  if (data.uiLeaderboardTitle != null) {
                     this.uiLeaderboardTitle = data.uiLeaderboardTitle;
                  }

                  if (data.uiCurrentLeaderboardTitle != null) {
                     this.uiCurrentLeaderboardTitle = data.uiCurrentLeaderboardTitle;
                  }

                  if (data.uiLastLeaderboardTitle != null) {
                     this.uiLastLeaderboardTitle = data.uiLastLeaderboardTitle;
                  }

                  if (data.raidDurationLevel1 > 0) {
                     this.raidDurationLevel1 = data.raidDurationLevel1;
                  }

                  if (data.raidDurationLevel2 > 0) {
                     this.raidDurationLevel2 = data.raidDurationLevel2;
                  }

                  if (data.raidDurationLevel3 > 0) {
                     this.raidDurationLevel3 = data.raidDurationLevel3;
                  }

                  if (data.raidDurationLevel4 > 0) {
                     this.raidDurationLevel4 = data.raidDurationLevel4;
                  }

                  if (data.raidDurationLevel5 > 0) {
                     this.raidDurationLevel5 = data.raidDurationLevel5;
                  }

                  this.damageCapPercentage = data.damageCapPercentage;
                  if (needsUpdate) {
                     this.save();
                  }

                  return;
               }
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

            return;
         } catch (JsonSyntaxException var29) {
            try {
               Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               this.addDefaults();
               this.save();
            } catch (IOException var23) {
            }
         } catch (Exception var30) {
         }

      }
   }

   private void addDefaultShop() {
      PixelmonRaidConfig.ShopItem s1 = new PixelmonRaidConfig.ShopItem();
      s1.itemID = "pixelmon:rare_candy";
      s1.price = 1;
      PixelmonRaidConfig.ShopItem s2 = new PixelmonRaidConfig.ShopItem();
      s2.itemID = "pixelmon:poke_ball";
      s2.price = 50;
      s2.nbt = "{PokeBallID:\"master_ball\"}";
      this.raidTokenShop.add(s1);
      this.raidTokenShop.add(s2);
   }

   private void addDefaults() {
      for(int i = 1; i <= 5; ++i) {
         PixelmonRaidConfig.TierRewardConfig tier = new PixelmonRaidConfig.TierRewardConfig();
         PixelmonRaidConfig.RankReward r1 = new PixelmonRaidConfig.RankReward();
         r1.items.add("pixelmon:rare_candy 3");
         r1.commands.add("economy give %player% 3000");
         PixelmonRaidConfig.RankReward r2 = new PixelmonRaidConfig.RankReward();
         r2.items.add("pixelmon:rare_candy 2");
         r2.commands.add("economy give %player% 2000");
         PixelmonRaidConfig.RankReward r3 = new PixelmonRaidConfig.RankReward();
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
         if (i == 1) {
            tier.bossHeldItem = "pixelmon:oran_berry";
         } else if (i == 2) {
            tier.bossHeldItem = "pixelmon:sitrus_berry";
         } else if (i == 3) {
            tier.bossHeldItem = "pixelmon:lum_berry";
         } else if (i == 4) {
            tier.bossHeldItem = "pixelmon:life_orb";
         } else if (i == 5) {
            tier.bossHeldItem = "pixelmon:leftovers";
         }

         if (i == 1) {
            this.tier1 = tier;
         } else if (i == 2) {
            this.tier2 = tier;
         } else if (i == 3) {
            this.tier3 = tier;
         } else if (i == 4) {
            this.tier4 = tier;
         } else if (i == 5) {
            this.tier5 = tier;
         }
      }

      this.addDefaultShop();
   }

   public PixelmonRaidConfig.TierRewardConfig getTierRewards(int level) {
      switch(level) {
      case 1:
         return this.tier1;
      case 2:
         return this.tier2;
      case 3:
         return this.tier3;
      case 4:
         return this.tier4;
      case 5:
         return this.tier5;
      default:
         return this.tier1;
      }
   }

   public void save() {
      try {
         Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8);
         Throwable var2 = null;

         try {
            gson.toJson(this, writer);
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

   public boolean isInternalShopEnabled() {
      return this.enableInternalShop;
   }

   public String getMsgShopDisabled() {
      return this.formatColor(this.msgShopDisabled);
   }

   public String getMsgSpawning5Min() {
      return this.formatColor(this.msgSpawning5Min);
   }

   public String getMsgSpawning1Min() {
      return this.formatColor(this.msgSpawning1Min);
   }

   public String getMsgClickToWarp() {
      return this.formatColor(this.msgClickToWarp);
   }

   public String getMsgBossHpUpdate() {
      return this.formatColor(this.msgBossHpUpdate);
   }

   public String getMsgBossHpUpdateSuddenDeath() {
      return this.formatColor(this.msgBossHpUpdateSuddenDeath);
   }

   public String getMsgReloadTimerSynced() {
      return this.formatColor(this.msgReloadTimerSynced);
   }

   public String getMsgReload() {
      return this.formatColor(this.msgReload);
   }

   public boolean isShowIdleTimerBar() {
      return this.showIdleTimerBar;
   }

   public String getUiTimerBarTitle() {
      return this.formatColor(this.uiTimerBarTitle);
   }

   public String getUiTimerBarPaused() {
      return this.formatColor(this.uiTimerBarPaused);
   }

   public boolean isAutoRaidEnabled() {
      return this.autoRaidEnabled;
   }

   public void setAutoRaidEnabled(boolean v) {
      this.autoRaidEnabled = v;
      this.save();
   }

   public boolean isBossBarSpacerEnabled() {
      return this.bossBarSpacer;
   }

   public boolean isHologramEnabled() {
      return this.hologramEnabled;
   }

   public void setHologramEnabled(boolean v) {
      this.hologramEnabled = v;
      this.save();
   }

   public double getHoloX() {
      return this.holoX;
   }

   public double getHoloY() {
      return this.holoY;
   }

   public double getHoloZ() {
      return this.holoZ;
   }

   public String getHoloWorld() {
      return this.holoWorld;
   }

   public void setHoloLocation(double x, double y, double z, String world) {
      this.holoX = x;
      this.holoY = y;
      this.holoZ = z;
      this.holoWorld = world;
      this.save();
   }

   public String getMsgRaidStart() {
      return this.formatColor(this.msgRaidStart);
   }

   public String getMsgRaidWin() {
      return this.formatColor(this.msgRaidWin);
   }

   public String getMsgRaidLoss() {
      return this.formatColor(this.msgRaidLoss);
   }

   public String getMsgEnrage() {
      return this.formatColor(this.msgEnrage);
   }

   public String getMsgJoin() {
      return this.formatColor(this.msgJoin);
   }

   public String getMsgCooldown() {
      return this.formatColor(this.msgCooldown);
   }

   public String getMsgKillshot() {
      return this.formatColor(this.msgKillshot);
   }

   public boolean isDynamicDifficulty() {
      return this.dynamicDifficulty;
   }

   public double getDifficultyScale() {
      return this.difficultyScalePerPlayer;
   }

   public int getRejoinCooldownSeconds() {
      return this.rejoinCooldownSeconds;
   }

   public String getUiServerName() {
      return this.formatColor(this.uiServerName);
   }

   public String getUiThemeColor() {
      return this.formatColor(this.uiThemeColor);
   }

   public String getUiLogoItem() {
      return this.uiLogoItem;
   }

   public String getUiBorderItem() {
      return this.uiBorderItem;
   }

   public String getUiLeaderboardTitle() {
      return this.formatColor(this.uiLeaderboardTitle);
   }

   public String getUiCurrentLeaderboardTitle() {
      return this.formatColor(this.uiCurrentLeaderboardTitle);
   }

   public String getUiLastLeaderboardTitle() {
      return this.formatColor(this.uiLastLeaderboardTitle);
   }

   public void setRaidDurationSeconds(int seconds) {
      this.raidDurationSeconds = seconds;
      this.save();
   }

   public int getRaidDurationSeconds() {
      return this.raidDurationSeconds > 0 ? this.raidDurationSeconds : 300;
   }

   public int getRaidDurationForDifficulty(int difficulty) {
      switch(difficulty) {
      case 1:
         return this.raidDurationLevel1 > 0 ? this.raidDurationLevel1 : this.raidDurationSeconds;
      case 2:
         return this.raidDurationLevel2 > 0 ? this.raidDurationLevel2 : this.raidDurationSeconds;
      case 3:
         return this.raidDurationLevel3 > 0 ? this.raidDurationLevel3 : this.raidDurationSeconds;
      case 4:
         return this.raidDurationLevel4 > 0 ? this.raidDurationLevel4 : this.raidDurationSeconds;
      case 5:
         return this.raidDurationLevel5 > 0 ? this.raidDurationLevel5 : this.raidDurationSeconds;
      default:
         return this.raidDurationSeconds > 0 ? this.raidDurationSeconds : 300;
      }
   }

   public void setSuddenDeathSeconds(int seconds) {
      this.baseSuddenDeathSeconds = seconds;
      this.save();
   }

   public int getSuddenDeathDurationSeconds() {
      return this.baseSuddenDeathSeconds > 0 ? this.baseSuddenDeathSeconds : 120;
   }

   public void setRaidIntervalSeconds(int seconds) {
      this.raidIntervalSeconds = seconds;
      this.save();
   }

   public int getRaidIntervalSeconds() {
      return this.raidIntervalSeconds;
   }

   public int getHpBroadcastIntervalSeconds() {
      return this.hpBroadcastIntervalSeconds;
   }

   public boolean incrementDifficultyOnWin() {
      if (this.raidDifficulty < 5) {
         ++this.raidDifficulty;
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public void decrementDifficultyOnLoss() {
      if (this.raidDifficulty > 1) {
         --this.raidDifficulty;
         this.save();
      }

   }

   public void setRaidDifficulty(int d) {
      this.raidDifficulty = d;
      this.save();
   }

   public void setSilentMode(boolean silent) {
      this.silentMode = silent;
      this.save();
   }

   public String getDiscordWebhookUrl() {
      return this.discordWebhookUrl;
   }

   public String getRandomBossSpecies(int difficulty) {
      List pool;
      switch(difficulty) {
      case 1:
         pool = this.bossListLevel1;
         break;
      case 2:
         pool = this.bossListLevel2;
         break;
      case 3:
         pool = this.bossListLevel3;
         break;
      case 4:
         pool = this.bossListLevel4;
         break;
      case 5:
         pool = this.bossListLevel5;
         break;
      default:
         pool = this.bossListLevel3;
      }

      return pool != null && !pool.isEmpty() ? (String)pool.get((new Random()).nextInt(pool.size())) : "Charizard";
   }

   public int getRaidDifficulty() {
      return this.raidDifficulty;
   }

   public int getBossHP(int difficulty, int winStreak) {
      int hp;
      switch(difficulty) {
      case 1:
         hp = this.bossHpLevel1 > 0 ? this.bossHpLevel1 : 20000;
         break;
      case 2:
         hp = this.bossHpLevel2 > 0 ? this.bossHpLevel2 : '꿈';
         break;
      case 3:
         hp = this.bossHpLevel3 > 0 ? this.bossHpLevel3 : 70000;
         break;
      case 4:
         hp = this.bossHpLevel4 > 0 ? this.bossHpLevel4 : 95000;
         break;
      case 5:
         hp = this.bossHpLevel5 > 0 ? this.bossHpLevel5 : 120000;
         break;
      default:
         hp = 20000;
      }

      if (difficulty >= 5 && winStreak > 0 && this.extraHpMultiplierPerWinAtMaxLevel > 0.0D) {
         hp += (int)((double)hp * (double)winStreak * this.extraHpMultiplierPerWinAtMaxLevel);
      }

      return hp;
   }

   public boolean isSilentMode() {
      return this.silentMode;
   }

   public double getBossScaleFactor() {
      return this.bossScaleFactor;
   }

   public boolean isSoundEnabled() {
      return this.soundsEnabled;
   }

   public float getSoundVolume() {
      return this.soundVolume;
   }

   public double getDamageCapPercentage() {
      return this.damageCapPercentage;
   }

   public List<PixelmonRaidConfig.ShopItem> getRaidTokenShop() {
      return this.raidTokenShop;
   }

   public static class LegacyTier {
      public Map<String, List<String>> rankRewards;
      public Map<String, Integer> rankMoney;
      public List<String> participation;
      public List<String> killshot;
      public Integer moneyParticipation;
      public Integer moneyKillshot;
      public Integer minTokens;
      public Integer maxTokens;
      public Integer tokenDropChance;
   }

   public static class TierRewardConfig {
      public String bossHeldItem = "";
      public Map<String, PixelmonRaidConfig.RankReward> winners = new LinkedHashMap();
      @SerializedName("Participation")
      public PixelmonRaidConfig.RankReward participation = new PixelmonRaidConfig.RankReward();
      @SerializedName("Killshot")
      public PixelmonRaidConfig.RankReward killshot = new PixelmonRaidConfig.RankReward();
      public int minTokens = 1;
      public int maxTokens = 3;
      public int tokenDropChance = 30;

      public int getRankCount() {
         return this.winners.size();
      }

      public void addRank() {
         int next = this.winners.size() + 1;
         this.winners.put("Winner_" + next, new PixelmonRaidConfig.RankReward());
      }

      public void removeLastRank() {
         int size = this.winners.size();
         if (size > 1) {
            this.winners.remove("Winner_" + size);
         }

      }
   }

   public static class RankReward {
      public List<String> items = new ArrayList();
      public List<String> commands = new ArrayList();
   }

   public static class ShopItem {
      public String itemID = "minecraft:paper";
      public int price = 100;
      public int displayCount = 1;
      public String nbt = "";
      public boolean isCommand = false;
      public List<String> commands = new ArrayList();
   }
}
