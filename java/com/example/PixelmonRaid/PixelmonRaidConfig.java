package com.example.PixelmonRaid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.*;
import java.util.*;

public class PixelmonRaidConfig {
    private static PixelmonRaidConfig instance;
    private static final File configFile = new File("config/pixelmonraid.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String msgRaidStart = "&d&l☠ THE RAID BOSS HAS SPAWNED! &fUse &e/raid join &fto fight!";
    private String msgRaidWin = "&6&l🏆 RAID VICTORY! &eThe boss has been defeated!";
    private String msgRaidLoss = "&c&l✖ RAID FAILED! &7The boss escaped...";
    private String msgEnrage = "&c&l⚡ THE BOSS IS ENRAGED! ⚡";
    private String msgJoin = "&e&l⚠ BATTLE INITIATING... PREPARE YOURSELF! ⚠";
    private String msgCooldown = "&c&l⏳ WAITING FOR STAMINA... &7(%s)";
    private String msgKillshot = "&4&l☠ %player% DEALT THE FINAL BLOW! ☠";
    private String msgWarn60 = "&e&l⚠ RAID STARTING IN 1 MINUTE! &fPrepare your team!";
    private String msgWarn30 = "&6&l⚠ RAID STARTING IN 30 SECONDS! &fType &e/raid join &fnow!";


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
    private double bossScaleFactor = 10.0;

    private boolean bossBarSpacer = true;

    private boolean soundsEnabled = true;
    private float soundVolume = 1.0f;


    private boolean hologramEnabled = true;
    private double holoX = 0.0;
    private double holoY = 100.0;
    private double holoZ = 0.0;
    private String holoWorld = "minecraft:overworld";

    private boolean dynamicDifficulty = true;
    private double difficultyScalePerPlayer = 0.15;
    private int raidIntervalSeconds = 300;
    private int baseSuddenDeathSeconds = 120;
    private int raidDurationSeconds = 300;
    private int rejoinCooldownSeconds = 10;
    private int hpBroadcastIntervalSeconds = 60;

    private String uiServerName = "&d&lMY SERVER RAIDS";
    private String uiThemeColor = "&5";
    private String uiLogoItem = "minecraft:nether_star";
    private String uiBorderItem = "minecraft:purple_stained_glass_pane";
    private String uiLeaderboardTitle = "&8&lRaid Leaderboard";

    private int raidDurationLevel1 = 300;
    private int raidDurationLevel2 = 360;
    private int raidDurationLevel3 = 420;
    private int raidDurationLevel4 = 480;
    private int raidDurationLevel5 = 600;

    private double damageCapPercentage = 0.10;

    private int tokensDropLevel1 = 1;
    private int tokensDropLevel2 = 2;
    private int tokensDropLevel3 = 3;
    private int tokensDropLevel4 = 5;
    private int tokensDropLevel5 = 10;
    private List<String> raidShopItems = new ArrayList<>(Arrays.asList("pixelmon:rare_candy 1 1", "pixelmon:master_ball 50 1", "pixelmon:park_ball 20 1"));


    private Map<String, List<String>> rankRewards = new LinkedHashMap<>();
    private Map<String, Integer> rankMoney = new LinkedHashMap<>();


    private List<String> rewardsRank1;
    private List<String> rewardsRank2;
    private List<String> rewardsRank3;
    private List<String> rewardsRank4;
    private List<String> rewardsRank5;
    private int moneyRank1;
    private int moneyRank2;
    private int moneyRank3;
    private int moneyRank4;
    private int moneyRank5;

    private List<String> rewardsWinningParticipation = new ArrayList<>();
    private List<String> rewardsParticipation = new ArrayList<>();
    private List<String> killShotRewards = new ArrayList<>();
    private int tokenDropChance = 30;

    private String moneyCommand = "economy give %player% %amount%";
    private int moneyParticipation = 200;
    private int moneyKillshot = 5000;

    public PixelmonRaidConfig() {
    }

    public static PixelmonRaidConfig getInstance() {
        if (instance == null) {
            instance = new PixelmonRaidConfig();
            instance.load();
        }
        return instance;
    }

    public static void loadConfig() { getInstance().load(); }

    public static void reload() {
        if (instance != null) {
            instance.load();
        }
    }

    private void load() {
        if (!configFile.exists()) {
            addDefaults();
            save();
            return;
        }

        try (Reader reader = new FileReader(configFile)) {
            PixelmonRaidConfig data = gson.fromJson(reader, PixelmonRaidConfig.class);

            if (data == null) {
                System.err.println("[PixelmonRaid] Config file was empty or invalid. Keeping defaults.");
                return;
            }

            if (data.msgRaidStart != null) this.msgRaidStart = data.msgRaidStart;
            if (data.msgRaidWin != null) this.msgRaidWin = data.msgRaidWin;
            if (data.msgRaidLoss != null) this.msgRaidLoss = data.msgRaidLoss;
            if (data.msgEnrage != null) this.msgEnrage = data.msgEnrage;
            if (data.msgJoin != null) this.msgJoin = data.msgJoin;
            if (data.msgCooldown != null) this.msgCooldown = data.msgCooldown;
            if (data.msgKillshot != null) this.msgKillshot = data.msgKillshot;
            if (data.msgWarn60 != null) this.msgWarn60 = data.msgWarn60;
            if (data.msgWarn30 != null) this.msgWarn30 = data.msgWarn30;

            this.discordWebhookUrl = data.discordWebhookUrl;
            if (data.bossListLevel1 != null) this.bossListLevel1 = data.bossListLevel1;
            if (data.bossListLevel2 != null) this.bossListLevel2 = data.bossListLevel2;
            if (data.bossListLevel3 != null) this.bossListLevel3 = data.bossListLevel3;
            if (data.bossListLevel4 != null) this.bossListLevel4 = data.bossListLevel4;
            if (data.bossListLevel5 != null) this.bossListLevel5 = data.bossListLevel5;

            if (data.bossSpeciesList != null) this.bossSpeciesList = data.bossSpeciesList;
            if (data.bossSpeciesListTier5 != null) this.bossSpeciesListTier5 = data.bossSpeciesListTier5;

            this.raidDifficulty = data.raidDifficulty;
            this.silentMode = data.silentMode;
            if (data.bossScaleFactor > 0) this.bossScaleFactor = data.bossScaleFactor;
            this.soundsEnabled = data.soundsEnabled;
            if (data.soundVolume > 0) this.soundVolume = data.soundVolume;
            this.bossBarSpacer = data.bossBarSpacer;

            this.hologramEnabled = data.hologramEnabled;
            this.holoX = data.holoX;
            this.holoY = data.holoY;
            this.holoZ = data.holoZ;
            if (data.holoWorld != null) this.holoWorld = data.holoWorld;

            this.raidIntervalSeconds = data.raidIntervalSeconds;
            this.baseSuddenDeathSeconds = data.baseSuddenDeathSeconds;
            this.raidDurationSeconds = data.raidDurationSeconds > 0 ? data.raidDurationSeconds : data.raidDurationLevel1;
            if (data.rejoinCooldownSeconds >= 0) this.rejoinCooldownSeconds = data.rejoinCooldownSeconds;
            if (data.hpBroadcastIntervalSeconds > 0) this.hpBroadcastIntervalSeconds = data.hpBroadcastIntervalSeconds;

            this.dynamicDifficulty = data.dynamicDifficulty;
            if (data.difficultyScalePerPlayer > 0) this.difficultyScalePerPlayer = data.difficultyScalePerPlayer;

            if (data.uiServerName != null) this.uiServerName = data.uiServerName;
            if (data.uiThemeColor != null) this.uiThemeColor = data.uiThemeColor;
            if (data.uiLogoItem != null) this.uiLogoItem = data.uiLogoItem;
            if (data.uiBorderItem != null) this.uiBorderItem = data.uiBorderItem;
            if (data.uiLeaderboardTitle != null) this.uiLeaderboardTitle = data.uiLeaderboardTitle;
            if (data.raidDurationLevel1 > 0) this.raidDurationLevel1 = data.raidDurationLevel1;
            if (data.raidDurationLevel2 > 0) this.raidDurationLevel2 = data.raidDurationLevel2;
            if (data.raidDurationLevel3 > 0) this.raidDurationLevel3 = data.raidDurationLevel3;
            if (data.raidDurationLevel4 > 0) this.raidDurationLevel4 = data.raidDurationLevel4;
            if (data.raidDurationLevel5 > 0) this.raidDurationLevel5 = data.raidDurationLevel5;

            this.damageCapPercentage = data.damageCapPercentage;
            this.tokensDropLevel1 = data.tokensDropLevel1;
            this.tokensDropLevel2 = data.tokensDropLevel2;
            this.tokensDropLevel3 = data.tokensDropLevel3;
            this.tokensDropLevel4 = data.tokensDropLevel4;
            this.tokensDropLevel5 = data.tokensDropLevel5;
            if(data.tokenDropChance > 0) this.tokenDropChance = data.tokenDropChance;

            if (data.rankRewards != null && !data.rankRewards.isEmpty()) {
                this.rankRewards = new LinkedHashMap<>(data.rankRewards);
            } else {
                if (data.rewardsRank1 != null) this.rankRewards.put("1", data.rewardsRank1);
                if (data.rewardsRank2 != null) this.rankRewards.put("2", data.rewardsRank2);
                if (data.rewardsRank3 != null) this.rankRewards.put("3", data.rewardsRank3);
                if (data.rewardsRank4 != null) this.rankRewards.put("4", data.rewardsRank4);
                if (data.rewardsRank5 != null) this.rankRewards.put("5", data.rewardsRank5);
            }

            if (data.rankMoney != null && !data.rankMoney.isEmpty()) {
                this.rankMoney = new LinkedHashMap<>(data.rankMoney);
            } else {
                if (data.moneyRank1 > 0) this.rankMoney.put("1", data.moneyRank1);
                if (data.moneyRank2 > 0) this.rankMoney.put("2", data.moneyRank2);
                if (data.moneyRank3 > 0) this.rankMoney.put("3", data.moneyRank3);
                if (data.moneyRank4 > 0) this.rankMoney.put("4", data.moneyRank4);
                if (data.moneyRank5 > 0) this.rankMoney.put("5", data.moneyRank5);
            }

            this.rewardsParticipation = data.rewardsParticipation;
            if (data.rewardsWinningParticipation != null) this.rewardsWinningParticipation = data.rewardsWinningParticipation;

            this.killShotRewards = data.killShotRewards;
            if (data.raidShopItems != null) this.raidShopItems = data.raidShopItems;
            if (data.moneyCommand != null) this.moneyCommand = data.moneyCommand;
            this.moneyParticipation = data.moneyParticipation;
            this.moneyKillshot = data.moneyKillshot;

            if(this.rankRewards.isEmpty()) addDefaults();
        } catch (JsonSyntaxException e) {
            System.err.println("[PixelmonRaid] ERROR: Config file has invalid JSON syntax! Changes were NOT loaded to protect your file.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[PixelmonRaid] ERROR: Failed to load config file.");
            e.printStackTrace();
        }
    }

    private void addDefaults() {
        addDefaultRank(1);
        addDefaultRank(2);
        addDefaultRank(3);
        rewardsParticipation = new ArrayList<>(); rewardsParticipation.add("pixelmon:potion 5");
        rewardsWinningParticipation = new ArrayList<>(); rewardsWinningParticipation.add("pixelmon:rare_candy 1");
        killShotRewards = new ArrayList<>(); killShotRewards.add("pixelmon:master_ball 1");
        moneyParticipation = 200; moneyKillshot = 5000;
    }

    private void addDefaultRank(int rank) {
        List<String> items = new ArrayList<>();
        items.add("pixelmon:rare_candy " + rank);
        rankRewards.put(String.valueOf(rank), items);
        rankMoney.put(String.valueOf(rank), 10000 - (rank * 1000));
    }

    public void save() {
        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean isBossBarSpacerEnabled() { return bossBarSpacer; }
    public boolean isHologramEnabled() { return hologramEnabled; }
    public void setHologramEnabled(boolean v) { this.hologramEnabled = v; save(); }

    public double getHoloX() { return holoX; }
    public double getHoloY() { return holoY; }
    public double getHoloZ() { return holoZ; }
    public String getHoloWorld() { return holoWorld; }

    public void setHoloLocation(double x, double y, double z, String world) {
        this.holoX = x; this.holoY = y; this.holoZ = z; this.holoWorld = world;
        save();
    }

    public int getRankCount() { return rankRewards.size(); }
    public void addRank() {
        int nextRank = rankRewards.size() + 1;
        rankRewards.put(String.valueOf(nextRank), new ArrayList<>());
        rankMoney.put(String.valueOf(nextRank), 1000);
        save();
    }
    public void removeLastRank() {
        int size = rankRewards.size();
        if (size > 1) {
            rankRewards.remove(String.valueOf(size));
            rankMoney.remove(String.valueOf(size));
            save();
        }
    }

    public List<String> getRewardsForRank(int rank) { return rankRewards.getOrDefault(String.valueOf(rank), null); }
    public void setRewardsForRank(int rank, List<String> items) { rankRewards.put(String.valueOf(rank), items); save(); }
    public int getMoneyForRank(int rank) { return rankMoney.getOrDefault(String.valueOf(rank), 0); }
    public void setMoneyForRank(int rank, int amount) { rankMoney.put(String.valueOf(rank), amount); save(); }

    public String getMsgRaidStart() { return msgRaidStart.replace("&", "§"); }
    public String getMsgRaidWin() { return msgRaidWin.replace("&", "§"); }
    public String getMsgRaidLoss() { return msgRaidLoss.replace("&", "§"); }
    public String getMsgEnrage() { return msgEnrage.replace("&", "§"); }
    public String getMsgJoin() { return msgJoin.replace("&", "§"); }
    public String getMsgCooldown() { return msgCooldown.replace("&", "§"); }
    public String getMsgKillshot() { return msgKillshot.replace("&", "§"); }
    public String getMsgWarn60() { return msgWarn60.replace("&", "§"); }
    public String getMsgWarn30() { return msgWarn30.replace("&", "§"); }

    public boolean isDynamicDifficulty() { return dynamicDifficulty; }
    public double getDifficultyScale() { return difficultyScalePerPlayer; }

    public int getRejoinCooldownSeconds() { return rejoinCooldownSeconds; }
    public String getUiServerName() { return uiServerName.replace("&", "§"); }
    public String getUiThemeColor() { return uiThemeColor.replace("&", "§"); }
    public String getUiLogoItem() { return uiLogoItem; }
    public String getUiBorderItem() { return uiBorderItem; }
    public String getUiLeaderboardTitle() { return uiLeaderboardTitle.replace("&", "§"); }

    public void setRaidDurationSeconds(int seconds) { this.raidDurationSeconds = seconds; save(); }
    public int getRaidDurationSeconds() { return this.raidDurationSeconds > 0 ? this.raidDurationSeconds : 300; }


    public int getRaidDurationForDifficulty(int difficulty) {
        switch(difficulty) {
            case 1: return raidDurationLevel1 > 0 ? raidDurationLevel1 : raidDurationSeconds;
            case 2: return raidDurationLevel2 > 0 ? raidDurationLevel2 : raidDurationSeconds;
            case 3: return raidDurationLevel3 > 0 ? raidDurationLevel3 : raidDurationSeconds;
            case 4: return raidDurationLevel4 > 0 ? raidDurationLevel4 : raidDurationSeconds;
            case 5: return raidDurationLevel5 > 0 ? raidDurationLevel5 : raidDurationSeconds;
            default: return raidDurationSeconds > 0 ? raidDurationSeconds : 300;
        }
    }

    public void setSuddenDeathSeconds(int seconds) { this.baseSuddenDeathSeconds = seconds; save(); }
    public int getSuddenDeathDurationSeconds() { return this.baseSuddenDeathSeconds > 0 ? this.baseSuddenDeathSeconds : 120; }

    public void setRaidIntervalSeconds(int seconds) { this.raidIntervalSeconds = seconds; save(); }
    public int getRaidIntervalSeconds() { return raidIntervalSeconds; }

    public int getHpBroadcastIntervalSeconds() { return hpBroadcastIntervalSeconds; }

    public boolean incrementDifficultyOnWin() {
        if (this.raidDifficulty < 5) {
            this.raidDifficulty++;
            save();
            return true;
        }
        return false;
    }

    public void decrementDifficultyOnLoss() { if (this.raidDifficulty > 1) { this.raidDifficulty--; save(); } }
    public void setRaidDifficulty(int d) { this.raidDifficulty = d; save(); }
    public void setSilentMode(boolean silent) { this.silentMode = silent; save(); }

    public String getDiscordWebhookUrl() { return discordWebhookUrl; }

    public String getRandomBossSpecies(int difficulty) {
        List<String> pool;
        switch (difficulty) {
            case 1: pool = bossListLevel1; break;
            case 2: pool = bossListLevel2; break;
            case 3: pool = bossListLevel3; break;
            case 4: pool = bossListLevel4; break;
            case 5: pool = bossListLevel5; break;
            default: pool = bossListLevel3; break;
        }
        if (pool == null || pool.isEmpty()) return "Charizard";
        return pool.get(new Random().nextInt(pool.size()));
    }

    public int getRaidDifficulty() { return raidDifficulty; }
    public int getBossHP() { return 20000 + ((raidDifficulty - 1) * 25000); }
    public boolean isSilentMode() { return silentMode; }
    public double getBossScaleFactor() { return bossScaleFactor; }

    public boolean isSoundEnabled() { return soundsEnabled; }
    public float getSoundVolume() { return soundVolume; }

    public double getDamageCapPercentage() { return damageCapPercentage; }
    public int getTokenDropChance() { return tokenDropChance; }

    public int getTokensForDifficulty(int difficulty) {
        int min = 0, max = 0;
        switch(difficulty) {
            case 1: min = tokensDropLevel1; max = tokensDropLevel1 + 2; break;
            case 2: min = tokensDropLevel2; max = tokensDropLevel2 + 3; break;
            case 3: min = tokensDropLevel3; max = tokensDropLevel3 + 4; break;
            case 4: min = tokensDropLevel4; max = tokensDropLevel4 + 5; break;
            case 5: min = tokensDropLevel5; max = tokensDropLevel5 + 10; break;
            default: min = 1; max = 3;
        }
        return min + new Random().nextInt(max - min + 1);
    }

    public List<String> getRaidShopItems() { return raidShopItems; }
    public void setRaidShopItems(List<String> items) { this.raidShopItems = items; save(); }

    public void setRewardsParticipation(List<String> items) { this.rewardsParticipation = items; save(); }
    public void setKillShotRewards(List<String> items) { this.killShotRewards = items; save(); }

    public List<String> getRewardsParticipation() { return rewardsParticipation; }
    public List<String> getRewardsWinningParticipation() { return rewardsWinningParticipation; }
    public List<String> getKillShotRewards() { return killShotRewards; }

    public String getMoneyCommand() { return moneyCommand; }
    public int getMoneyParticipation() { return moneyParticipation; }
    public int getMoneyKillshot() { return moneyKillshot; }

    public void setMoneyParticipation(int v) { this.moneyParticipation = v; save(); }
    public void setMoneyKillshot(int v) { this.moneyKillshot = v; save(); }
}