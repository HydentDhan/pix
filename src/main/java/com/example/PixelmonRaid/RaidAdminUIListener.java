package com.example.PixelmonRaid;

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
import net.minecraft.inventory.container.ChestContainer;
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
            String state = RaidAdminCommand.playerMenuState.get(id);
            if (state != null && (state.matches("\\d+") || state.equals("killshot") || state.equals("participation"))) {
                if (event.getPlayer() instanceof ServerPlayerEntity) {
                    saveRewardsFromEditor((ServerPlayerEntity) event.getPlayer(), null, state);
                }
            }

            RaidAdminCommand.playerMenuState.remove(id);
            RaidAdminCommand.editingItemIndex.remove(id);
            RaidAdminCommand.purchasingItemIndex.remove(id);
            RaidAdminCommand.playerShopPage.remove(id);
            RaidAdminCommand.editingLootLevel.remove(id);
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
        boolean requiresRedraw = false;

        ItemStack cursorStack = player.inventory.getCarried();
        if (!cursorStack.isEmpty() && isGuiItem(cursorStack)) {
            String rawName = cursorStack.getHoverName().getString();
            player.inventory.setCarried(ItemStack.EMPTY);
            player.connection.send(new SSetSlotPacket(-1, -1, ItemStack.EMPTY));
            dirty = true;
            requiresRedraw = true;
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
                requiresRedraw = true;
                if (isMenuOpen && !handledClick) {
                    handledClick = handleButtonClick(player, rawName, state, st);
                }
            }
        }

        if (isMenuOpen) {
            boolean isRewardEditor = state.equals("killshot") ||
                    state.equals("participation") || state.matches("\\d+");
            if (isRewardEditor && player.containerMenu != null && !IS_TRANSITIONING.contains(player.getUUID())) {
                boolean backgroundTampered = false;
                for (int i = 0; i < 54; i++) {
                    if (i >= player.containerMenu.slots.size()) break;
                    if (i == 49) continue;

                    boolean isRewardSlot = false;
                    for (int slot : RaidAdminCommand.REWARD_SLOTS) {
                        if (i == slot) { isRewardSlot = true;
                            break; }
                    }

                    if (!isRewardSlot) {
                        Slot s = player.containerMenu.getSlot(i);
                        if (s != null) {
                            if (s.hasItem() && !isGuiItem(s.getItem())) {
                                ItemStack stolenItem = s.getItem().copy();
                                s.set(ItemStack.EMPTY);
                                if (!player.inventory.add(stolenItem)) player.drop(stolenItem, false);
                                backgroundTampered = true;
                            } else if (!s.hasItem()) {
                                backgroundTampered = true;
                            }
                        }
                    }
                }

                if (backgroundTampered) {
                    requiresRedraw = true;
                }
            }
        }

        if (dirty) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();
        }

        if (isMenuOpen && requiresRedraw && !handledClick && !IS_TRANSITIONING.contains(player.getUUID())) {
            RaidAdminCommand.redrawCurrentMenu(player);
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
        if (lowName.contains("next page")) {
            int page = RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
            RaidAdminCommand.playerShopPage.put(player.getUUID(), page + 1);
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }
        if (lowName.contains("prev page") || lowName.contains("previous page")) {
            int page = RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
            RaidAdminCommand.playerShopPage.put(player.getUUID(), Math.max(0, page - 1));
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }

        if (lowName.contains("close shop")) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeContainer();
            return true;
        }

        if (lowName.contains("return") || lowName.contains("back") || lowName.contains("cancel") || lowName.contains("exit") || lowName.contains("save & return")) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
                saveRewardsFromEditor(player, null, state);
            }

            if (state.equals("PRICE_EDITOR")) switchMenu(player, "SHOP_EDITOR");
            else if (state.equals("PURCHASE_UI")) switchMenu(player, "TOKEN_SHOP");
            else if (state.equals("REWARDS_HUB")) switchMenu(player, "HUB");
            else if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
                switchMenu(player, "REWARDS_HUB");
            }
            else switchMenu(player, "HUB");
            return true;
        }

        if (state.equals("REWARDS_HUB")) {
            if (lowName.contains("editing tier")) {
                int lvl = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                lvl = (lvl >= 5) ? 1 : lvl + 1;
                RaidAdminCommand.editingLootLevel.put(player.getUUID(), lvl);
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
                RaidAdminCommand.redrawCurrentMenu(player);
                return true;
            }
            if (lowName.contains("add winner")) {
                int lvl = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                PixelmonRaidConfig.getInstance().getTierRewards(lvl).addRank();
                PixelmonRaidConfig.getInstance().save();
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                RaidAdminCommand.redrawCurrentMenu(player);
                return true;
            }
            if (lowName.contains("remove winner")) {
                int lvl = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                PixelmonRaidConfig.getInstance().getTierRewards(lvl).removeLastRank();
                PixelmonRaidConfig.getInstance().save();
                player.playSound(SoundEvents.ANVIL_BREAK, 1.0f, 1.0f);
                RaidAdminCommand.redrawCurrentMenu(player);
                return true;
            }
            if (stack.hasTag() && stack.getTag().contains("RankIndex")) {
                int rankIdx = stack.getTag().getInt("RankIndex");
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
                switchMenu(player, String.valueOf(rankIdx));
                return true;
            }
            if (lowName.contains("killshot")) { switchMenu(player, "killshot");
                return true; }
            if (lowName.contains("participation")) { switchMenu(player, "participation");
                return true; }
        }

        if (lowName.contains("start raid") || lowName.contains("begin raid")) {
            if(session!=null) session.startBattleNow();
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }
        if (lowName.contains("force win") || lowName.contains("complete")) {
            if(session!=null) session.finishRaid(true, null);
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }
        if (lowName.contains("abort") || lowName.contains("stop raid") || lowName.contains("end raid")) {
            if(session!=null) session.forceResetTimer();
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }

        // --- THE BUG FIX: REMOVED THE WORD "DISABLE" FROM THE AUTO RAID CHECK ---
        if (lowName.contains("auto-raids") || lowName.contains("auto-spawn")) {
            boolean newState = !PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
            PixelmonRaidConfig.getInstance().setAutoRaidEnabled(newState);
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if(newState) player.sendMessage(new StringTextComponent("§a[Raid] Auto-Spawn Enabled!"), Util.NIL_UUID);
            else player.sendMessage(new StringTextComponent("§c[Raid] Auto-Spawn Paused."), Util.NIL_UUID);
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }

        if (state.equals("PRICE_EDITOR")) {
            int idx = RaidAdminCommand.editingItemIndex.getOrDefault(player.getUUID(), -1);
            List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();

            if (idx >= 0 && idx < items.size()) {
                PixelmonRaidConfig.ShopItem entry = items.get(idx);
                boolean changed = false;

                if (cleanName.contains("+100")) { entry.price += 100; changed = true;
                }
                else if (cleanName.contains("+10")) { entry.price += 10;
                    changed = true; }
                else if (cleanName.contains("+1")) { entry.price += 1;
                    changed = true; }
                else if (cleanName.contains("-100")) { entry.price = Math.max(1, entry.price - 100);
                    changed = true; }
                else if (cleanName.contains("-10")) { entry.price = Math.max(1, entry.price - 10);
                    changed = true; }
                else if (cleanName.contains("-1")) { entry.price = Math.max(1, entry.price - 1);
                    changed = true; }
                else if (cleanName.contains("DELETE ITEM")) {
                    items.remove(idx);
                    PixelmonRaidConfig.getInstance().save();
                    player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.0f);
                    switchMenu(player, "SHOP_EDITOR");
                    return true;
                }

                if (changed) {
                    PixelmonRaidConfig.getInstance().save();
                    player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    RaidAdminCommand.redrawCurrentMenu(player);
                    return true;
                }
            } else {
                switchMenu(player, "SHOP_EDITOR");
                return true;
            }
        }

        if (cleanName.contains("Difficulty:")) {
            int next = PixelmonRaidConfig.getInstance().getRaidDifficulty() + 1;
            if (next > 5) next = 1;
            PixelmonRaidConfig.getInstance().setRaidDifficulty(next);
            player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.5f);
            RaidAdminCommand.redrawCurrentMenu(player);
            return true;
        }

        if (state.equals("SHOP_EDITOR") || state.equals("TOKEN_SHOP")) {
            if (stack.hasTag() && stack.getTag().contains("ShopIndex")) {
                int index = stack.getTag().getInt("ShopIndex");
                player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);

                if (state.equals("SHOP_EDITOR")) {
                    RaidAdminCommand.editingItemIndex.put(player.getUUID(), index);
                    switchMenu(player, "PRICE_EDITOR");
                } else {
                    shopQuantities.put(player.getUUID(), 1);
                    RaidAdminCommand.purchasingItemIndex.put(player.getUUID(), index);
                    switchMenu(player, "PURCHASE_UI");
                }
                return true;
            }
        }

        // --- THE BUG FIX: THE ADMIN GUI BRICK WALL ---
        // I also added the word "disabled" into this check so it correctly captures the button click!
        if (lowName.contains("shop editor") || lowName.contains("edit shop") || lowName.contains("shop disabled")) {
            if (!PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
                player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(new StringTextComponent("§c❌ Shop Editor is locked! You must enable the internal shop in the JSON file first."), Util.NIL_UUID);
                return true; // Stop code right here so it doesn't do anything else!
            }
            switchMenu(player, "SHOP_EDITOR");
            return true;
        }

        if (lowName.contains("loot tables") || lowName.contains("rewards")) { switchMenu(player, "REWARDS_HUB"); return true;
        }

        if (state.equals("PURCHASE_UI")) {
            if (RaidAdminCommand.purchasingItemIndex.containsKey(player.getUUID())) {
                int idx = RaidAdminCommand.purchasingItemIndex.get(player.getUUID());
                int currentQty = shopQuantities.getOrDefault(player.getUUID(), 1);
                boolean update = false;

                List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
                if (idx < items.size()) {
                    PixelmonRaidConfig.ShopItem entry = items.get(idx);
                    if (cleanName.contains("MAX")) {
                        int price = entry.price;
                        int bal = RaidSaveData.get(world).getTokens(player.getUUID());
                        if (price > 0) {
                            int affordable = bal / price;
                            currentQty = Math.max(1, Math.min(64, affordable));
                            update = true;
                        }
                    }
                    else if (cleanName.contains("+64")) { currentQty += 64;
                        update = true; }
                    else if (cleanName.contains("+10")) { currentQty += 10;
                        update = true; }
                    else if (cleanName.contains("+1")) { currentQty += 1;
                        update = true; }
                    else if (cleanName.contains("-64")) { currentQty = Math.max(1, currentQty - 64);
                        update = true; }
                    else if (cleanName.contains("-10")) { currentQty = Math.max(1, currentQty - 10);
                        update = true; }
                    else if (cleanName.contains("-1")) { currentQty = Math.max(1, currentQty - 1);
                        update = true; }

                    if (update) {
                        shopQuantities.put(player.getUUID(), currentQty);
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.5f);
                        RaidAdminCommand.redrawCurrentMenu(player);
                        return true;
                    }

                    if (cleanName.contains("CONFIRM PURCHASE")) {
                        int baseCost = entry.price;
                        int baseCount = entry.displayCount;
                        int totalCost = baseCost * currentQty;
                        int totalItems = baseCount * currentQty;
                        int bal = RaidSaveData.get(world).getTokens(player.getUUID());
                        if (bal >= totalCost) {
                            RaidSaveData.get(world).removeTokens(player.getUUID(), totalCost);
                            if (entry.isCommand && entry.commands != null && !entry.commands.isEmpty()) {
                                for (int i = 0; i < currentQty; i++) {
                                    for (String cmdTemplate : entry.commands) {
                                        String cmd = cmdTemplate.replace("%player%", player.getGameProfile().getName());
                                        try {
                                            player.getServer().getCommands().performCommand(player.getServer().createCommandSourceStack(), cmd);
                                        } catch(Exception ignored){}
                                    }
                                }
                            } else
                            {
                                ResourceLocation res = new ResourceLocation(entry.itemID);
                                ItemStack give = new ItemStack(ForgeRegistries.ITEMS.getValue(res), totalItems);
                                try { if (entry.nbt != null && !entry.nbt.isEmpty()) { give.setTag(JsonToNBT.parseTag(entry.nbt));
                                } } catch(Exception ignored){}
                                if (!player.inventory.add(give)) { player.drop(give, false);
                                }
                            }

                            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
                            player.sendMessage(new StringTextComponent("§aSuccessful Purchase!"), Util.NIL_UUID);
                            switchMenu(player, "TOKEN_SHOP");
                        } else {
                            player.playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void switchMenu(ServerPlayerEntity player, String newState) {
        IS_TRANSITIONING.add(player.getUUID());
        try {
            boolean isEnteringEditor = newState.equals("killshot") || newState.equals("participation") ||
                    newState.matches("\\d+");
            if (isEnteringEditor && player.containerMenu instanceof ChestContainer) {
                ChestContainer chest = (ChestContainer) player.containerMenu;
                for (int slot : RaidAdminCommand.REWARD_SLOTS) {
                    if (slot >= 0 && slot < chest.slots.size() - 36) {
                        chest.getSlot(slot).set(ItemStack.EMPTY);
                    }
                }
            }

            RaidAdminCommand.playerMenuState.put(player.getUUID(), newState);
            RaidAdminCommand.redrawCurrentMenu(player);
        } finally {
            IS_TRANSITIONING.remove(player.getUUID());
        }
    }

    private static void saveRewardsFromEditor(ServerPlayerEntity player, Object ignored, String rankId) {
        List<String> serializedItems = new ArrayList<>();
        for (int slot : RaidAdminCommand.REWARD_SLOTS) {
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR) continue;

            if (stack.hasTag() && stack.getTag().getBoolean("RaidGuiItem")) continue;
            String entry = stack.getItem().getRegistryName().toString() + " " + stack.getCount() + (stack.hasTag() ? " " + stack.getTag().toString() : "");
            serializedItems.add(entry);
        }

        int lvl = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
        PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(lvl);
        // Safely overwrites physical items without destroying JSON Commands
        if (rankId.equalsIgnoreCase("killshot")) tier.killshot.items = serializedItems;
        else if (rankId.equalsIgnoreCase("participation")) tier.participation.items = serializedItems;
        else {
            try {
                int r = Integer.parseInt(rankId);
                if (!tier.winners.containsKey("Winner_" + r)) {
                    tier.winners.put("Winner_" + r, new PixelmonRaidConfig.RankReward());
                }
                tier.winners.get("Winner_" + r).items = serializedItems;
            } catch (NumberFormatException e) { }
        }
        PixelmonRaidConfig.getInstance().save();
        player.sendMessage(new StringTextComponent("§a[Raid] Saved physical items for Tier " + lvl + " - " + rankId + "!"), Util.NIL_UUID);
    }
}