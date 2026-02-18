package com.PixelmonRaid;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.Util;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.inventory.container.Slot;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.play.server.SSetSlotPacket;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class RaidAdminUIListener {

    public static final Map<UUID, Integer> shopQuantities = new HashMap<>();
    private static final Set<UUID> IS_TRANSITIONING = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntityItem() != null) {
            ItemStack stack = event.getEntityItem().getItem();
            if (isGuiItem(stack)) {
                event.getEntityItem().remove();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        UUID id = event.getPlayer().getUUID();
        if (!IS_TRANSITIONING.contains(id)) {
            RaidAdminCommand.playerMenuState.remove(id);
            RaidAdminCommand.editingItemIndex.remove(id);
            RaidAdminCommand.purchasingItemIndex.remove(id);
            shopQuantities.remove(id);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level.isClientSide) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;

        boolean isMenuOpen = RaidAdminCommand.playerMenuState.containsKey(player.getUUID());
        String state = isMenuOpen ? RaidAdminCommand.playerMenuState.get(player.getUUID()) : null;

        boolean dirty = false;
        boolean handledClick = false;

        ItemStack cursorStack = player.inventory.getCarried();
        if (!cursorStack.isEmpty() && isGuiItem(cursorStack)) {
            String rawName = cursorStack.getHoverName().getString();

            player.inventory.setCarried(ItemStack.EMPTY);
            player.connection.send(new SSetSlotPacket(-1, -1, ItemStack.EMPTY));
            dirty = true;

            if (isMenuOpen && !handledClick) {
                handledClick = handleButtonClick(player, rawName, state, cursorStack);
            }
        }

        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack st = player.inventory.getItem(i);
            if (!st.isEmpty() && isGuiItem(st)) {
                String rawName = st.getHoverName().getString();
                player.inventory.removeItemNoUpdate(i);
                dirty = true;
                if (isMenuOpen && !handledClick) {
                    handledClick = handleButtonClick(player, rawName, state, st);
                }
            }
        }

        if (isMenuOpen) {
            boolean isRewardEditor = state.equals("killshot") || state.equals("participation") || state.matches("\\d+");
            if (isRewardEditor && player.containerMenu != null) {
                for (int i = 0; i < 54; i++) {
                    if (i >= player.containerMenu.slots.size()) break;
                    if (i == 49) continue;

                    boolean isValidSlot = false;
                    for (int slot : RaidAdminCommand.REWARD_SLOTS) {
                        if (i == slot) { isValidSlot = true; break; }
                    }

                    if (!isValidSlot) {
                        Slot s = player.containerMenu.getSlot(i);
                        if (s != null && s.hasItem()) {
                            ItemStack is = s.getItem();
                            if (!isGuiItem(is)) {
                                if (player.inventory.add(is)) {
                                    s.set(new ItemStack(Items.GRAY_STAINED_GLASS_PANE));
                                } else {
                                    player.drop(is, false);
                                    s.set(new ItemStack(Items.GRAY_STAINED_GLASS_PANE));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (dirty) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();

            if (isMenuOpen && !handledClick && !IS_TRANSITIONING.contains(player.getUUID())) {
                reopenCurrentGui(player, state);
            }
        }
    }

    private static boolean isGuiItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.hasTag() && stack.getTag().getBoolean("RaidGuiItem");
    }

    private static boolean handleButtonClick(ServerPlayerEntity player, String rawName, String state, ItemStack stack) {
        String cleanName = TextFormatting.stripFormatting(rawName);
        String lowName = cleanName.toLowerCase();

        ServerWorld world = (ServerWorld) player.level;
        RaidSession session = RaidSpawner.getSessionSafe(world);

        if (lowName.contains("return") || lowName.contains("back") || lowName.contains("cancel") || lowName.contains("exit") || lowName.contains("close")) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
                saveRewardsFromEditor(player, null, state);
            }

            if (state.equals("PRICE_EDITOR")) switchMenu(player, "SHOP_EDITOR", () -> RaidAdminCommand.openShopEditor(player));
            else if (state.startsWith("SHOP_") && !state.equals("SHOP_EDITOR")) switchMenu(player, "TOKEN_SHOP", () -> RaidAdminCommand.openTokenShop(player));
            else if (state.equals("PURCHASE_UI")) switchMenu(player, "TOKEN_SHOP", () -> RaidAdminCommand.openTokenShop(player));
            else if (state.equals("REWARDS_HUB")) switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            else if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
                switchMenu(player, "REWARDS_HUB", () -> RaidAdminCommand.openRewardsHub(player));
            }
            else switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }

        if (state.equals("REWARDS_HUB")) {
            if (lowName.contains("add rank")) {
                PixelmonRaidConfig.getInstance().addRank();
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                switchMenu(player, "REWARDS_HUB", () -> RaidAdminCommand.openRewardsHub(player));
                return true;
            }
            if (lowName.contains("remove rank")) {
                PixelmonRaidConfig.getInstance().removeLastRank();
                player.playSound(SoundEvents.ANVIL_BREAK, 1.0f, 1.0f);
                switchMenu(player, "REWARDS_HUB", () -> RaidAdminCommand.openRewardsHub(player));
                return true;
            }
            if (stack.hasTag() && stack.getTag().contains("RankIndex")) {
                int rankIdx = stack.getTag().getInt("RankIndex");
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
                switchMenu(player, String.valueOf(rankIdx), () -> RaidAdminCommand.openRewardEditor(player, String.valueOf(rankIdx)));
                return true;
            }
            if (lowName.contains("killshot")) {
                switchMenu(player, "killshot", () -> RaidAdminCommand.openRewardEditor(player, "killshot"));
                return true;
            }
            if (lowName.contains("participation")) {
                switchMenu(player, "participation", () -> RaidAdminCommand.openRewardEditor(player, "participation"));
                return true;
            }
        }

        if (lowName.contains("start raid") || lowName.contains("begin raid")) {
            if(session!=null) session.startBattleNow();
            switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }
        if (lowName.contains("force win") || lowName.contains("complete")) {
            if(session!=null) session.finishRaid(true, null);
            switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }
        if (lowName.contains("abort") || lowName.contains("stop raid") || lowName.contains("end raid")) {
            if(session!=null) session.forceResetTimer();
            switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }
        if (lowName.contains("auto-raids") || lowName.contains("auto-spawn") || lowName.contains("disable") || lowName.contains("enable")) {
            boolean newState = !PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
            PixelmonRaidConfig.getInstance().setAutoRaidEnabled(newState);

            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if(newState) player.sendMessage(new StringTextComponent("§a[Raid] Auto-Spawn Enabled!"), Util.NIL_UUID);
            else player.sendMessage(new StringTextComponent("§c[Raid] Auto-Spawn Paused."), Util.NIL_UUID);

            switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }

        if (state.equals("PRICE_EDITOR")) {
            int idx = RaidAdminCommand.editingItemIndex.getOrDefault(player.getUUID(), -1);
            List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();

            if (idx >= 0 && idx < items.size()) {
                String entry = items.get(idx);
                String[] parts = entry.split(" ");
                int price = Integer.parseInt(parts[1]);
                int stock = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                boolean changed = false;

                if (cleanName.contains("+100")) { price += 100; changed = true; }
                else if (cleanName.contains("+10")) { price += 10; changed = true; }
                else if (cleanName.contains("-100")) { price = Math.max(1, price - 100); changed = true; }
                else if (cleanName.contains("-10")) { price = Math.max(1, price - 10); changed = true; }
                else if (cleanName.contains("DELETE ITEM")) {
                    items.remove(idx);
                    PixelmonRaidConfig.getInstance().setRaidShopItems(items);
                    player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
                    switchMenu(player, "SHOP_EDITOR", () -> RaidAdminCommand.openShopEditor(player));
                    return true;
                }

                if (changed) {
                    StringBuilder newEntry = new StringBuilder(parts[0] + " " + price + " " + stock);
                    if (parts.length > 3) newEntry.append(" ").append(parts[3]);
                    if (entry.contains("{")) newEntry.append(entry.substring(entry.indexOf("{")));

                    items.set(idx, newEntry.toString());
                    PixelmonRaidConfig.getInstance().setRaidShopItems(items);
                    player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    switchMenu(player, "PRICE_EDITOR", () -> RaidAdminCommand.openItemPriceEditor(player, idx));
                    return true;
                }
            } else {
                switchMenu(player, "SHOP_EDITOR", () -> RaidAdminCommand.openShopEditor(player));
                return true;
            }
        }

        if (cleanName.contains("Difficulty:")) {
            int next = PixelmonRaidConfig.getInstance().getRaidDifficulty() + 1;
            if (next > 5) next = 1;
            PixelmonRaidConfig.getInstance().setRaidDifficulty(next);
            switchMenu(player, "HUB", () -> RaidAdminCommand.openHub(player));
            return true;
        }

        if (state.equals("SHOP_EDITOR")) {
            if (stack.hasTag() && stack.getTag().contains("ShopIndex")) {
                int index = stack.getTag().getInt("ShopIndex");
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
                switchMenu(player, "PRICE_EDITOR", () -> RaidAdminCommand.openItemPriceEditor(player, index));
                return true;
            }
        }

        if (lowName.contains("shop editor") || lowName.contains("edit shop")) { switchMenu(player, "SHOP_EDITOR", () -> RaidAdminCommand.openShopEditor(player)); return true; }
        if (lowName.contains("loot tables") || lowName.contains("rewards")) { switchMenu(player, "REWARDS_HUB", () -> RaidAdminCommand.openRewardsHub(player)); return true; }

        if (state.equals("TOKEN_SHOP")) {
            if (lowName.contains("pokéballs") || lowName.contains("pokeballs")) { switchMenu(player, "SHOP_BALLS", () -> RaidAdminCommand.openShopCategory(player, "BALLS")); return true; }
            if (lowName.contains("rare items")) { switchMenu(player, "SHOP_RARE", () -> RaidAdminCommand.openShopCategory(player, "RARE")); return true; }
            if (lowName.contains("keys")) { switchMenu(player, "SHOP_KEYS", () -> RaidAdminCommand.openShopCategory(player, "KEYS")); return true; }
            if (lowName.contains("special")) { switchMenu(player, "SHOP_SPECIAL", () -> RaidAdminCommand.openShopCategory(player, "SPECIAL")); return true; }
        }

        if (state.equals("PURCHASE_UI")) {
            if (RaidAdminCommand.purchasingItemIndex.containsKey(player.getUUID())) {
                int idx = RaidAdminCommand.purchasingItemIndex.get(player.getUUID());
                int currentQty = shopQuantities.getOrDefault(player.getUUID(), 1);
                boolean update = false;

                if (cleanName.contains("MAX")) {
                    List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
                    if (idx < items.size()) {
                        String entry = items.get(idx);
                        try {
                            int price = Integer.parseInt(entry.split(" ")[1]);
                            int bal = RaidSaveData.get(world).getTokens(player.getUUID());
                            if (price > 0) {
                                int affordable = bal / price;
                                currentQty = Math.max(1, Math.min(64, affordable));
                                update = true;
                            }
                        } catch(Exception e) {}
                    }
                }
                else if (cleanName.contains("+64")) { currentQty += 64; update = true; }
                else if (cleanName.contains("+10")) { currentQty += 10; update = true; }
                else if (cleanName.contains("+1")) { currentQty += 1; update = true; }
                else if (cleanName.contains("-64")) { currentQty = Math.max(1, currentQty - 64); update = true; }
                else if (cleanName.contains("-10")) { currentQty = Math.max(1, currentQty - 10); update = true; }
                else if (cleanName.contains("-1")) { currentQty = Math.max(1, currentQty - 1); update = true; }

                if (update) {
                    shopQuantities.put(player.getUUID(), currentQty);
                    player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    switchMenu(player, "PURCHASE_UI", () -> RaidAdminCommand.openPurchasePanel(player, idx));
                    return true;
                }

                if (cleanName.contains("CONFIRM PURCHASE")) {
                    List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
                    if (idx < items.size()) {
                        String entry = items.get(idx);
                        String[] parts = entry.split(" ");
                        int baseCost = Integer.parseInt(parts[1]);
                        int baseCount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                        int totalCost = baseCost * currentQty;
                        int totalItems = baseCount * currentQty;
                        int bal = RaidSaveData.get(world).getTokens(player.getUUID());
                        if (bal >= totalCost) {
                            RaidSaveData.get(world).removeTokens(player.getUUID(), totalCost);
                            ResourceLocation res = new ResourceLocation(parts[0]);
                            ItemStack give = new ItemStack(ForgeRegistries.ITEMS.getValue(res), totalItems);
                            try { if (parts.length > 4 || entry.contains("{")) { String nbtStr = entry.substring(entry.indexOf("{")); CompoundNBT nbt = JsonToNBT.parseTag(nbtStr); give.setTag(nbt);
                            } } catch(Exception ignored){}
                            if (!player.inventory.add(give)) { player.drop(give, false); }
                            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                            player.sendMessage(new StringTextComponent("§aSuccessful Purchase!"), Util.NIL_UUID);
                            switchMenu(player, "TOKEN_SHOP", () -> RaidAdminCommand.openTokenShop(player));
                        } else {
                            player.playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        }
                    }
                    return true;
                }
            }
        }

        if (state.startsWith("SHOP_")) {
            if (stack.hasTag() && stack.getTag().contains("ShopIndex")) {
                int index = stack.getTag().getInt("ShopIndex");
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
                shopQuantities.put(player.getUUID(), 1);
                switchMenu(player, "PURCHASE_UI", () -> RaidAdminCommand.openPurchasePanel(player, index));
                return true;
            }
        }

        return false;
    }

    private static void switchMenu(ServerPlayerEntity player, String newState, Runnable openAction) {
        IS_TRANSITIONING.add(player.getUUID());
        try {
            openAction.run();
        } finally {
            IS_TRANSITIONING.remove(player.getUUID());
        }
    }

    public static void reopenCurrentGui(ServerPlayerEntity player, String state) {
        IS_TRANSITIONING.add(player.getUUID());
        try {
            if (state.equals("HUB")) RaidAdminCommand.openHub(player);
            else if (state.equals("REWARDS_HUB")) RaidAdminCommand.openRewardsHub(player);
            else if (state.equals("TOKEN_SHOP")) RaidAdminCommand.openTokenShop(player);
            else if (state.equals("PURCHASE_UI")) {
                int idx = RaidAdminCommand.purchasingItemIndex.getOrDefault(player.getUUID(), 0);
                RaidAdminCommand.openPurchasePanel(player, idx);
            }
            else if (state.startsWith("SHOP_") && !state.equals("SHOP_EDITOR")) {
                RaidAdminCommand.openShopCategory(player, state.substring(5));
            }
            else if (state.equals("SHOP_EDITOR")) RaidAdminCommand.openShopEditor(player);
            else if (state.equals("PRICE_EDITOR")) {
                int idx = RaidAdminCommand.editingItemIndex.getOrDefault(player.getUUID(), 0);
                RaidAdminCommand.openItemPriceEditor(player, idx);
            }
            else if (state.equals("killshot") || state.equals("participation") || state.matches("\\d+")) {
                RaidAdminCommand.openRewardEditor(player, state);
            }
        } finally {
            IS_TRANSITIONING.remove(player.getUUID());
        }
    }

    private static void saveRewardsFromEditor(ServerPlayerEntity player, Object ignored, String rankId) {
        List<String> serializedItems = new ArrayList<>();
        for (int slot : RaidAdminCommand.REWARD_SLOTS) {
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) continue;
            String entry = stack.getItem().getRegistryName().toString() + " " + stack.getCount() + (stack.hasTag() ? " " + stack.getTag().toString() : "");
            serializedItems.add(entry);
        }

        if (rankId.equalsIgnoreCase("killshot")) PixelmonRaidConfig.getInstance().setKillShotRewards(serializedItems);
        else if (rankId.equalsIgnoreCase("participation")) PixelmonRaidConfig.getInstance().setRewardsParticipation(serializedItems);
        else {
            try {
                int r = Integer.parseInt(rankId);
                PixelmonRaidConfig.getInstance().setRewardsForRank(r, serializedItems);
            } catch (NumberFormatException e) { }
        }

        player.sendMessage(new StringTextComponent("§a[Raid] Saved Items for " + rankId + "!"), Util.NIL_UUID);
    }
}
