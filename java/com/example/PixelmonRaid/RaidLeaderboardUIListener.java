package com.example.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
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

        boolean guiRestored = false;

        if (player.tickCount % 5 == 0) {
            boolean dirty = false;
            for (int i = 0; i < player.inventory.getContainerSize(); i++) {
                ItemStack st = player.inventory.getItem(i);
                if (!st.isEmpty() && isLeaderboardItem(st)) {
                    player.inventory.removeItemNoUpdate(i);
                    dirty = true;
                }
            }
            ItemStack cursor = player.inventory.getCarried();
            if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
                player.inventory.setCarried(ItemStack.EMPTY);
                dirty = true;
            }
            if (dirty) {
                player.inventoryMenu.broadcastChanges();
                guiRestored = true;
            }
        }

        if (!openPages.containsKey(player.getUUID())) return;

        ItemStack cursor = player.inventory.getCarried();
        if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
            String name = TextFormatting.stripFormatting(cursor.getHoverName().getString()).toLowerCase();
            boolean handled = false;

            if (name.contains("next page")) {
                int page = openPages.get(player.getUUID());
                if (page > 0) {
                    player.inventory.setCarried(ItemStack.EMPTY);
                    player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                    RaidLeaderboardCommand.openLeaderboard(player, page + 1);
                    handled = true;
                }
            }
            else if (name.contains("previous page")) {
                int page = openPages.get(player.getUUID());
                if (page > 0) {
                    player.inventory.setCarried(ItemStack.EMPTY);
                    player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                    RaidLeaderboardCommand.openLeaderboard(player, page - 1);
                    handled = true;
                }
            }
            else {
                player.inventory.setCarried(ItemStack.EMPTY);
            }

            if (!handled) {
                guiRestored = true;
            }
        }

        if (guiRestored) {
            int page = openPages.getOrDefault(player.getUUID(), 1);
            player.getServer().execute(() -> {
                if (page == 0) {
                    RaidLeaderboardCommand.openLastRaidLeaderboard(player);
                } else {
                    RaidLeaderboardCommand.openLeaderboard(player, page);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        openPages.remove(event.getPlayer().getUUID());
    }

    private static boolean isLeaderboardItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.hasTag() && stack.getTag().getBoolean("RaidGuiItem");
    }
}
