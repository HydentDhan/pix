package com.PixelmonRaid;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RaidAdminCommand {
   public static final Map<UUID, String> playerMenuState = new ConcurrentHashMap<>();
   public static final Map<UUID, Integer> editingItemIndex = new ConcurrentHashMap<>();
   public static final Map<UUID, Integer> purchasingItemIndex = new ConcurrentHashMap<>();
   public static final Map<UUID, Integer> playerShopPage = new ConcurrentHashMap<>();
   public static final Map<UUID, Integer> editingLootLevel = new ConcurrentHashMap<>();
   public static final int[] REWARD_SLOTS = new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32};

   private static Item getLogoItem() {
      try {
         String id = PixelmonRaidConfig.getInstance().getUiLogoItem();
         Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
         return item != null ? item : Items.NETHER_STAR;
      } catch (Exception var2) {
         return Items.NETHER_STAR;
      }
   }

   private static Item getBorderItem() {
      try {
         String id = PixelmonRaidConfig.getInstance().getUiBorderItem();
         Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
         return item != null ? item : Items.BLACK_STAINED_GLASS_PANE;
      } catch (Exception var2) {
         return Items.BLACK_STAINED_GLASS_PANE;
      }
   }

   private static String getName() {
      return PixelmonRaidConfig.getInstance().getUiServerName();
   }

   private static String getColor() {
      return PixelmonRaidConfig.getInstance().getUiThemeColor();
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("raidadmin").requires((source) -> {
         return source.hasPermission(2);
      }).then(Commands.literal("start").executes((ctx) -> {
         ServerLevel world = ctx.getSource().getLevel();
         RaidSession session = RaidSpawner.getSession(world);
         if (session != null) {
            session.startWaitingNow(world.getGameTime());
            ctx.getSource().sendSystemMessage(Component.literal("§aForce started raid sequence."));
         }
         return 1;
      })).then(Commands.literal("stop").executes((ctx) -> {
         ServerLevel world = ctx.getSource().getLevel();
         RaidSession session = RaidSpawner.getSession(world);
         if (session != null) {
            session.cleanup();
            ctx.getSource().sendSystemMessage(Component.literal("§cRaid stopped."));
         }
         return 1;
      })).then(Commands.literal("forcewin").executes((context) -> {
         RaidSession session = RaidSpawner.getSessionSafe(context.getSource().getLevel());
         if (session != null) {
            session.finishRaid(true, null);
            context.getSource().sendSystemMessage(Component.literal("§aRaid Force Won!"));
         } else {
            context.getSource().sendSystemMessage(Component.literal("No active battle to win."));
         }
         return 1;
      })).then(Commands.literal("setcenter").executes((ctx) -> {
         if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            return 0;
         } else {
            ServerPlayer p = (ServerPlayer)ctx.getSource().getEntity();
            RaidSession s = RaidSpawner.getSession(p.serverLevel());
            if (s != null) {
               s.setCenter(p.blockPosition());
               String pos = p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ();
               ctx.getSource().sendSystemMessage(Component.literal("§aRaid Center set to " + pos));
            }
            return 1;
         }
      })).then(Commands.literal("setwarp").executes((ctx) -> {
         if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            return 0;
         } else {
            ServerPlayer p = (ServerPlayer)ctx.getSource().getEntity();
            RaidSession s = RaidSpawner.getSession(p.serverLevel());
            if (s != null) {
               s.setPlayerSpawn(p.blockPosition());
               String pos = p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ();
               ctx.getSource().sendSystemMessage(Component.literal("§aRaid Player Warp set to " + pos));
            }
            return 1;
         }
      })).then(Commands.literal("reload").executes((ctx) -> {
         PixelmonRaidConfig.reload();
         ServerLevel world = ctx.getSource().getLevel();
         RaidSession session = RaidSpawner.getSessionSafe(world);
         if (session != null && session.getState() == RaidSession.State.IDLE) {
            session.forceResetTimer();
            ctx.getSource().sendSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgReloadTimerSynced()));
         } else {
            ctx.getSource().sendSystemMessage(Component.literal(PixelmonRaidConfig.getInstance().getMsgReload()));
         }
         return 1;
      })).then(Commands.literal("tokens").then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes((ctx) -> {
         ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
         int amt = IntegerArgumentType.getInteger(ctx, "amount");
         RaidSaveData.get((ServerLevel)target.level()).addTokens(target.getUUID(), amt);
         ctx.getSource().sendSystemMessage(Component.literal("§aGave " + amt + " tokens to " + target.getName().getString()));
         return 1;
      })))).then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes((ctx) -> {
         ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
         int amt = IntegerArgumentType.getInteger(ctx, "amount");
         boolean success = RaidSaveData.get((ServerLevel)target.level()).removeTokens(target.getUUID(), amt);
         if (success) {
            ctx.getSource().sendSystemMessage(Component.literal("§aTook " + amt + " tokens from " + target.getName().getString()));
         } else {
            ctx.getSource().sendSystemMessage(Component.literal("§cPlayer does not have enough tokens."));
         }
         return 1;
      }))))).then(Commands.literal("setholo").executes((ctx) -> {
         if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            return 0;
         } else {
            ServerPlayer player = (ServerPlayer)ctx.getSource().getEntity();
            PixelmonRaidConfig.getInstance().setHoloLocation(player.getX(), player.getY() + 2.0D, player.getZ(), player.level().dimension().location().toString());
            ctx.getSource().sendSystemMessage(Component.literal("§a§l✔ Holographic Leaderboard set to your location!"));
            return 1;
         }
      })).then(Commands.literal("toggleholo").executes((ctx) -> {
         boolean current = PixelmonRaidConfig.getInstance().isHologramEnabled();
         PixelmonRaidConfig.getInstance().setHologramEnabled(!current);
         String status = !current ? "§aENABLED" : "§cDISABLED";
         ctx.getSource().sendSystemMessage(Component.literal("§eHolographic Leaderboard is now " + status));
         return 1;
      })).then(Commands.literal("shop").requires((source) -> {
         return PixelmonRaidConfig.getInstance().isInternalShopEnabled();
      }).then(Commands.literal("add").then(Commands.argument("price", IntegerArgumentType.integer(1)).executes((ctx) -> {
         if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            return 0;
         } else {
            ServerPlayer player = (ServerPlayer)ctx.getSource().getEntity();
            int price = IntegerArgumentType.getInteger(ctx, "price");
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
               ctx.getSource().sendSystemMessage(Component.literal("§cYou must hold an item to add it!"));
               return 0;
            } else {
               PixelmonRaidConfig.ShopItem item = new PixelmonRaidConfig.ShopItem();
               item.itemID = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
               item.price = price;
               item.displayCount = held.getCount();
               item.nbt = GuiItemUtil.hasGuiTag(held) ? GuiItemUtil.getGuiTag(held).toString() : "";
               item.isCommand = false;
               PixelmonRaidConfig.getInstance().getRaidTokenShop().add(item);
               PixelmonRaidConfig.getInstance().save();
               ctx.getSource().sendSystemMessage(Component.literal("§aAdded " + item.displayCount + "x " + item.itemID + " to the shop for " + price + " tokens."));
               return 1;
            }
         }
      })))).executes((context) -> {
         if (!(context.getSource().getEntity() instanceof ServerPlayer)) {
            context.getSource().sendSystemMessage(Component.literal("Only a player can open the Raid Admin GUI!"));
            return 0;
         } else {
            return openHub((ServerPlayer)context.getSource().getEntity());
         }
      }));
   }

   public static void redrawCurrentMenu(ServerPlayer player) {
      String state = playerMenuState.get(player.getUUID());
      if (state != null && player.containerMenu instanceof ChestMenu chest) {
         boolean isRewardEditor = state.equals("killshot") || state.equals("participation") || state.matches("\\d+");

         for(int i = 0; i < chest.slots.size() - 36; ++i) {
            boolean skip = false;
            if (isRewardEditor) {
               for(int rs : REWARD_SLOTS) {
                  if (i == rs) skip = true;
               }
            }
            if (!skip) {
               chest.getSlot(i).set(ItemStack.EMPTY);
            }
         }

         if (state.equals("HUB")) {
            fillHub(chest, player);
         } else if (state.equals("REWARDS_HUB")) {
            fillRewardsHub(chest, player);
         } else if (state.equals("TOKEN_SHOP")) {
            fillTokenShop(chest, player);
         } else if (state.equals("SHOP_EDITOR")) {
            fillShopEditor(chest, player);
         } else if (state.equals("PRICE_EDITOR")) {
            fillPriceEditor(chest, player, editingItemIndex.getOrDefault(player.getUUID(), 0));
         } else if (state.equals("PURCHASE_UI")) {
            fillPurchasePanel(chest, player, purchasingItemIndex.getOrDefault(player.getUUID(), 0));
         } else if (isRewardEditor) {
            fillRewardEditor(chest, player, state);
         }

         chest.broadcastChanges();
      }
   }

   public static void fillHub(ChestMenu chest, ServerPlayer player) {
      ServerLevel world = (ServerLevel)player.level();
      RaidSession session = RaidSpawner.getSessionSafe(world);
      boolean autoEnabled = PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
      boolean isBattleActive = session != null && (session.getState() == RaidSession.State.IN_BATTLE || session.getState() == RaidSession.State.SUDDEN_DEATH);
      fillBorder(chest, 6, getBorderItem());
      setSlot(chest, 4, createGuiItem(getLogoItem(), getName(), "§7Admin Control Panel", "§7Version 6.0"));
      if (isBattleActive) {
         setSlot(chest, 19, createGuiItem(Items.BARRIER, "§7§lRaid In Progress", "§cCannot start new raid", "§cwhile one is active."));
      } else {
         setSlot(chest, 19, createGuiItem(Items.EMERALD_BLOCK, "§a§l▶ START RAID", "§7Force start a raid immediately.", " ", "§e▶ Click to Initialize"));
      }

      setSlot(chest, 21, createGuiItem(Items.DIAMOND_SWORD, "§b§l⚔ FORCE WIN", "§7Kill boss & give rewards.", " ", "§e▶ Click to Execute"));
      setSlot(chest, 23, createGuiItem(Items.REDSTONE_BLOCK, "§c§l✖ ABORT RAID", "§7Cancel current raid.", " ", "§c▶ Click to Stop"));
      ItemStack toggleItem = createGuiItem(Items.REPEATER, autoEnabled ? "§a§l✔ AUTO-RAIDS: ON" : "§c§l✖ AUTO-RAIDS: OFF", "§7Control whether raids spawn automatically.", " ", autoEnabled ? "§e▶ Click to PAUSE Raids" : "§e▶ Click to RESUME Raids");
      if (autoEnabled) {
         GuiItemUtil.getGuiTag(toggleItem).putBoolean("EnchantmentGlint", true);
      }
      setSlot(chest, 37, toggleItem);

      int diff = PixelmonRaidConfig.getInstance().getRaidDifficulty();
      ItemStack diffItem = createGuiItem(Items.GLOWSTONE, getColor() + "§l★ Difficulty: " + diff, "§7Current Level: §f" + diff + "/5", " ", "§e▶ Click to Cycle");
      GuiItemUtil.getGuiTag(diffItem).putBoolean("EnchantmentGlint", true);
      setSlot(chest, 25, diffItem);
      setSlot(chest, 39, createGuiItem(Items.CHEST, getColor() + "§l🎁 Loot Tables", "§7Edit rewards for specific Tiers.", " ", "§e▶ Click to Edit"));
      if (PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
         setSlot(chest, 41, createGuiItem(Items.GOLD_INGOT, getColor() + "§l💰 Shop Editor", "§7Edit shop prices/items.", " ", "§e▶ Click to Edit"));
      } else {
         setSlot(chest, 41, createGuiItem(Items.BARRIER, "§c§l✖ Shop Disabled", "§7The internal shop is currently", "§7disabled in the config.", " ", "§8(Using External Shop)"));
      }

      fillEmptySlots(chest, 54, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
   }

   public static int openHub(ServerPlayer player) {
      playerMenuState.put(player.getUUID(), "HUB");
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         fillHub(chest, player);
         return chest;
      }, Component.literal(getName())));
      return 1;
   }

   public static void fillRewardsHub(ChestMenu chest, ServerPlayer player) {
      fillBorder(chest, 6, Items.BLUE_STAINED_GLASS_PANE);
      int level = editingLootLevel.getOrDefault(player.getUUID(), 1);
      ItemStack cycleBtn = createGuiItem(Items.NETHER_STAR, "§b§l⭐ Editing Tier: Level " + level + " ⭐", "§7Click here to cycle (1-5)", "§7and edit rewards for other difficulties!");
      GuiItemUtil.getGuiTag(cycleBtn).putBoolean("EnchantmentGlint", true);
      setSlot(chest, 4, cycleBtn);

      PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(level);
      int rankCount = tier.getRankCount();
      int slot = 10;
      for(int i = 1; i <= rankCount && slot <= 44; ++i) {
         if ((slot + 1) % 9 == 0) {
            slot += 2;
         }
         ItemStack rankItem = createGuiItem(Items.GOLDEN_APPLE, "§6⭐ Winner " + i + " Rewards", "§7For Boss Tier: §eLevel " + level, " ", "§7Click to Edit");
         CompoundTag nbt = GuiItemUtil.getGuiTag(rankItem);
         nbt.putInt("RankIndex", i);
         GuiItemUtil.setGuiTag(rankItem, nbt);
         setSlot(chest, slot, rankItem);
         ++slot;
      }

      setSlot(chest, 46, createGuiItem(Items.LIME_DYE, "§a§l[+] ADD WINNER", "§7Add a new placement reward."));
      setSlot(chest, 52, createGuiItem(Items.RED_DYE, "§c§l[-] REMOVE WINNER", "§7Remove the highest placement."));
      setSlot(chest, 48, createGuiItem(Items.EXPERIENCE_BOTTLE, "§d🎁 Participation", "§7For Boss Tier: §eLevel " + level, " ", "§e▶ Click to Edit"));
      setSlot(chest, 50, createGuiItem(Items.SKELETON_SKULL, "§c☠ Killshot", "§7For Boss Tier: §eLevel " + level, " ", "§e▶ Click to Edit"));
      setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ RETURN", "§7Back to Dashboard"));
      fillEmptySlots(chest, 54, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
   }

   public static void openRewardsHub(ServerPlayer player) {
      playerMenuState.put(player.getUUID(), "REWARDS_HUB");
      editingLootLevel.putIfAbsent(player.getUUID(), 1);
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         fillRewardsHub(chest, player);
         return chest;
      }, Component.literal("§8§lLoot Table Editor")));
   }

   public static void fillRewardEditor(ChestMenu chest, ServerPlayer player, String rank) {
      Item bgItem = Items.GRAY_STAINED_GLASS_PANE;
      Item outlineItem = getBorderItem();

      for(int i = 0; i < 54; ++i) {
         boolean isRewardSlot = false;
         for(int s : REWARD_SLOTS) {
            if (i == s) isRewardSlot = true;
         }

         if (!isRewardSlot) {
            boolean isOutline = i == 3 || i == 4 || i == 5 || i == 11 || i == 15 || i == 20 || i == 24 || i == 29 || i == 33 || i == 39 || i == 40 || i == 41;
            setSlot(chest, i, createGuiItem(isOutline ? outlineItem : bgItem, " "));
         }
      }

      int level = editingLootLevel.getOrDefault(player.getUUID(), 1);
      String label = !rank.equals("participation") && !rank.equals("killshot") ? "WINNER " + rank : rank.toUpperCase();
      setSlot(chest, 4, createGuiItem(getLogoItem(), "§d§lEditing Tier " + level + ": §f" + label, "§7Place items inside the 3x3 frame below!"));
      setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ SAVE & RETURN", "§7Closes and Saves Items"));

      PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(level);
      List<String> rewards = null;
      if (rank.equals("participation")) {
         rewards = tier.participation.items;
      } else if (rank.equals("killshot")) {
         rewards = tier.killshot.items;
      } else {
         try {
            int idx = Integer.parseInt(rank);
            PixelmonRaidConfig.RankReward rw = tier.winners.get("Winner_" + idx);
            if (rw != null) {
               rewards = rw.items;
            }
         } catch (Exception var18) {}
      }

      if (rewards != null) {
         int idx = 0;
         for(String s : rewards) {
            try {
               String[] parts = s.split(" ", 3);
               Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0]));
               if (item != null) {
                  ItemStack stack = new ItemStack(item);
                  if (parts.length > 1) {
                     stack.setCount(Integer.parseInt(parts[1]));
                  }
                  if (parts.length > 2) {
                     try {
                        CompoundTag nbt = TagParser.parseTag(parts[2]);
                        setGuiTag(stack, nbt);
                     } catch (Exception var16) {}
                  }

                  if (idx < REWARD_SLOTS.length) {
                     setSlot(chest, REWARD_SLOTS[idx], stack);
                     ++idx;
                  }
               }
            } catch (Exception var17) {}
         }
      }
   }

   public static void openRewardEditor(ServerPlayer player, String rank) {
      playerMenuState.put(player.getUUID(), rank);
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         for(int slot : REWARD_SLOTS) {
            chest.getSlot(slot).set(ItemStack.EMPTY);
         }
         fillRewardEditor(chest, player, rank);
         return chest;
      }, Component.literal("§5Editing: §d" + rank.toUpperCase())));
   }

   public static void fillTokenShop(ChestMenu chest, ServerPlayer player) {
      fillBorder(chest, 6, getBorderItem());
      setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));
      int balance = RaidSaveData.get((ServerLevel)player.level()).getTokens(player.getUUID());
      ItemStack balItem = createGuiItem(Items.GOLD_NUGGET, "§6§lYour Account", "§e" + balance + " Tokens");
      GuiItemUtil.getGuiTag(balItem).putBoolean("EnchantmentGlint", true);
      setSlot(chest, 4, balItem);

      List<PixelmonRaidConfig.ShopItem> allItems = PixelmonRaidConfig.getInstance().getRaidTokenShop();
      int page = playerShopPage.getOrDefault(player.getUUID(), 0);
      int itemsPerPage = 28;
      int maxPages = (int)Math.ceil((double)allItems.size() / (double)itemsPerPage);
      if (maxPages == 0) maxPages = 1;
      if (page >= maxPages) page = maxPages - 1;
      if (page < 0) page = 0;

      playerShopPage.put(player.getUUID(), page);
      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
      int[] shopSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
      for(int i = 0; i < endIndex - startIndex; ++i) {
         int trueIndex = startIndex + i;
         PixelmonRaidConfig.ShopItem entry = allItems.get(trueIndex);
         int slot = shopSlots[i];

         try {
            ResourceLocation res = ResourceLocation.parse(entry.itemID);
            Item item = BuiltInRegistries.ITEM.get(res);
            if (item != null) {
               ItemStack display = new ItemStack(item, entry.displayCount);
               CompoundTag nbt = GuiItemUtil.getGuiTag(display);
               if (entry.nbt != null && !entry.nbt.isEmpty()) {
                  try {
                     nbt.merge(TagParser.parseTag(entry.nbt));
                     GuiItemUtil.setGuiTag(display, nbt);
                  } catch (Exception var21) {}
               }

               GuiItemUtil.setStackName(display,Component.literal("§b" + display.getHoverName().getString()));
               CompoundTag d = nbt.contains("display") ? nbt.getCompound("display") : new CompoundTag();
               ListTag l = new ListTag();
               l.add(StringTag.valueOf("{\"text\":\"§8§m------------------\"}"));
               if (entry.isCommand) {
                  l.add(StringTag.valueOf("{\"text\":\"§d✨ Command Reward ✨\"}"));
               }
               l.add(StringTag.valueOf("{\"text\":\"§7Price: §6" + entry.price + " Tokens\"}"));
               l.add(StringTag.valueOf("{\"text\":\" \"}"));
               l.add(StringTag.valueOf("{\"text\":\"§e▶ Click to Purchase\"}"));
               l.add(StringTag.valueOf("{\"text\":\"§8§m------------------\"}"));
               d.put("Lore", l);
               nbt.put("display", d);
               nbt.putBoolean("RaidGuiItem", true);
               GuiItemUtil.setGuiTag(display, nbt);
               GuiItemUtil.getGuiTag(display).putInt("ShopIndex", trueIndex);
               setSlot(chest, slot, display);
            }
         } catch (Exception var22) {}
      }

      if (page > 0) {
         setSlot(chest, 48, createGuiItem(Items.ARROW, "§e§l◀ PREVIOUS PAGE", "§7Page " + page));
      }
      if (page < maxPages - 1) {
         setSlot(chest, 50, createGuiItem(Items.ARROW, "§e§lNEXT PAGE ▶", "§7Page " + (page + 2)));
      }

      setSlot(chest, 49, createGuiItem(Items.BARRIER, "§c§l✖ CLOSE SHOP", "§7Exit the menu"));
      fillEmptySlots(chest, 54, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
   }

   public static void openTokenShop(ServerPlayer player) {
      if (!PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
         player.sendSystemMessage(Component.literal("§c❌ The internal Raid Shop is permanently disabled. Please use /shop instead!"));
      } else {
         playerMenuState.put(player.getUUID(), "TOKEN_SHOP");
         playerShopPage.put(player.getUUID(), 0);
         player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
            ChestMenu chest = ChestMenu.sixRows(id, playerInv);
            fillTokenShop(chest, player);
            return chest;
         }, Component.literal(getColor() + "§lToken Shop")));
      }
   }

   public static void fillPurchasePanel(ChestMenu chest, ServerPlayer player, int itemIndex) {
      int qty = RaidAdminUIListener.shopQuantities.getOrDefault(player.getUUID(), 1);
      fillBorder(chest, 6, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
      setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));
      List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
      if (itemIndex >= items.size()) {
         openTokenShop(player);
      } else {
         PixelmonRaidConfig.ShopItem entry = items.get(itemIndex);
         ResourceLocation res = ResourceLocation.parse(entry.itemID);
         int totalCost = entry.price * qty;
         int totalItems = entry.displayCount * qty;
         int balance = RaidSaveData.get((ServerLevel)player.level()).getTokens(player.getUUID());
         boolean canAfford = balance >= totalCost;
         setSlot(chest, 4, createGuiItem(Items.PAPER, "§e§lQuantity Selected", "§fCurrent: §b" + qty, " ", "§7Use the +/- buttons below", "§7to change amount."));
         ItemStack product = new ItemStack(BuiltInRegistries.ITEM.get(res), Math.min(64, totalItems));
         CompoundTag nbt = GuiItemUtil.getGuiTag(product);
         if (entry.nbt != null && !entry.nbt.isEmpty()) {
            try {
               nbt.merge(TagParser.parseTag(entry.nbt));
               GuiItemUtil.setGuiTag(product, nbt);
            } catch (Exception var15) {}
         }

         GuiItemUtil.setStackName(product,Component.literal("§6§l" + totalItems + "x " + product.getHoverName().getString()));
         CompoundTag disp = nbt.contains("display") ? nbt.getCompound("display") : new CompoundTag();
         ListTag lore = new ListTag();
         if (entry.isCommand) {
            lore.add(StringTag.valueOf("{\"text\":\"§d✨ Command Reward ✨\"}"));
         }
         lore.add(StringTag.valueOf("{\"text\":\"§7Total Cost: §e" + totalCost + " Tokens\"}"));
         lore.add(StringTag.valueOf("{\"text\":\" \"}"));
         lore.add(StringTag.valueOf("{\"text\":\"" + (canAfford ? "§a✔ You can afford this" : "§c✖ Insufficient Tokens") + "\"}"));
         disp.put("Lore", lore);
         nbt.put("display", disp);
         nbt.putBoolean("RaidGuiItem", true);
         GuiItemUtil.setGuiTag(product, nbt);

         setSlot(chest, 13, product);
         setSlot(chest, 10, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-64", "§7Decrease"));
         setSlot(chest, 11, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-10", "§7Decrease"));
         setSlot(chest, 12, createGuiItem(Items.RED_STAINED_GLASS_PANE, "§c-1", "§7Decrease"));
         setSlot(chest, 14, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+1", "§7Increase"));
         setSlot(chest, 15, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+10", "§7Increase"));
         setSlot(chest, 16, createGuiItem(Items.LIME_STAINED_GLASS_PANE, "§a+64", "§7Increase"));
         setSlot(chest, 22, createGuiItem(Items.DIAMOND_BLOCK, "§6§lMAX", "§7Buy max affordable", "§7(Up to 64)"));
         if (canAfford) {
            setSlot(chest, 31, createGuiItem(Items.LIME_TERRACOTTA, "§a§l✔ CONFIRM PURCHASE", "§7Cost: §e" + totalCost + " Tokens", " ", "§e▶ Click to Buy"));
         } else {
            setSlot(chest, 31, createGuiItem(Items.RED_TERRACOTTA, "§c§l✖ CANNOT AFFORD", "§7You need " + (totalCost - balance) + " more tokens."));
         }

         setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ CANCEL", "§7Return to Shop"));
         fillEmptySlots(chest, 54, Items.GRAY_STAINED_GLASS_PANE);
      }
   }

   public static void openPurchasePanel(ServerPlayer player, int itemIndex) {
      playerMenuState.put(player.getUUID(), "PURCHASE_UI");
      purchasingItemIndex.put(player.getUUID(), itemIndex);
      if (!RaidAdminUIListener.shopQuantities.containsKey(player.getUUID())) {
         RaidAdminUIListener.shopQuantities.put(player.getUUID(), 1);
      }
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         fillPurchasePanel(chest, player, itemIndex);
         return chest;
      }, Component.literal("§8§lConfirm Purchase")));
   }

   public static void fillShopEditor(ChestMenu chest, ServerPlayer player) {
      for(int i = 0; i < 9; ++i) {
         setSlot(chest, i, createGuiItem(Items.GRAY_STAINED_GLASS_PANE, " "));
      }
      for(int i = 45; i < 54; ++i) {
         setSlot(chest, i, createGuiItem(Items.GRAY_STAINED_GLASS_PANE, " "));
      }

      setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));
      List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
      int page = playerShopPage.getOrDefault(player.getUUID(), 0);
      int itemsPerPage = 36;
      int maxPages = (int)Math.ceil((double)items.size() / (double)itemsPerPage);
      if (maxPages == 0) maxPages = 1;
      if (page >= maxPages) page = maxPages - 1;
      if (page < 0) page = 0;
      playerShopPage.put(player.getUUID(), page);
      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, items.size());
      int slot = 9;
      for(int i = startIndex; i < endIndex; ++i) {
         PixelmonRaidConfig.ShopItem entry = items.get(i);
         try {
            ResourceLocation res = ResourceLocation.parse(entry.itemID);
            Item item = BuiltInRegistries.ITEM.get(res);
            if (item != null) {
               ItemStack display = new ItemStack(item, entry.displayCount);
               CompoundTag nbt = GuiItemUtil.getGuiTag(display);
               if (entry.nbt != null && !entry.nbt.isEmpty()) {
                  try {
                     nbt.merge(TagParser.parseTag(entry.nbt));
                     GuiItemUtil.setGuiTag(display, nbt);
                  } catch (Exception var16) {}
               }
               GuiItemUtil.setStackName(display,Component.literal("§b" + display.getHoverName().getString()));
               nbt.putInt("ShopIndex", i);
               nbt.putBoolean("RaidGuiItem", true);
               GuiItemUtil.setGuiTag(display, nbt);
               setSlot(chest, slot, display);
               ++slot;
            }
         } catch (Exception var17) {}
      }

      if (page > 0) {
         setSlot(chest, 48, createGuiItem(Items.ARROW, "§e§l◀ PREVIOUS PAGE", "§7Page " + page));
      }
      if (page < maxPages - 1) {
         setSlot(chest, 50, createGuiItem(Items.ARROW, "§e§lNEXT PAGE ▶", "§7Page " + (page + 2)));
      }

      setSlot(chest, 49, createGuiItem(Items.RED_BED, "§c§l↩ RETURN", "§7Back to Dashboard"));
      fillEmptySlots(chest, 54, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
   }

   public static void openShopEditor(ServerPlayer player) {
      playerMenuState.put(player.getUUID(), "SHOP_EDITOR");
      playerShopPage.put(player.getUUID(), 0);
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         fillShopEditor(chest, player);
         return chest;
      }, Component.literal("§8§lShop Editor")));
   }

   public static void fillPriceEditor(ChestMenu chest, ServerPlayer player, int index) {
      fillBorder(chest, 6, Items.GRAY_STAINED_GLASS_PANE);
      setSlot(chest, 0, createGuiItem(getLogoItem(), getName()));
      List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
      if (index >= 0 && index < items.size()) {
         PixelmonRaidConfig.ShopItem entry = items.get(index);
         ResourceLocation res = ResourceLocation.parse(entry.itemID);
         ItemStack display = new ItemStack(BuiltInRegistries.ITEM.get(res), entry.displayCount);
         CompoundTag nbt = GuiItemUtil.getGuiTag(display);
         if (entry.nbt != null && !entry.nbt.isEmpty()) {
            try {
               nbt.merge(TagParser.parseTag(entry.nbt));
               GuiItemUtil.setGuiTag(display, nbt);
            } catch (Exception var10) {}
         }

         GuiItemUtil.setStackName(display,Component.literal("§6§lCurrent Settings"));
         CompoundTag d = new CompoundTag();
         ListTag l = new ListTag();
         l.add(StringTag.valueOf("{\"text\":\"§7Price: §e" + entry.price + "\"}"));
         l.add(StringTag.valueOf("{\"text\":\"§7Stock: §f" + entry.displayCount + "\"}"));
         d.put("Lore", l);
         nbt.put("display", d);
         nbt.putBoolean("RaidGuiItem", true);
         GuiItemUtil.setGuiTag(display, nbt);
         setSlot(chest, 13, display);
         setSlot(chest, 10, createGuiItem(Items.RED_DYE, "§cPrice: -100", "§7Decrease cost"));
         setSlot(chest, 11, createGuiItem(Items.RED_DYE, "§cPrice: -10", "§7Decrease cost"));
         setSlot(chest, 12, createGuiItem(Items.RED_DYE, "§cPrice: -1", "§7Decrease cost"));
         setSlot(chest, 14, createGuiItem(Items.LIME_DYE, "§aPrice: +1", "§7Increase cost"));
         setSlot(chest, 15, createGuiItem(Items.LIME_DYE, "§aPrice: +10", "§7Increase cost"));
         setSlot(chest, 16, createGuiItem(Items.GOLD_INGOT, "§aPrice: +100", "§7Increase cost"));
         setSlot(chest, 53, createGuiItem(Items.BARRIER, "§4§lDELETE ITEM", "§7Remove from Shop", "§c⚠ Cannot be undone"));
      }

      setSlot(chest, 49, createGuiItem(Items.ARROW, "§c§l↩ BACK", "§7Back to Shop Editor"));
      fillEmptySlots(chest, 54, Items.LIGHT_GRAY_STAINED_GLASS_PANE);
   }

   public static void openItemPriceEditor(ServerPlayer player, int index) {
      playerMenuState.put(player.getUUID(), "PRICE_EDITOR");
      editingItemIndex.put(player.getUUID(), index);
      player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> {
         ChestMenu chest = ChestMenu.sixRows(id, playerInv);
         fillPriceEditor(chest, player, index);
         return chest;
      }, Component.literal("§8§lEdit Item")));
   }

   public static void setSlot(ChestMenu container, int slot, ItemStack stack) {
      if (slot >= 0 && slot < container.slots.size() - 36) {
         container.getSlot(slot).set(stack);
      }
   }

   private static void fillBorder(ChestMenu container, int rows, Item pane) {
      for(int i = 0; i < 9; ++i) {
         setSlot(container, i, createGuiItem(pane, " "));
      }
      for(int i = (rows - 1) * 9; i < rows * 9; ++i) {
         setSlot(container, i, createGuiItem(pane, " "));
      }
      for(int i = 0; i < rows; ++i) {
         setSlot(container, i * 9, createGuiItem(pane, " "));
         setSlot(container, i * 9 + 8, createGuiItem(pane, " "));
      }
   }

   private static void fillEmptySlots(ChestMenu container, int size, Item paneItem) {
      for(int i = 0; i < size; ++i) {
         if (container.getSlot(i).getItem().isEmpty()) {
            setSlot(container, i, createGuiItem(paneItem, " "));
         }
      }
   }

   public static ItemStack createGuiItem(Item item, String name, String... loreLines) {
      ItemStack stack = new ItemStack(item);
      setStackName(stack,Component.literal(name));
      CompoundTag nbt = GuiItemUtil.getGuiTag(stack);
      nbt.putBoolean("RaidGuiItem", true);
      CompoundTag display = nbt.contains("display") ? nbt.getCompound("display") : new CompoundTag();
      ListTag loreList = new ListTag();
      for(String line : loreLines) {
         if (line.isEmpty()) {
            loreList.add(StringTag.valueOf("{\"text\":\" \"}"));
         } else {
            loreList.add(StringTag.valueOf("{\"text\":\"" + line + "\"}"));
         }
      }

      display.put("Lore", loreList);
      nbt.put("display", display);
      setGuiTag(stack, nbt);
      return stack;
   }
   public static CompoundTag getGuiTag(ItemStack stack) {
      return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
   }

   public static void setGuiTag(ItemStack stack, CompoundTag tag) {
      net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);
   }

   public static void setStackName(ItemStack stack, Component name) {
      stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
   }

   public static boolean hasGuiTag(ItemStack stack) {
      return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
   }
}