package com.example.PixelmonRaid;

import net.minecraft.inventory.container.ContainerType;

public final class PixelmonRaidModReflectionHelper {

    private PixelmonRaidModReflectionHelper() {}

    public static void assignContainer(ContainerType<?> container) {
        try {
            java.lang.reflect.Field f = PixelmonRaidMod.class.getDeclaredField("RAID_REWARDS_CONTAINER");
            f.setAccessible(true);
            f.set(null, container);
        } catch (Throwable ignored) {}
    }
}
