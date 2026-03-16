package com.PixelmonRaid;

public class RaidDifficultyManager {
   private static RaidDifficultyManager.Difficulty current = RaidDifficultyManager.Difficulty.NORMAL;
   public static void setDifficulty(RaidDifficultyManager.Difficulty difficulty) { current = difficulty; }
   public static RaidDifficultyManager.Difficulty getDifficulty() { return current; }
   public static enum Difficulty {
      EASY(1.0F, 1.0F), NORMAL(0.9F, 1.1F), HARD(0.7F, 1.3F), NIGHTMARE(0.5F, 1.5F);
      public final float playerDamageMultiplier;
      public final float bossDamageMultiplier;
      private Difficulty(float playerMultiplier, float bossMultiplier) {
         this.playerDamageMultiplier = playerMultiplier;
         this.bossDamageMultiplier = bossMultiplier;
      }
   }
}