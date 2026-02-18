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

    public String getName() { return id; }
    public float getHealthMultiplier() { return healthMultiplier; }
    public float getDamageMultiplier() { return damageMultiplier; }

    public void toBuffer(PacketBuffer buffer) {
        buffer.writeUtf(id);
        buffer.writeFloat(healthMultiplier);
        buffer.writeFloat(damageMultiplier);
    }

    public static RaidBossTierConfig fromBuffer(PacketBuffer buffer) {
        String id = buffer.readUtf(32767);
        float health = buffer.readFloat();
        float damage = buffer.readFloat();
        return new RaidBossTierConfig(id, health, damage);
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Name", id);
        tag.putFloat("HealthMultiplier", healthMultiplier);
        tag.putFloat("DamageMultiplier", damageMultiplier);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
    }
}