package com.example.PixelmonRaid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.Util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public class RaidAdminCommand {

    public static final Map<UUID, String> playerMenuState = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> editingItemIndex = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> purchasingItemIndex = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> playerShopPage = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> editingLootLevel = new ConcurrentHashMap<>();
    public static final int[] REWARD_SLOTS = { 12, 13, 14, 21, 22, 23, 30, 31, 32 };

    private static net.minecraft.item.Item getLogoItem() {
        try {
            String id = PixelmonRaidConfig.getInstance().getUiLogoItem();
            net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return item != null ? item : Items.NETHER_STAR;
        } catch (Exception e) { return Items.NETHER_STAR; }
    }

    private static net.minecraft.item.Item getBorderItem() {
        try {
            String id = PixelmonRaidConfig.getInstance().getUiBorderItem();
            net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return item != null ? item : Items.PURPLE_STAINED_GLASS_PANE;
        } catch (Exception e) { return Items.PURPLE_STAINED_GLASS_PANE; }
    }

    private static String getName() { return PixelmonRaidConfig.getInstance().getUiServerName(); }
    private static String getColor() { return PixelmonRaidConfig.getInstance().getUiThemeColor(); }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("raidadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            ServerWorld world = ctx.getSource().getLevel();
                            RaidSession session = RaidSpawner.getSession(world);
                            if (session != null) {
                                session.startWaitingNow(world.getGameTime());
                                ctx.getSource().sendSuccess(new StringTextComponent("§aForce started raid sequence."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerWorld world = ctx.getSource().getLevel();
                            RaidSession session = RaidSpawner.getSession(world);
                            if (session != null) {
                                session.cleanup();
                                ctx.getSource().sendSuccess(new StringTextComponent("§cRaid stopped."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("forcewin")
                        .executes(context -> {
                            RaidSession session = RaidSpawner.getSessionSafe((ServerWorld) context.getSource().getLevel());
                            if (session != null) {
                                session.finishRaid(true, null);
                                context.getSource().sendSuccess(new StringTextComponent("§aRaid Force Won!"), true);
                            } else {
                                context.getSource().sendFailure(new StringTextComponent("No active battle to win."));
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("setcenter")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                            ServerPlayerEntity p = (ServerPlayerEntity) ctx.getSource().getEntity();
                            RaidSession s = RaidSpawner.getSession(p.getLevel());
                            if (s != null) {
                                s.setCenter(p.blockPosition());
                                String pos = p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ();
                                ctx.getSource().sendSuccess(new StringTextComponent("§aRaid Center set to " + pos), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("setwarp")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                            ServerPlayerEntity p = (ServerPlayerEntity) ctx.getSource().getEntity();
                            RaidSession s = RaidSpawner.getSession(p.getLevel());
                            if (s != null) {
                                s.setPlayerSpawn(p.blockPosition());
                                String pos = p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ();
                                ctx.getSource().sendSuccess(new StringTextComponent("§aRaid Player Warp set to " + pos), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            PixelmonRaidConfig.reload();
                            ServerWorld world = ctx.getSource().getLevel();
                            RaidSession session = RaidSpawner.getSessionSafe(world);
                            if (session != null && session.getState() == RaidSession.State.IDLE) {
                                session.forceResetTimer();
                                ctx.getSource().sendSuccess(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgReloadTimerSynced()), true);
                            } else {
                                ctx.getSource().sendSuccess(new StringTextComponent(PixelmonRaidConfig.getInstance().getMsgReload()), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("tokens")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgument.getPlayer(ctx, "player");
                                                    int amt = IntegerArgumentType.getInteger(ctx, "amount");
                                                    RaidSaveData.get((ServerWorld)target.level).addTokens(target.getUUID(), amt);
                                                    ctx.getSource().sendSuccess(new StringTextComponent("§aGave " + amt + " tokens to " + target.getName().getString()), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgument.getPlayer(ctx, "player");
                                                    int amt = IntegerArgumentType.getInteger(ctx, "amount");
                                                    boolean success = RaidSaveData.get((ServerWorld)target.level).removeTokens(target.getUUID(), amt);
                                                    if(success) ctx.getSource().sendSuccess(new StringTextComponent("§aTook " + amt + " tokens from " + target.getName().getString()), true);
                                                    else ctx.getSource().sendFailure(new StringTextComponent("§cPlayer does not have enough tokens."));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("setholo")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                            ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
                            PixelmonRaidConfig.getInstance().setHoloLocation(
                                    player.getX(), player.getY() + 2.0, player.getZ(),
                                    player.level.dimension().location().toString()
                            );
                            ctx.getSource().sendSuccess(new StringTextComponent("§a§l✔ Holographic Leaderboard set to your location!"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("toggleholo")
                        .executes(ctx -> {
                            boolean current = PixelmonRaidConfig.getInstance().isHologramEnabled();
                            PixelmonRaidConfig.getInstance().setHologramEnabled(!current);
                            String status = !current ? "§aENABLED" : "§cDISABLED";
                            ctx.getSource().sendSuccess(new StringTextComponent("§eHolographic Leaderboard is now " + status), true);
                            return 1;
                        })
                )
                .then(Commands.literal("shop")
                        // --- THE VISIBILITY FIX ---
                        // If internal shop is disabled, the /raidadmin shop command disappears!
                        .requires(source -> PixelmonRaidConfig.getInstance().isInternalShopEnabled())
                        .then(Commands.literal("add")
                                .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                                            ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
                                            int price = IntegerArgumentType.getInteger(ctx, "price");
                                            ItemStack held = player.getMainHandItem();
                                            if (held.isEmpty()) {
                                                ctx.getSource().sendFailure(new StringTextComponent("§cYou must hold an item to add it!"));
                                                return 0;
                                            }

                                            PixelmonRaidConfig.ShopItem item = new PixelmonRaidConfig.ShopItem();
                                            item.itemID = held.getItem().getRegistryName().toString();
                                            item.price = price;
                                            item.displayCount = held.getCount();
                                            item.nbt = held.hasTag() ? held.getTag().toString() : "";
                                            item.isCommand = false;

                                            PixelmonRaidConfig.getInstance().getRaidTokenShop().add(item);
                                            PixelmonRaidConfig.getInstance().save();

                                            ctx.getSource().sendSuccess(new StringTextComponent("§aAdded " + item.displayCount + "x " + item.itemID + " to the shop for " + price + " tokens."), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
                        context.getSource().sendFailure(new StringTextComponent("Only a player can open the Raid Admin GUI!"));
                        return 0;
                    }
                    return openHub((ServerPlayerEntity) context.getSource().getEntity());
                })
        );
    }

    public static void redrawCurrentMenu(ServerPlayerEntity player) {
        String state = playerMenuState.get(player.getUUID());
        if (state == null || !(player.containerMenu instanceof ChestContainer)) return;
        ChestContainer chest = (ChestContainer) player.containerMenu;

        boolean isRewardEditor = state.equals("killshot") || state.equals("participation") || state.matches("\\d+");

        for (int i = 0; i < chest.slots.size() - 36; i++) {
            boolean skip = false;
            if (isRewardEditor) {
                for (int rs : REWARD_SLOTS) if (i == rs) skip = true;
            }
            if (!skip) chest.getSlot(i).set(ItemStack.EMPTY);
        }

        if (state.equals("HUB")) fillHub(chest, player);
        else if (state.equals("REWARDS_HUB")) fillRewardsHub(chest, player);
        else if (state.equals("TOKEN_SHOP")) fillTokenShop(chest, player);
        else if (state.equals("SHOP_EDITOR")) fillShopEditor(chest, player);
        else if (state.equals("PRICE_EDITOR")) fillPriceEditor(chest, player, editingItemIndex.getOrDefault(player.getUUID(), 0));
        else if (state.equals("PURCHASE_UI")) fillPurchasePanel(chest, player, purchasingItemIndex.getOrDefault(player.getUUID(), 0));
        else if (isRewardEditor) fillRewardEditor(chest, player, state);

        chest.broadcastChanges();
    }

    public static void fillHub(ChestContainer chest, ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.level;
        RaidSession session = RaidSpawner.getSessionSafe(world);
        boolean autoEnabled = PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
        boolean isBattleActive = (session != null) && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH);
        fillBorder(chest, 6, getBorderItem());
        setSlot(chest, 4, createGuiItem(getLogoItem(), getName(), "§7Admin Control Panel", "§7Version 6.0"));
        if (isBattleActive) {
            setSlot(chest, 19, createGuiItem(Items.BARRIER, "§7§lRaid In Progress", "§cCannot start new raid", "§cwhile one is active."));
        } else {
            setSlot(chest, 19, createGuiItem(Items.LIME_CONCRETE, "§a§l▶ START RAID", "§7Force start a raid immediately.", " ", "§e▶ Click to Initialize"));
        }

        setSlot(chest, 21, createGuiItem(Items.DIAMOND_SWORD, "§b§l⚔ FORCE WIN", "§7Kill boss & give rewards.", " ", "§e▶ Click to Execute"));
        setSlot(chest, 23, createGuiItem(Items.TNT, "§c§l✖ ABORT RAID", "§7Cancel current raid.", " ", "§c▶ Click to Stop"));
        ItemStack toggleItem = createGuiItem(Items.LEVER,
                autoEnabled ? "§a§l✔ AUTO-RAIDS: ON" : "§c§l✖ AUTO-RAIDS: OFF",
                "§7Control whether raids spawn automatically.", " ",
                autoEnabled ? "§e▶ Click to PAUSE Raids" : "§e▶ Click to RESUME Raids"
        );
        if(autoEnabled) toggleItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
        setSlot(chest, 37, toggleItem);

        int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
        ItemStack diffItem = createGuiItem(Items.IRON_CHESTPLATE, getColor() + "§l★ Difficulty: " + diff, "§7Current Level: §f" + diff + "/5", " ", "§e▶ Click to Cycle");
        diffItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
        setSlot(chest, 25, diffItem);

        setSlot(chest, 39, createGuiItem(Items.CHEST, getColor() + "§l🎁 Loot Tables", "§7Edit rewards for specific Tiers.", " ", "§e▶ Click to Edit"));

        // --- HANDLES DISABLED SHOP VISUALLY ---
        if (PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
            setSlot(chest, 41, createGuiItem(Items.EMERALD, getColor() + "§l💰 Shop Editor", "§7Edit shop prices/items.", " ", "§e▶ Click to Edit"));
        } else {
            setSlot(chest, 41, createGuiItem(Items.BARRIER, "§c§l✖ Shop Disabled", "§7The internal shop is currently", "§7disabled in the config.", " ", "§8(Using External Shop)"));
        }

        fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
    }

    public static int openHub(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "HUB");
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillHub(chest, player);
            return chest;
        }, new StringTextComponent(getName())));
        return 1;
    }

    public static void fillRewardsHub(ChestContainer chest, ServerPlayerEntity player) {
        fillBorder(chest, 6, Items.BLUE_STAINED_GLASS_PANE);
        int level = editingLootLevel.getOrDefault(player.getUUID(), 1);
        ItemStack cycleBtn = createGuiItem(Items.BEACON, "§b§l⭐ Editing Tier: Level " + level + " ⭐", "§7Click here to cycle (1-5)", "§7and edit rewards for other difficulties!");
        cycleBtn.getOrCreateTag().putBoolean("EnchantmentGlint", true);
        setSlot(chest, 4, cycleBtn);

        PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(level);
        int rankCount = tier.getRankCount();
        int slot = 10;
        for (int i = 1; i <= rankCount; i++) {
            if (slot > 44) break;
            if ((slot + 1) % 9 == 0) slot += 2;
            ItemStack rankItem = createGuiItem(Items.GOLD_NUGGET, "§6⭐ Winner " + i + " Rewards", "§7For Boss Tier: §eLevel " + level, " ", "§7Click to Edit");
            CompoundNBT nbt = rankItem.getOrCreateTag();
            nbt.putInt("RankIndex", i);
            rankItem.setTag(nbt);

            setSlot(chest, slot, rankItem);
            slot++;
        }

        setSlot(chest, 46, createGuiItem(Items.LIME_WOOL, "§a§l[+] ADD WINNER", "§7Add a new placement reward."));
        setSlot(chest, 52, createGuiItem(Items.RED_WOOL, "§c§l[-] REMOVE WINNER", "§7Remove the highest placement."));
        setSlot(chest, 48, createGuiItem(Items.ENDER_CHEST, "§d🎁 Participation", "§7For Boss Tier: §eLevel " + level, " ", "§e▶ Click to Edit"));
        setSlot(chest, 50, createGuiItem(Items.DRAGON_EGG, "§c☠ Killshot", "§7For Boss Tier: §eLevel " + level, " ", "§e▶ Click to Edit"));
        setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Back to Dashboard"));

        fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
    }

    public static void openRewardsHub(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "REWARDS_HUB");
        editingLootLevel.putIfAbsent(player.getUUID(), 1);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillRewardsHub(chest, player);
            return chest;
        }, new StringTextComponent("§8§lLoot Table Editor")));
    }

    public static void fillRewardEditor(ChestContainer chest, ServerPlayerEntity player, String rank) {
        net.minecraft.item.Item bgItem = Items.BLACK_STAINED_GLASS_PANE;
        net.minecraft.item.Item outlineItem = getBorderItem();

        for(int i=0; i<54; i++) {
            boolean isRewardSlot = false;
            for(int s : REWARD_SLOTS) if(i == s) isRewardSlot = true;
            if(!isRewardSlot) {
                boolean isOutline = (i == 3 || i == 4 || i == 5 || i == 11 || i == 15 || i == 20 || i == 24 || i == 29 || i == 33 || i == 39 || i == 40 || i == 41);
                setSlot(chest, i, createGuiItem(isOutline ? outlineItem : bgItem, " "));
            }
        }

        int level = editingLootLevel.getOrDefault(player.getUUID(), 1);
        String label = rank.equals("participation") || rank.equals("killshot") ? rank.toUpperCase() : "WINNER " + rank;
        setSlot(chest, 4, createGuiItem(getLogoItem(), "§d§lEditing Tier " + level + ": §f" + label, "§7Place items inside the 3x3 frame below!"));
        setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ SAVE & RETURN", "§7Closes and Saves Items"));

        PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(level);
        List<String> rewards = null;
        if (rank.equals("participation")) rewards = tier.participation.items;
        else if (rank.equals("killshot")) rewards = tier.killshot.items;
        else {
            try {
                int r = Integer.parseInt(rank);
                PixelmonRaidConfig.RankReward rw = tier.winners.get("Winner_" + r);
                if (rw != null) rewards = rw.items;
            } catch (Exception e) {}
        }

        if (rewards != null) {
            int idx = 0;
            for (String s : rewards) {
                try {
                    String[] parts = s.split(" ", 3);
                    net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0]));
                    if (item != null) {
                        ItemStack stack = new ItemStack(item);
                        if (parts.length > 1) stack.setCount(Integer.parseInt(parts[1]));
                        if (parts.length > 2) {
                            try { CompoundNBT nbt = net.minecraft.nbt.JsonToNBT.parseTag(parts[2]);
                                stack.setTag(nbt); } catch (Exception e) {}
                        }
                        if (idx < REWARD_SLOTS.length) { setSlot(chest, REWARD_SLOTS[idx], stack);
                            idx++; }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static void openRewardEditor(ServerPlayerEntity player, String rank) {
        playerMenuState.put(player.getUUID(), rank);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            for (int slot : REWARD_SLOTS) chest.getSlot(slot).set(ItemStack.EMPTY);
            fillRewardEditor(chest, player, rank);
            return chest;
        }, new StringTextComponent("§5Editing: §d" + rank.toUpperCase())));
    }

    public static void fillTokenShop(ChestContainer chest, ServerPlayerEntity player) {
        fillBorder(chest, 6, getBorderItem());
        setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));

        int balance = RaidSaveData.get((ServerWorld) player.level).getTokens(player.getUUID());
        ItemStack balItem = createGuiItem(Items.SUNFLOWER, "§6§lYour Account", "§e" + balance + " Tokens");
        balItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
        setSlot(chest, 4, balItem);

        List<PixelmonRaidConfig.ShopItem> allItems = PixelmonRaidConfig.getInstance().getRaidTokenShop();
        int page = playerShopPage.getOrDefault(player.getUUID(), 0);
        int itemsPerPage = 28;
        int maxPages = (int) Math.ceil(allItems.size() / (double) itemsPerPage);
        if (maxPages == 0) maxPages = 1;
        if (page >= maxPages) page = maxPages - 1;
        if (page < 0) page = 0;
        playerShopPage.put(player.getUUID(), page);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
        int[] shopSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        for (int i = 0; i < (endIndex - startIndex); i++) {
            int trueIndex = startIndex + i;
            PixelmonRaidConfig.ShopItem entry = allItems.get(trueIndex);
            int slot = shopSlots[i];

            try {
                ResourceLocation res = new ResourceLocation(entry.itemID);
                net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(res);
                if (item != null) {
                    ItemStack display = new ItemStack(item, entry.displayCount);
                    CompoundNBT nbt = display.getOrCreateTag();

                    if (entry.nbt != null && !entry.nbt.isEmpty()) {
                        try { nbt.merge(net.minecraft.nbt.JsonToNBT.parseTag(entry.nbt));
                            display.setTag(nbt); } catch(Exception ignored){}
                    }

                    display.setHoverName(new StringTextComponent("§b" + display.getHoverName().getString()));
                    CompoundNBT d = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
                    ListNBT l = new ListNBT();
                    l.add(StringNBT.valueOf("{\"text\":\"§8§m------------------\"}"));
                    if (entry.isCommand) {
                        l.add(StringNBT.valueOf("{\"text\":\"§d✨ Command Reward ✨\"}"));
                    }
                    l.add(StringNBT.valueOf("{\"text\":\"§7Price: §6" + entry.price + " Tokens\"}"));
                    l.add(StringNBT.valueOf("{\"text\":\" \"}"));
                    l.add(StringNBT.valueOf("{\"text\":\"§e▶ Click to Purchase\"}"));
                    l.add(StringNBT.valueOf("{\"text\":\"§8§m------------------\"}"));
                    d.put("Lore", l);
                    nbt.put("display", d);
                    nbt.putBoolean("RaidGuiItem", true);
                    display.setTag(nbt);

                    display.getOrCreateTag().putInt("ShopIndex", trueIndex);
                    setSlot(chest, slot, display);
                }
            } catch (Exception ignored) {}
        }

        if (page > 0) setSlot(chest, 48, createGuiItem(Items.ARROW, "§e§l◀ PREVIOUS PAGE", "§7Page " + page));
        if (page < maxPages - 1) setSlot(chest, 50, createGuiItem(Items.ARROW, "§e§lNEXT PAGE ▶", "§7Page " + (page + 2)));
        setSlot(chest, 49, createGuiItem(Items.BARRIER, "§c§l✖ CLOSE SHOP", "§7Exit the menu"));
        fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
    }

    public static void openTokenShop(ServerPlayerEntity player) {
        // --- THE BRICK WALL FIX ---
        if (!PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
            player.sendMessage(new StringTextComponent("§c❌ The internal Raid Shop is permanently disabled. Please use /shop instead!"), Util.NIL_UUID);
            return;
        }

        playerMenuState.put(player.getUUID(), "TOKEN_SHOP");
        playerShopPage.put(player.getUUID(), 0);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillTokenShop(chest, player);
            return chest;
        }, new StringTextComponent(getColor() + "§lToken Shop")));
    }

    public static void fillPurchasePanel(ChestContainer chest, ServerPlayerEntity player, int itemIndex) {
        int qty = RaidAdminUIListener.shopQuantities.getOrDefault(player.getUUID(), 1);
        fillBorder(chest, 6, Items.GRAY_STAINED_GLASS_PANE);
        setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));

        List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
        if (itemIndex >= items.size()) { openTokenShop(player); return; }

        PixelmonRaidConfig.ShopItem entry = items.get(itemIndex);
        ResourceLocation res = new ResourceLocation(entry.itemID);
        int totalCost = entry.price * qty;
        int totalItems = entry.displayCount * qty;
        int balance = RaidSaveData.get((ServerWorld) player.level).getTokens(player.getUUID());
        boolean canAfford = balance >= totalCost;

        setSlot(chest, 4, createGuiItem(Items.OAK_SIGN, "§e§lQuantity Selected", "§fCurrent: §b" + qty, " ", "§7Use the +/- buttons below", "§7to change amount."));
        ItemStack product = new ItemStack(ForgeRegistries.ITEMS.getValue(res), Math.min(64, totalItems));
        CompoundNBT nbt = product.getOrCreateTag();
        if (entry.nbt != null && !entry.nbt.isEmpty()) {
            try { nbt.merge(net.minecraft.nbt.JsonToNBT.parseTag(entry.nbt));
                product.setTag(nbt); } catch(Exception ignored){}
        }
        product.setHoverName(new StringTextComponent("§6§l" + totalItems + "x " + product.getHoverName().getString()));
        CompoundNBT disp = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
        ListNBT lore = new ListNBT();
        if (entry.isCommand) lore.add(StringNBT.valueOf("{\"text\":\"§d✨ Command Reward ✨\"}"));
        lore.add(StringNBT.valueOf("{\"text\":\"§7Total Cost: §e" + totalCost + " Tokens\"}"));
        lore.add(StringNBT.valueOf("{\"text\":\" \"}"));
        lore.add(StringNBT.valueOf("{\"text\":\"" + (canAfford ? "§a✔ You can afford this" : "§c✖ Insufficient Tokens") + "\"}"));
        disp.put("Lore", lore);
        nbt.put("display", disp);
        nbt.putBoolean("RaidGuiItem", true);
        product.setTag(nbt);
        setSlot(chest, 13, product);

        setSlot(chest, 10, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-64", "§7Decrease"));
        setSlot(chest, 11, createGuiItem(Items.ORANGE_STAINED_GLASS_PANE, "§c-10", "§7Decrease"));
        setSlot(chest, 12, createGuiItem(Items.YELLOW_STAINED_GLASS_PANE, "§c-1", "§7Decrease"));
        setSlot(chest, 14, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+1", "§7Increase"));
        setSlot(chest, 15, createGuiItem(Items.GREEN_STAINED_GLASS_PANE, "§a+10", "§7Increase"));
        setSlot(chest, 16, createGuiItem(Items.EMERALD_BLOCK, "§a+64", "§7Increase"));
        setSlot(chest, 22, createGuiItem(Items.GOLD_BLOCK, "§6§lMAX", "§7Buy max affordable", "§7(Up to 64)"));
        if (canAfford) {
            setSlot(chest, 31, createGuiItem(Items.LIME_TERRACOTTA, "§a§l✔ CONFIRM PURCHASE", "§7Cost: §e" + totalCost + " Tokens", " ", "§e▶ Click to Buy"));
        } else {
            setSlot(chest, 31, createGuiItem(Items.RED_TERRACOTTA, "§c§l✖ CANNOT AFFORD", "§7You need " + (totalCost - balance) + " more tokens."));
        }
        setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ CANCEL", "§7Return to Shop"));
        fillEmptySlots(chest, 54, Items.BLACK_STAINED_GLASS_PANE);
    }

    public static void openPurchasePanel(ServerPlayerEntity player, int itemIndex) {
        playerMenuState.put(player.getUUID(), "PURCHASE_UI");
        purchasingItemIndex.put(player.getUUID(), itemIndex);
        if (!RaidAdminUIListener.shopQuantities.containsKey(player.getUUID())) {
            RaidAdminUIListener.shopQuantities.put(player.getUUID(), 1);
        }
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillPurchasePanel(chest, player, itemIndex);
            return chest;
        }, new StringTextComponent("§8§lConfirm Purchase")));
    }

    public static void fillShopEditor(ChestContainer chest, ServerPlayerEntity player) {
        for (int i = 0; i < 9; i++) setSlot(chest, i, createGuiItem(Items.BLACK_STAINED_GLASS_PANE, " "));
        for (int i = 45; i < 54; i++) setSlot(chest, i, createGuiItem(Items.BLACK_STAINED_GLASS_PANE, " "));
        setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));
        List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
        int page = playerShopPage.getOrDefault(player.getUUID(), 0);
        int itemsPerPage = 36;
        int maxPages = (int) Math.ceil(items.size() / (double) itemsPerPage);
        if (maxPages == 0) maxPages = 1;
        if (page >= maxPages) page = maxPages - 1;
        if (page < 0) page = 0;
        playerShopPage.put(player.getUUID(), page);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            PixelmonRaidConfig.ShopItem entry = items.get(i);
            try {
                ResourceLocation res = new ResourceLocation(entry.itemID);
                net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(res);
                if (item != null) {
                    ItemStack display = new ItemStack(item, entry.displayCount);
                    CompoundNBT nbt = display.getOrCreateTag();

                    if (entry.nbt != null && !entry.nbt.isEmpty()) {
                        try { nbt.merge(net.minecraft.nbt.JsonToNBT.parseTag(entry.nbt));
                            display.setTag(nbt); } catch(Exception ignored){}
                    }

                    display.setHoverName(new StringTextComponent("§b" + display.getHoverName().getString()));
                    nbt.putInt("ShopIndex", i);
                    nbt.putBoolean("RaidGuiItem", true);
                    display.setTag(nbt);
                    setSlot(chest, slot, display);
                    slot++;
                }
            } catch(Exception ignored) {}
        }

        if (page > 0) setSlot(chest, 48, createGuiItem(Items.ARROW, "§e§l◀ PREVIOUS PAGE", "§7Page " + page));
        if (page < maxPages - 1) setSlot(chest, 50, createGuiItem(Items.ARROW, "§e§lNEXT PAGE ▶", "§7Page " + (page + 2)));
        setSlot(chest, 49, createGuiItem(Items.DARK_OAK_DOOR, "§c§l↩ RETURN", "§7Back to Dashboard"));
        fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
    }

    public static void openShopEditor(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "SHOP_EDITOR");
        playerShopPage.put(player.getUUID(), 0);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillShopEditor(chest, player);
            return chest;
        }, new StringTextComponent("§8§lShop Editor")));
    }

    public static void fillPriceEditor(ChestContainer chest, ServerPlayerEntity player, int index) {
        fillBorder(chest, 6, Items.BLACK_STAINED_GLASS_PANE);
        setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));

        List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
        if (index >= 0 && index < items.size()) {
            PixelmonRaidConfig.ShopItem entry = items.get(index);
            ResourceLocation res = new ResourceLocation(entry.itemID);

            ItemStack display = new ItemStack(ForgeRegistries.ITEMS.getValue(res), entry.displayCount);
            CompoundNBT nbt = display.getOrCreateTag();
            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try { nbt.merge(net.minecraft.nbt.JsonToNBT.parseTag(entry.nbt));
                    display.setTag(nbt); } catch(Exception ignored){}
            }

            display.setHoverName(new StringTextComponent("§6§lCurrent Settings"));
            CompoundNBT d = new CompoundNBT();
            ListNBT l = new ListNBT();
            l.add(StringNBT.valueOf("{\"text\":\"§7Price: §e" + entry.price + "\"}"));
            l.add(StringNBT.valueOf("{\"text\":\"§7Stock: §f" + entry.displayCount + "\"}"));
            d.put("Lore", l);
            nbt.put("display", d);
            nbt.putBoolean("RaidGuiItem", true);
            display.setTag(nbt);
            setSlot(chest, 13, display);
            setSlot(chest, 10, createGuiItem(Items.RED_DYE, "§cPrice: -100", "§7Decrease cost"));
            setSlot(chest, 11, createGuiItem(Items.ORANGE_DYE, "§cPrice: -10", "§7Decrease cost"));
            setSlot(chest, 12, createGuiItem(Items.PINK_DYE, "§cPrice: -1", "§7Decrease cost"));
            setSlot(chest, 14, createGuiItem(Items.LIME_DYE, "§aPrice: +1", "§7Increase cost"));
            setSlot(chest, 15, createGuiItem(Items.GREEN_DYE, "§aPrice: +10", "§7Increase cost"));
            setSlot(chest, 16, createGuiItem(Items.EMERALD, "§aPrice: +100", "§7Increase cost"));
            setSlot(chest, 53, createGuiItem(Items.BARRIER, "§4§lDELETE ITEM", "§7Remove from Shop", "§c⚠ Cannot be undone"));
        }
        setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ BACK", "§7Back to Shop Editor"));
        fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
    }

    public static void openItemPriceEditor(ServerPlayerEntity player, int index) {
        playerMenuState.put(player.getUUID(), "PRICE_EDITOR");
        editingItemIndex.put(player.getUUID(), index);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillPriceEditor(chest, player, index);
            return chest;
        }, new StringTextComponent("§8§lEdit Item")));
    }

    public static void setSlot(ChestContainer container, int slot, ItemStack stack) {
        if (slot >= 0 && slot < container.slots.size() - 36) {
            container.getSlot(slot).set(stack);
        }
    }

    private static void fillBorder(ChestContainer container, int rows, net.minecraft.item.Item pane) {
        for (int i = 0; i < 9; i++) setSlot(container, i, createGuiItem(pane, " "));
        for (int i = (rows - 1) * 9; i < rows * 9; i++) setSlot(container, i, createGuiItem(pane, " "));
        for (int i = 0; i < rows; i++) {
            setSlot(container, i * 9, createGuiItem(pane, " "));
            setSlot(container, i * 9 + 8, createGuiItem(pane, " "));
        }
    }

    private static void fillEmptySlots(ChestContainer container, int size, net.minecraft.item.Item paneItem) {
        for (int i = 0; i < size; i++) {
            if (container.getSlot(i).getItem().isEmpty()) {
                setSlot(container, i, createGuiItem(paneItem, " "));
            }
        }
    }

    public static ItemStack createGuiItem(net.minecraft.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(new StringTextComponent(name));
        CompoundNBT nbt = stack.getOrCreateTag();
        nbt.putBoolean("RaidGuiItem", true);
        CompoundNBT display = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
        ListNBT loreList = new ListNBT();
        for (String line : loreLines) {
            if(line.isEmpty()) {
                loreList.add(StringNBT.valueOf("{\"text\":\" \"}"));
            } else {
                loreList.add(StringNBT.valueOf("{\"text\":\"" + line + "\"}"));
            }
        }
        display.put("Lore", loreList);
        nbt.put("display", display);
        stack.setTag(nbt);
        return stack;
    }
}