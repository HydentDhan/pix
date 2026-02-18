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
import net.minecraft.network.play.server.SSetSlotPacket;

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

        if (!openPages.containsKey(player.getUUID())) return;

        ItemStack cursor = player.inventory.getCarried();
        if (!cursor.isEmpty() && isLeaderboardItem(cursor)) {
            String name = TextFormatting.stripFormatting(cursor.getHoverName().getString()).toLowerCase();
            boolean handled = false;

            player.inventory.setCarried(ItemStack.EMPTY);
            player.connection.send(new SSetSlotPacket(-1, -1, ItemStack.EMPTY));

            if (name.contains("next page")) {
                int page = openPages.get(player.getUUID());
                if (page > 0) {
                    player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                    RaidLeaderboardCommand.openLeaderboard(player, page + 1);
                    handled = true;
                }
            }
            else if (name.contains("previous page")) {
                int page = openPages.get(player.getUUID());
                if (page > 0) {
                    player.playSound(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);
                    RaidLeaderboardCommand.openLeaderboard(player, page - 1);
                    handled = true;
                }
            }

            if (!handled) {
                guiRestored = true;
            }
        }

        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack st = player.inventory.getItem(i);
            if (!st.isEmpty() && isLeaderboardItem(st)) {
                player.inventory.removeItemNoUpdate(i);
                guiRestored = true;
            }
        }

        if (guiRestored) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();

            int page = openPages.getOrDefault(player.getUUID(), 1);
            if (page == 0) {
                RaidLeaderboardCommand.openLastRaidLeaderboard(player);
            } else {
                RaidLeaderboardCommand.openLeaderboard(player, page);
            }
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
