package com.PixelmonRaid;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.INBTSerializable;

public class RaidBossTierConfig implements INBTSerializable<CompoundNBT> {
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

   public void toBuffer(PacketBuffer buffer) {
      buffer.writeUtf(this.id);
      buffer.writeFloat(this.healthMultiplier);
      buffer.writeFloat(this.damageMultiplier);
   }

   public static RaidBossTierConfig fromBuffer(PacketBuffer buffer) {
      String id = buffer.readUtf(32767);
      float health = buffer.readFloat();
      float damage = buffer.readFloat();
      return new RaidBossTierConfig(id, health, damage);
   }

   public CompoundNBT serializeNBT() {
      CompoundNBT tag = new CompoundNBT();
      tag.putString("Name", this.id);
      tag.putFloat("HealthMultiplier", this.healthMultiplier);
      tag.putFloat("DamageMultiplier", this.damageMultiplier);
      return tag;
   }

   public void deserializeNBT(CompoundNBT nbt) {
   }
}