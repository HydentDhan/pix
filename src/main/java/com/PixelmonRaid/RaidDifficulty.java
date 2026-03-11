package com.example.PixelmonRaid;

public enum RaidDifficulty {
   EASY(1.0F, 0.75F),
   NORMAL(1.0F, 1.0F),
   HARD(1.0F, 1.5F),
   NIGHTMARE(1.0F, 2.0F);

   private final float playerDamageMultiplier;
   private final float bossDamageMultiplier;

   private RaidDifficulty(float playerDamageMultiplier, float bossDamageMultiplier) {
      this.playerDamageMultiplier = playerDamageMultiplier;
      this.bossDamageMultiplier = bossDamageMultiplier;
   }

   public float getPlayerDamageMultiplier() {
      return this.playerDamageMultiplier;
   }

   public float getBossDamageMultiplier() {
      return this.bossDamageMultiplier;
   }
}
