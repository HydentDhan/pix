package com.example.PixelmonRaid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class RaidBossTierConfig implements INBTSerializable<CompoundTag> {
   private final String id;
   private final float healthMultiplier;
   private final float damageMultiplier;

   public RaidBossTierConfig(String id, float healthMultiplier, float damageMultiplier) {
      this.id = id;
      this.healthMultiplier = healthMultiplier;
      this.damageMultiplier = damageMultiplier;
   }

   public String getName() {
      return this.id;
   }

   public float getHealthMultiplier() {
      return this.healthMultiplier;
   }

   public float getDamageMultiplier() {
      return this.damageMultiplier;
   }

   public void toBuffer(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.id);
      buffer.writeFloat(this.healthMultiplier);
      buffer.writeFloat(this.damageMultiplier);
   }

   public static RaidBossTierConfig fromBuffer(FriendlyByteBuf buffer) {
      String id = buffer.readUtf(32767);
      float health = buffer.readFloat();
      float damage = buffer.readFloat();
      return new RaidBossTierConfig(id, health, damage);
   }

   @Override
   public CompoundTag serializeNBT(HolderLookup.Provider provider) {
      CompoundTag tag = new CompoundTag();
      tag.putString("Name", this.id);
      tag.putFloat("HealthMultiplier", this.healthMultiplier);
      tag.putFloat("DamageMultiplier", this.damageMultiplier);
      return tag;
   }

   @Override
   public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
   }
}