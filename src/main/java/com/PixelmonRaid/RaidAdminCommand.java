package com.PixelmonRaid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

public class RaidAdminCommand {

    public static final Map<UUID, String> playerMenuState = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> editingItemIndex = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> purchasingItemIndex = new ConcurrentHashMap<>();
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
                                ctx.getSource().sendSuccess(new StringTextComponent("§a§l✔ Configuration Reloaded & Timer Synced!"), true);
                            } else {
                                ctx.getSource().sendSuccess(new StringTextComponent("§a§l✔ Configuration Reloaded!"), true);
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
                        .then(Commands.literal("add")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((c, b) -> ISuggestionProvider.suggest(new String[]{"BALLS", "RARE", "KEYS", "SPECIAL"}, b))
                                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) return 0;
                                                    ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
                                                    String cat = StringArgumentType.getString(ctx, "category").toUpperCase();
                                                    int price = IntegerArgumentType.getInteger(ctx, "price");

                                                    ItemStack held = player.getMainHandItem();
                                                    if (held.isEmpty()) {
                                                        ctx.getSource().sendFailure(new StringTextComponent("§cYou must hold an item to add it!"));
                                                        return 0;
                                                    }

                                                    String id = held.getItem().getRegistryName().toString();
                                                    int count = 1;

                                                    String nbt = held.hasTag() ? " " + held.getTag().toString() : "";
                                                    String entry = id + " " + price + " " + count + " " + cat + nbt;
                                                    List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
                                                    items.add(entry);
                                                    PixelmonRaidConfig.getInstance().setRaidShopItems(items);

                                                    ctx.getSource().sendSuccess(new StringTextComponent("§aAdded 1x " + id + " to " + cat + " for " + price + " tokens."), true);
                                                    return 1;
                                                })
                                        )
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

    public static int openHub(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "HUB");
        ServerWorld world = (ServerWorld) player.level;
        RaidSession session = RaidSpawner.getSessionSafe(world);

        boolean autoEnabled = PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
        boolean isBattleActive = (session != null) && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH);

        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer container = ChestContainer.sixRows(id, playerInv);
            fillBorder(container, 6, getBorderItem());

            container.setItem(4, createGuiItem(getLogoItem(), getName(), "§7Admin Control Panel", "§7Version 3.5"));

            if (isBattleActive) {
                container.setItem(19, createGuiItem(Items.BARRIER, "§7§lRaid In Progress", "§cCannot start new raid", "§cwhile one is active."));
            } else {
                container.setItem(19, createGuiItem(Items.LIME_CONCRETE, "§a§l▶ START RAID", "§7Force start a raid immediately.", " ", "§e▶ Click to Initialize"));
            }

            container.setItem(21, createGuiItem(Items.DIAMOND_SWORD, "§b§l⚔ FORCE WIN", "§7Kill boss & give rewards.", " ", "§e▶ Click to Execute"));
            container.setItem(23, createGuiItem(Items.TNT, "§c§l✖ ABORT RAID", "§7Cancel current raid.", " ", "§c▶ Click to Stop"));

            ItemStack toggleItem = createGuiItem(Items.LEVER,
                    autoEnabled ? "§a§l✔ AUTO-RAIDS: ON" : "§c§l✖ AUTO-RAIDS: OFF",
                    "§7Control whether raids spawn automatically.", " ",
                    autoEnabled ? "§e▶ Click to PAUSE Raids" : "§e▶ Click to RESUME Raids"
            );
            if(autoEnabled) toggleItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
            container.setItem(37, toggleItem);

            int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
            ItemStack diffItem = createGuiItem(Items.IRON_CHESTPLATE, getColor() + "§l★ Difficulty: " + diff, "§7Current Level: §f" + diff + "/5", " ", "§e▶ Click to Cycle");
            diffItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
            container.setItem(25, diffItem);

            container.setItem(39, createGuiItem(Items.CHEST, getColor() + "§l🎁 Loot Tables", "§7Edit rewards for top ranks.", " ", "§e▶ Click to Edit"));
            container.setItem(41, createGuiItem(Items.EMERALD, getColor() + "§l💰 Shop Editor", "§7Edit shop prices/items.", " ", "§e▶ Click to Edit"));

            fillEmptySlots(container, 54, Items.GRAY_STAINED_GLASS_PANE);
            return container;
        }, new StringTextComponent(getName())));

        return 1;
    }

    public static void openRewardsHub(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "REWARDS_HUB");
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer container = ChestContainer.sixRows(id, playerInv);
            fillBorder(container, 6, Items.BLUE_STAINED_GLASS_PANE);

            container.setItem(0, createGuiItem(getLogoItem(), getName()));

            PixelmonRaidConfig cfg = PixelmonRaidConfig.getInstance();
            int rankCount = cfg.getRankCount();

            int slot = 10;
            for (int i = 1; i <= rankCount; i++) {
                if (slot > 44) break;
                if ((slot + 1) % 9 == 0) slot += 2;

                String rankName = "Rank " + i;
                ItemStack rankItem = createGuiItem(Items.GOLD_NUGGET, "§6⭐ " + rankName + " Rewards", "§7Click to Edit", " ");
                CompoundNBT nbt = rankItem.getOrCreateTag();
                nbt.putInt("RankIndex", i);
                rankItem.setTag(nbt);

                container.setItem(slot, rankItem);
                slot++;
            }

            container.setItem(46, createGuiItem(Items.LIME_WOOL, "§a§l[+] ADD RANK", "§7Create a new Rank reward tier."));
            container.setItem(52, createGuiItem(Items.RED_WOOL, "§c§l[-] REMOVE RANK", "§7Remove the last Rank (" + rankCount + ")."));
            container.setItem(48, createGuiItem(Items.ENDER_CHEST, "§d🎁 Participation", "§7Rewards for everyone else", " ", "§e▶ Click to Edit"));
            container.setItem(50, createGuiItem(Items.DRAGON_EGG, "§c☠ Killshot", "§7Bonus for final hit", " ", "§e▶ Click to Edit"));
            container.setItem(49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Back to Dashboard"));

            fillEmptySlots(container, 54, Items.GRAY_STAINED_GLASS_PANE);
            return container;
        }, new StringTextComponent("§8§lLoot Table Editor")));
    }

    public static void openRewardEditor(ServerPlayerEntity player, String rank) {
        playerMenuState.put(player.getUUID(), rank);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            for(int i=0; i<54; i++) chest.setItem(i, createGuiItem(Items.GRAY_STAINED_GLASS_PANE, " "));
            for (int slot : REWARD_SLOTS) chest.setItem(slot, ItemStack.EMPTY);

            List<String> rewards;
            if (rank.equals("participation")) rewards = PixelmonRaidConfig.getInstance().getRewardsParticipation();
            else if (rank.equals("killshot")) rewards = PixelmonRaidConfig.getInstance().getKillShotRewards();
            else {
                try {
                    int r = Integer.parseInt(rank);
                    rewards = PixelmonRaidConfig.getInstance().getRewardsForRank(r);
                } catch (Exception e) { rewards = new ArrayList<>(); }
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
                            if (parts.length > 2) { try { CompoundNBT nbt = net.minecraft.nbt.JsonToNBT.parseTag(parts[2]);
                                stack.setTag(nbt);
                            } catch (Exception e) {} }
                            if (idx < REWARD_SLOTS.length) { chest.setItem(REWARD_SLOTS[idx], stack); idx++; }
                        }
                    } catch (Exception ignored) {}
                }
            }
            chest.setItem(49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Closes and Saves Rewards"));
            return chest;
        }, new StringTextComponent("§5Editing: §d" + rank.toUpperCase())));
    }

    public static void openTokenShop(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "TOKEN_SHOP");
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.threeRows(id, playerInv);
            fillBorder(chest, 3, getBorderItem());
            chest.setItem(0, createGuiItem(getLogoItem(), getName()));

            int balance = RaidSaveData.get((ServerWorld) player.level).getTokens(player.getUUID());
            ItemStack balItem = createGuiItem(Items.SUNFLOWER, "§6§lYour Balance", "§e" + balance + " Tokens");
            balItem.getOrCreateTag().putBoolean("EnchantmentGlint", true);
            chest.setItem(4, balItem);

            chest.setItem(10, createGuiItem(Items.SLIME_BALL, "§a§l🔴 Pokéballs", "§7Catching supplies", " ", "§e▶ Click to Browse"));
            chest.setItem(12, createGuiItem(Items.EXPERIENCE_BOTTLE, "§d§l✨ Rare Items", "§7Candies & Enhancements", " ", "§e▶ Click to Browse"));
            chest.setItem(14, createGuiItem(Items.TRIPWIRE_HOOK, "§5§l🔑 Keys", "§7Crate Keys", " ", "§e▶ Click to Browse"));
            chest.setItem(16, createGuiItem(Items.NETHER_STAR, "§b§l⭐ Special", "§7Unique Rewards", " ", "§e▶ Click to Browse"));

            chest.setItem(22, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Close Shop"));

            fillEmptySlots(chest, 27, Items.GRAY_STAINED_GLASS_PANE);
            return chest;
        }, new StringTextComponent(getColor() + "§lToken Shop")));
    }

    public static void openPurchasePanel(ServerPlayerEntity player, int itemIndex) {
        playerMenuState.put(player.getUUID(), "PURCHASE_UI");
        purchasingItemIndex.put(player.getUUID(), itemIndex);

        if (!RaidAdminUIListener.shopQuantities.containsKey(player.getUUID())) {
            RaidAdminUIListener.shopQuantities.put(player.getUUID(), 1);
        }
        int qty = RaidAdminUIListener.shopQuantities.get(player.getUUID());

        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.fiveRows(id, playerInv);
            fillBorder(chest, 5, Items.GRAY_STAINED_GLASS_PANE);

            chest.setItem(0, createGuiItem(getLogoItem(), getName()));

            List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
            if (itemIndex >= items.size()) { openTokenShop(player); return chest; }

            String entry = items.get(itemIndex);
            String[] parts = entry.split(" ");
            ResourceLocation res = new ResourceLocation(parts[0]);
            int costPerUnit = Integer.parseInt(parts[1]);
            int baseCount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;

            int totalCost = costPerUnit * qty;
            int totalItems = baseCount * qty;
            int balance = RaidSaveData.get((ServerWorld) player.level).getTokens(player.getUUID());
            boolean canAfford = balance >= totalCost;

            ItemStack info = createGuiItem(Items.OAK_SIGN, "§e§lQuantity Selected", "§fCurrent: §b" + qty, " ", "§7Use the +/- buttons below", "§7to change amount.");
            chest.setItem(4, info);

            ItemStack product = new ItemStack(ForgeRegistries.ITEMS.getValue(res), Math.min(64, totalItems));
            product.setHoverName(new StringTextComponent("§6§l" + totalItems + "x " + product.getItem().getName(product).getString()));
            CompoundNBT nbt = product.getOrCreateTag();
            if (parts.length > 4 || entry.contains("{")) {
                try {
                    String nbtStr = entry.substring(entry.indexOf("{"));
                    CompoundNBT extra = net.minecraft.nbt.JsonToNBT.parseTag(nbtStr);
                    nbt.merge(extra);
                } catch(Exception ignored){}
            }
            CompoundNBT disp = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
            ListNBT lore = new ListNBT();
            lore.add(StringNBT.valueOf("{\"text\":\"§7Total Cost: §e" + totalCost + " Tokens\"}"));
            lore.add(StringNBT.valueOf("{\"text\":\" \"}"));
            lore.add(StringNBT.valueOf("{\"text\":\"" + (canAfford ? "§a✔ You can afford this" : "§c✖ Insufficient Tokens") + "\"}"));
            disp.put("Lore", lore);
            nbt.put("display", disp);
            nbt.putBoolean("RaidGuiItem", true);
            product.setTag(nbt);
            chest.setItem(13, product);

            chest.setItem(10, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-64", "§7Decrease"));
            chest.setItem(11, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-10", "§7Decrease"));
            chest.setItem(12, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-1", "§7Decrease"));

            chest.setItem(14, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+1", "§7Increase"));
            chest.setItem(15, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+10", "§7Increase"));
            chest.setItem(16, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+64", "§7Increase"));

            chest.setItem(22, createGuiItem(Items.GOLD_BLOCK, "§6§lMAX", "§7Buy max affordable", "§7(Up to 64)"));

            if (canAfford) {
                chest.setItem(31, createGuiItem(Items.LIME_TERRACOTTA, "§a§l✔ CONFIRM PURCHASE", "§7Cost: §e" + totalCost + " Tokens", " ", "§e▶ Click to Buy"));
            } else {
                chest.setItem(31, createGuiItem(Items.RED_TERRACOTTA, "§c§l✖ CANNOT AFFORD", "§7You need " + (totalCost - balance) + " more tokens."));
            }
            chest.setItem(40, createGuiItem(Items.ARROW, "§c§l↩ CANCEL", "§7Return to Shop"));

            fillEmptySlots(chest, 45, Items.BLACK_STAINED_GLASS_PANE);
            return chest;
        }, new StringTextComponent("§8§lConfirm Purchase")));
    }

    public static void openShopCategory(ServerPlayerEntity player, String category) {
        String state = "SHOP_" + category.toUpperCase();
        playerMenuState.put(player.getUUID(), state);

        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            fillBorder(chest, 6, getBorderItem());
            chest.setItem(0, createGuiItem(getLogoItem(), getName()));

            int balance = RaidSaveData.get((ServerWorld) player.level).getTokens(player.getUUID());
            chest.setItem(4, createGuiItem(Items.SUNFLOWER, "§6§lBalance: §e" + balance));

            List<String> allItems = PixelmonRaidConfig.getInstance().getRaidShopItems();
            int slot = 10;

            for (int i = 0; i < allItems.size(); i++) {
                if (slot % 9 == 0) slot++;
                if (slot % 9 == 8) slot += 2;
                if (slot >= 44) break;

                String entry = allItems.get(i);
                if (!matchesCategory(entry, category)) continue;

                try {
                    String[] parts = entry.split(" ");
                    if (parts.length >= 2) {
                        ResourceLocation res = new ResourceLocation(parts[0]);
                        net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(res);
                        if (item != null) {
                            int costBase = Integer.parseInt(parts[1]);
                            int countBase = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;

                            ItemStack display = new ItemStack(item, countBase);
                            display.setHoverName(new StringTextComponent("§b" + item.getName(display).getString()));
                            CompoundNBT nbt = display.getOrCreateTag();
                            if (parts.length > 4 || entry.contains("{")) {
                                try {
                                    String nbtStr = entry.substring(entry.indexOf("{"));
                                    CompoundNBT extra = net.minecraft.nbt.JsonToNBT.parseTag(nbtStr);
                                    nbt.merge(extra);
                                } catch(Exception ignored){}
                            }

                            CompoundNBT d = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
                            ListNBT l = new ListNBT();
                            l.add(StringNBT.valueOf("{\"text\":\"§8§m------------------\"}"));
                            l.add(StringNBT.valueOf("{\"text\":\"§7Price: §6" + costBase + " Tokens\"}"));
                            l.add(StringNBT.valueOf("{\"text\":\" \"}"));
                            l.add(StringNBT.valueOf("{\"text\":\"§e▶ Click to Purchase\"}"));
                            l.add(StringNBT.valueOf("{\"text\":\"§8§m------------------\"}"));
                            d.put("Lore", l);
                            nbt.put("display", d);
                            nbt.putBoolean("RaidGuiItem", true);
                            display.setTag(nbt);

                            display.getOrCreateTag().putInt("ShopIndex", i);
                            chest.setItem(slot, display);
                            slot++;
                        }
                    }
                } catch (Exception ignored) {}
            }
            chest.setItem(49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Back to Categories"));

            fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
            return chest;
        }, new StringTextComponent("§8§lShop: " + category)));
    }

    public static void openShopEditor(ServerPlayerEntity player) {
        playerMenuState.put(player.getUUID(), "SHOP_EDITOR");
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.sixRows(id, playerInv);
            for (int i = 0; i < 9; i++) chest.setItem(i, createGuiItem(Items.BLACK_STAINED_GLASS_PANE, " "));
            for (int i = 45; i < 54; i++) chest.setItem(i, createGuiItem(Items.BLACK_STAINED_GLASS_PANE, " "));
            chest.setItem(0, createGuiItem(getLogoItem(), getName()));

            List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
            int slot = 9;
            for (String entry : items) {
                if (slot >= 45) break;
                try {
                    String[] parts = entry.split(" ");
                    ResourceLocation res = new ResourceLocation(parts[0]);
                    net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(res);
                    if (item != null) {
                        int count = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                        ItemStack display = new ItemStack(item, count);
                        display.setHoverName(new StringTextComponent("§b" + item.getName(display).getString()));
                        CompoundNBT nbt = display.getOrCreateTag();
                        nbt.putInt("ShopIndex", items.indexOf(entry));
                        nbt.putBoolean("RaidGuiItem", true);
                        display.setTag(nbt);
                        chest.setItem(slot, display);
                        slot++;
                    }
                } catch(Exception ignored) {}
            }
            chest.setItem(49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Back to Dashboard"));

            fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
            return chest;
        }, new StringTextComponent("§8§lShop Editor")));
    }

    public static void openItemPriceEditor(ServerPlayerEntity player, int index) {
        playerMenuState.put(player.getUUID(), "PRICE_EDITOR");
        editingItemIndex.put(player.getUUID(), index);
        player.openMenu(new SimpleNamedContainerProvider((id, playerInv, p) -> {
            ChestContainer chest = ChestContainer.threeRows(id, playerInv);
            fillBorder(chest, 3, Items.BLACK_STAINED_GLASS_PANE);
            chest.setItem(0, createGuiItem(getLogoItem(), getName()));

            List<String> items = PixelmonRaidConfig.getInstance().getRaidShopItems();
            if (index >= 0 && index < items.size()) {
                String entry = items.get(index);
                String[] parts = entry.split(" ");
                ResourceLocation res = new ResourceLocation(parts[0]);
                int price = Integer.parseInt(parts[1]);
                int count = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                ItemStack display = new ItemStack(ForgeRegistries.ITEMS.getValue(res), count);
                display.setHoverName(new StringTextComponent("§6§lCurrent Settings"));
                CompoundNBT nbt = display.getOrCreateTag();
                CompoundNBT d = new CompoundNBT();
                ListNBT l = new ListNBT();
                l.add(StringNBT.valueOf("{\"text\":\"§7Price: §e" + price + "\"}"));
                l.add(StringNBT.valueOf("{\"text\":\"§7Stock: §f" + count + "\"}"));
                d.put("Lore", l);
                nbt.put("display", d);
                nbt.putBoolean("RaidGuiItem", true);
                display.setTag(nbt);
                chest.setItem(13, display);

                chest.setItem(10, createGuiItem(Items.RED_DYE, "§cPrice: -100", "§7Decrease cost"));
                chest.setItem(11, createGuiItem(Items.PINK_DYE, "§cPrice: -10", "§7Decrease cost"));
                chest.setItem(15, createGuiItem(Items.LIME_DYE, "§aPrice: +10", "§7Increase cost"));
                chest.setItem(16, createGuiItem(Items.GREEN_DYE, "§aPrice: +100", "§7Increase cost"));

                chest.setItem(26, createGuiItem(Items.BARRIER, "§4§lDELETE ITEM", "§7Remove from Shop", "§c⚠ Cannot be undone"));
            }
            chest.setItem(22, createGuiItem(Items.ARROW, "§c§l↩ BACK", "§7Back to Shop Editor"));

            fillEmptySlots(chest, 27, Items.GRAY_STAINED_GLASS_PANE);
            return chest;
        }, new StringTextComponent("§8§lEdit Item")));
    }

    private static boolean matchesCategory(String entry, String category) {
        String[] parts = entry.split(" ");
        if (parts.length > 3) {
            String catTag = parts[3].toUpperCase();
            if (catTag.equals(category)) return true;
        }
        String id = parts[0].toLowerCase();
        if (category.equals("BALLS")) return id.contains("ball");
        if (category.equals("KEYS")) return id.contains("key") || id.contains("crate");
        if (category.equals("RARE")) return id.contains("candy") || id.contains("bottle_cap") || id.contains("mint");
        if (category.equals("SPECIAL")) return !id.contains("ball") && !id.contains("key") && !id.contains("candy") && !id.contains("bottle_cap");
        return false;
    }

    private static void fillBorder(ChestContainer container, int rows, net.minecraft.item.Item pane) {
        for (int i = 0; i < 9; i++) container.setItem(i, createGuiItem(pane, " "));
        for (int i = (rows - 1) * 9; i < rows * 9; i++) container.setItem(i, createGuiItem(pane, " "));
        for (int i = 0; i < rows; i++) {
            container.setItem(i * 9, createGuiItem(pane, " "));
            container.setItem(i * 9 + 8, createGuiItem(pane, " "));
        }
    }

    private static void fillEmptySlots(ChestContainer container, int size, net.minecraft.item.Item paneItem) {
        for (int i = 0; i < size; i++) {
            if (container.getSlot(i).getItem().isEmpty()) {
                container.setItem(i, createGuiItem(paneItem, " "));
            }
        }
    }

    private static ItemStack createGuiItem(net.minecraft.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(new StringTextComponent(name));
        CompoundNBT nbt = stack.getOrCreateTag();
        nbt.putBoolean("RaidGuiItem", true);
        CompoundNBT display = nbt.contains("display") ? nbt.getCompound("display") : new CompoundNBT();
        ListNBT loreList = new ListNBT();
        for (String line : loreLines) loreList.add(StringNBT.valueOf("{\"text\":\"" + line + "\"}"));
        display.put("Lore", loreList);
        nbt.put("display", display);
        stack.setTag(nbt);
        return stack;
    }
}
