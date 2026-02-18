package com.PixelmonRaid;

public class RaidRewardsConfig {
    public static class RewardEntry {
        public String item;
        public int count;
        public float chance;

        public RewardEntry(String item, int count, float chance) {
            this.item = item;
            this.count = count;
            this.chance = chance;
        }
    }
}