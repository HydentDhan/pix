package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class RaidLeaderboardUIListener {

    public static final Map<UUID, Integer> openPages = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntityItem() != null) {
            ItemStack stack = event.getEntityItem().getItem();
            if (isLeaderboardItem(stack)) {
                event.getEntityItem().remove();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level.isClientSide) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;

        if (player.tickCount % 5 == 0) {
            boolean dirty = false;
            for (int i = 0; i < player.inventory.getContainerSize(); i++) {
                ItemStack st = player.inventory.getItem(i);
                if (!st.isEmpty() && isLeaderboardItem(st)) {
                    player.inventory.removeItem(st);
                    dirty = true;
                }
            }
            if (dirty) player.inventoryMenu.broadcastChanges();
        }

        if (!openPages.containsKey(player.getUUID())) return;

        ItemStack cursor = player.inventory.getCarried();
        if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
            String name = TextFormatting.stripFormatting(cursor.getHoverName().getString()).toLowerCase();

            if (name.contains("next page")) {
                int page = openPages.get(player.getUUID());
                player.inventory.setCarried(ItemStack.EMPTY);
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                RaidLeaderboardCommand.openLeaderboard(player, page + 1);
            }
            else if (name.contains("previous page")) {
                int page = openPages.get(player.getUUID());
                player.inventory.setCarried(ItemStack.EMPTY);
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                RaidLeaderboardCommand.openLeaderboard(player, page - 1);
            }
            else {
                player.inventory.setCarried(ItemStack.EMPTY);
                player.refreshContainer(player.containerMenu);
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        openPages.remove(event.getPlayer().getUUID());
    }

    private static boolean isLeaderboardItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() == Items.PLAYER_HEAD || stack.getItem() == Items.PAPER ||
                stack.getItem() == Items.ARROW || stack.getItem() == Items.CLOCK ||
                stack.getItem() == Items.SUNFLOWER || stack.getItem() == Items.GOLD_NUGGET ||
                stack.getItem() == Items.DIAMOND_SWORD || stack.getItem() == Items.GRAY_STAINED_GLASS_PANE) {

            if (stack.hasTag() && stack.getTag().contains("display")) {
                String name = TextFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase();
                if (name.contains("rank #") || name.contains("total damage:") ||
                        name.contains("next page") || name.contains("previous page") ||
                        name.contains("page ") || name.contains("your stats") ||
                        name.contains("raids joined") || name.contains("raids won") ||
                        name.contains("win streak") || name.contains("balance")) {
                    return true;
                }
            }
        }
        return false;
    }
}