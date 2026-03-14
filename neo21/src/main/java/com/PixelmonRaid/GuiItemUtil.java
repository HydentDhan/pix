package com.PixelmonRaid;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class GuiItemUtil {
    public static CompoundTag getGuiTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setGuiTag(ItemStack stack, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static void setStackName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    public static boolean hasGuiTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }
}