package com.example.PixelmonRaid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

@EventBusSubscriber
public class RaidAdminUIListener {
   public static final Map<UUID, Integer> shopQuantities = new HashMap<>();
   private static final Set<UUID> IS_TRANSITIONING = ConcurrentHashMap.newKeySet();

   @SubscribeEvent
   public static void onItemToss(ItemTossEvent event) {
      if (event.getEntity() != null) {
         ItemStack stack = event.getEntity().getItem();
         if (isGuiItem(stack)) {
            event.getEntity().discard();
            if (event instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onContainerClose(PlayerContainerEvent.Close event) {
      UUID id = event.getEntity().getUUID();
      if (!IS_TRANSITIONING.contains(id)) {
         String state = RaidAdminCommand.playerMenuState.get(id);

         // Auto-save rewards when closing the editor
         if (state != null && (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) && event.getEntity() instanceof ServerPlayer sp) {
            saveRewardsFromEditor(sp, null, state);
         }

         // Aggressively prevent memory leaks by clearing all maps!
         RaidAdminCommand.playerMenuState.remove(id);
         RaidAdminCommand.editingItemIndex.remove(id);
         RaidAdminCommand.purchasingItemIndex.remove(id);
         RaidAdminCommand.playerShopPage.remove(id);
         RaidAdminCommand.editingLootLevel.remove(id);
         shopQuantities.remove(id);
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent.Pre event) {
      if (!event.getEntity().level().isClientSide) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         boolean isMenuOpen = RaidAdminCommand.playerMenuState.containsKey(player.getUUID());
         String state = isMenuOpen ? RaidAdminCommand.playerMenuState.get(player.getUUID()) : null;
         boolean dirty = false;
         boolean handledClick = false;
         boolean requiresRedraw = false;

         // 1. CURSOR THEFT PROTECTION
         ItemStack cursorStack = player.containerMenu.getCarried();
         if (!cursorStack.isEmpty() && isGuiItem(cursorStack)) {
            String rawName = cursorStack.getHoverName().getString();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.connection.send(new ClientboundContainerSetSlotPacket(-1, 0, -1, ItemStack.EMPTY));
            dirty = true;
            requiresRedraw = true;
            if (isMenuOpen && !handledClick) {
               handledClick = handleButtonClick(player, rawName, state, cursorStack);
            }
         }

         // 2. INVENTORY SWEEP PROTECTION
         for(int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.isEmpty() && isGuiItem(st)) {
               String rawName = st.getHoverName().getString();
               player.getInventory().removeItemNoUpdate(i);
               dirty = true;
               requiresRedraw = true;
               if (isMenuOpen && !handledClick) {
                  handledClick = handleButtonClick(player, rawName, state, st);
               }
            }
         }

         // 3. LOOT EDITOR LOCKED SLOT PROTECTION
         if (isMenuOpen) {
            boolean isRewardEditor = state.equals("killshot") || state.equals("participation") || state.matches("\\d+");
            if (isRewardEditor && player.containerMenu != null && !IS_TRANSITIONING.contains(player.getUUID())) {
               boolean backgroundTampered = false;
               for(int i = 0; i < 54 && i < player.containerMenu.slots.size(); ++i) {
                  if (i != 49) { // Ignore the save/back button slot
                     boolean isRewardSlot = false;
                     for(int slot : RaidAdminCommand.REWARD_SLOTS) {
                        if (i == slot) {
                           isRewardSlot = true;
                           break;
                        }
                     }

                     if (!isRewardSlot) {
                        Slot s = player.containerMenu.getSlot(i);
                        if (s != null) {
                           if (s.hasItem() && !isGuiItem(s.getItem())) {
                              // Player tried to put a real item in the glass pane border!
                              ItemStack stolenItem = s.getItem().copy();
                              s.set(ItemStack.EMPTY);
                              if (!player.getInventory().add(stolenItem)) {
                                 player.drop(stolenItem, false);
                              }
                              backgroundTampered = true;
                           } else if (!s.hasItem()) {
                              backgroundTampered = true;
                           }
                        }
                     }
                  }
               }

               if (backgroundTampered) {
                  // Play an error sound to let the admin know they missed the 3x3 safe zone
                  player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
                  requiresRedraw = true;
               }
            }
         }

         if (dirty) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null) {
               player.containerMenu.broadcastChanges();
            }
         }

         if (isMenuOpen && requiresRedraw && !handledClick && !IS_TRANSITIONING.contains(player.getUUID())) {
            RaidAdminCommand.redrawCurrentMenu(player);
         }
      }
   }

   private static boolean isGuiItem(ItemStack stack) {
      if (stack.isEmpty()) return false;
      return GuiItemUtil.hasGuiTag(stack) && GuiItemUtil.getGuiTag(stack).getBoolean("RaidGuiItem");
   }

   private static boolean handleButtonClick(ServerPlayer player, String rawName, String state, ItemStack stack) {
      String cleanName = rawName.replaceAll("(?i)§[0-9a-fk-or]", "");
      String lowName = cleanName.toLowerCase();
      ServerLevel world = (ServerLevel)player.level();
      RaidSession session = RaidSpawner.getSessionSafe(world);
      int idx;

      if (lowName.contains("next page")) {
         idx = RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
         RaidAdminCommand.playerShopPage.put(player.getUUID(), idx + 1);
         player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
         RaidAdminCommand.redrawCurrentMenu(player);
         return true;
      } else if (!lowName.contains("prev page") && !lowName.contains("previous page")) {
         if (lowName.contains("close shop")) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            player.closeContainer();
            return true;
         } else if (!lowName.contains("return") && !lowName.contains("back") && !lowName.contains("cancel") && !lowName.contains("exit") && !lowName.contains("save & return")) {
            if (state.equals("REWARDS_HUB")) {
               if (lowName.contains("editing tier")) {
                  idx = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  idx = idx >= 5 ? 1 : idx + 1;
                  RaidAdminCommand.editingLootLevel.put(player.getUUID(), idx);
                  player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (lowName.contains("add winner")) {
                  idx = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  PixelmonRaidConfig.getInstance().getTierRewards(idx).addRank();
                  PixelmonRaidConfig.getInstance().save();
                  player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (lowName.contains("remove winner")) {
                  idx = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  PixelmonRaidConfig.getInstance().getTierRewards(idx).removeLastRank();
                  PixelmonRaidConfig.getInstance().save();
                  player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (GuiItemUtil.hasGuiTag(stack) && GuiItemUtil.getGuiTag(stack).contains("RankIndex")) {
                  idx = GuiItemUtil.getGuiTag(stack).getInt("RankIndex");
                  player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                  switchMenu(player, String.valueOf(idx));
                  return true;
               }

               if (lowName.contains("killshot")) {
                  switchMenu(player, "killshot");
                  return true;
               }

               if (lowName.contains("participation")) {
                  switchMenu(player, "participation");
                  return true;
               }
            }

            if (!lowName.contains("start raid") && !lowName.contains("begin raid")) {
               if (!lowName.contains("force win") && !lowName.contains("complete")) {
                  if (!lowName.contains("abort") && !lowName.contains("stop raid") && !lowName.contains("end raid")) {
                     if (!lowName.contains("auto-raids") && !lowName.contains("auto-spawn")) {
                        if (state.equals("PRICE_EDITOR")) {
                           idx = RaidAdminCommand.editingItemIndex.getOrDefault(player.getUUID(), -1);
                           List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
                           if (idx < 0 || idx >= items.size()) {
                              switchMenu(player, "SHOP_EDITOR");
                              return true;
                           }

                           PixelmonRaidConfig.ShopItem entry = items.get(idx);
                           boolean changed = false;
                           if (cleanName.contains("+100")) {
                              entry.price += 100;
                              changed = true;
                           } else if (cleanName.contains("+10")) {
                              entry.price += 10;
                              changed = true;
                           } else if (cleanName.contains("+1")) {
                              ++entry.price;
                              changed = true;
                           } else if (cleanName.contains("-100")) {
                              entry.price = Math.max(1, entry.price - 100);
                              changed = true;
                           } else if (cleanName.contains("-10")) {
                              entry.price = Math.max(1, entry.price - 10);
                              changed = true;
                           } else if (cleanName.contains("-1")) {
                              entry.price = Math.max(1, entry.price - 1);
                              changed = true;
                           } else if (cleanName.contains("DELETE ITEM")) {
                              items.remove(idx);
                              PixelmonRaidConfig.getInstance().save();
                              player.playSound(SoundEvents.ANVIL_DESTROY, 1.0F, 1.0F);
                              switchMenu(player, "SHOP_EDITOR");
                              return true;
                           }

                           if (changed) {
                              PixelmonRaidConfig.getInstance().save();
                              player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 2.0F);
                              RaidAdminCommand.redrawCurrentMenu(player);
                              return true;
                           }
                        }

                        if (cleanName.contains("Difficulty:")) {
                           idx = PixelmonRaidConfig.getInstance().getRaidDifficulty() + 1;
                           if (idx > 5) {
                              idx = 1;
                           }

                           PixelmonRaidConfig.getInstance().setRaidDifficulty(idx);
                           player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.5F);
                           RaidAdminCommand.redrawCurrentMenu(player);
                           return true;
                        } else if ((state.equals("SHOP_EDITOR") || state.equals("TOKEN_SHOP")) && GuiItemUtil.hasGuiTag(stack) && GuiItemUtil.getGuiTag(stack).contains("ShopIndex")) {
                           idx = GuiItemUtil.getGuiTag(stack).getInt("ShopIndex");
                           player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                           if (state.equals("SHOP_EDITOR")) {
                              RaidAdminCommand.editingItemIndex.put(player.getUUID(), idx);
                              switchMenu(player, "PRICE_EDITOR");
                           } else {
                              shopQuantities.put(player.getUUID(), 1);
                              RaidAdminCommand.purchasingItemIndex.put(player.getUUID(), idx);
                              switchMenu(player, "PURCHASE_UI");
                           }
                           return true;
                        } else if (!lowName.contains("shop editor") && !lowName.contains("edit shop") && !lowName.contains("shop disabled")) {
                           if (!lowName.contains("loot tables") && !lowName.contains("rewards")) {
                              if (state.equals("PURCHASE_UI") && RaidAdminCommand.purchasingItemIndex.containsKey(player.getUUID())) {
                                 idx = RaidAdminCommand.purchasingItemIndex.get(player.getUUID());
                                 int currentQty = shopQuantities.getOrDefault(player.getUUID(), 1);
                                 boolean update = false;
                                 List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
                                 if (idx < items.size()) {
                                    PixelmonRaidConfig.ShopItem entry = items.get(idx);
                                    int baseCost, baseCount, totalCost;

                                    if (cleanName.contains("MAX")) {
                                       baseCost = entry.price;
                                       baseCount = RaidSaveData.get(world).getTokens(player.getUUID());
                                       if (baseCost > 0) {
                                          totalCost = baseCount / baseCost;
                                          currentQty = Math.max(1, Math.min(64, totalCost));
                                          update = true;
                                       }
                                    } else if (cleanName.contains("+64")) {
                                       currentQty += 64;
                                       update = true;
                                    } else if (cleanName.contains("+10")) {
                                       currentQty += 10;
                                       update = true;
                                    } else if (cleanName.contains("+1")) {
                                       ++currentQty;
                                       update = true;
                                    } else if (cleanName.contains("-64")) {
                                       currentQty = Math.max(1, currentQty - 64);
                                       update = true;
                                    } else if (cleanName.contains("-10")) {
                                       currentQty = Math.max(1, currentQty - 10);
                                       update = true;
                                    } else if (cleanName.contains("-1")) {
                                       currentQty = Math.max(1, currentQty - 1);
                                       update = true;
                                    }

                                    if (update) {
                                       shopQuantities.put(player.getUUID(), currentQty);
                                       player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.5F);
                                       RaidAdminCommand.redrawCurrentMenu(player);
                                       return true;
                                    }

                                    if (cleanName.contains("CONFIRM PURCHASE")) {
                                       baseCost = entry.price;
                                       baseCount = entry.displayCount;
                                       totalCost = baseCost * currentQty;
                                       int totalItems = baseCount * currentQty;
                                       int bal = RaidSaveData.get(world).getTokens(player.getUUID());

                                       if (bal >= totalCost) {
                                          RaidSaveData.get(world).removeTokens(player.getUUID(), totalCost);
                                          if (entry.isCommand && entry.commands != null && !entry.commands.isEmpty()) {
                                             for(int i = 0; i < currentQty; ++i) {
                                                for(String cmdTemplate : entry.commands) {
                                                   String cmd = cmdTemplate.replace("%player%", player.getGameProfile().getName());
                                                   try {
                                                      player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), cmd);
                                                   } catch (Exception var24) {}
                                                }
                                             }
                                          } else {
                                             ResourceLocation res = ResourceLocation.parse(entry.itemID);
                                             ItemStack give = new ItemStack(BuiltInRegistries.ITEM.get(res), totalItems);
                                             try {
                                                if (entry.nbt != null && !entry.nbt.isEmpty()) {
                                                   GuiItemUtil.setGuiTag(give, net.minecraft.nbt.TagParser.parseTag(entry.nbt));
                                                }
                                             } catch (Exception var23) {}

                                             if (!player.getInventory().add(give)) {
                                                player.drop(give, false);
                                             }
                                          }

                                          player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
                                          player.sendSystemMessage(Component.literal("§aSuccessful Purchase!"));
                                          switchMenu(player, "TOKEN_SHOP");
                                       } else {
                                          player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.5F);
                                       }
                                       return true;
                                    }
                                 }
                              }
                              return false;
                           } else {
                              switchMenu(player, "REWARDS_HUB");
                              return true;
                           }
                        } else if (!PixelmonRaidConfig.getInstance().isInternalShopEnabled()) {
                           player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                           player.sendSystemMessage(Component.literal("§c❌ Shop Editor is locked! You must enable the internal shop in the JSON file first."));
                           return true;
                        } else {
                           switchMenu(player, "SHOP_EDITOR");
                           return true;
                        }
                     } else {
                        boolean newState = !PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
                        PixelmonRaidConfig.getInstance().setAutoRaidEnabled(newState);
                        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                        if (newState) {
                           player.sendSystemMessage(Component.literal("§a[Raid] Auto-Spawn Enabled!"));
                        } else {
                           player.sendSystemMessage(Component.literal("§c[Raid] Auto-Spawn Paused."));
                        }

                        RaidAdminCommand.redrawCurrentMenu(player);
                        return true;
                     }
                  } else {
                     if (session != null) {
                        session.forceResetTimer();
                     }
                     RaidAdminCommand.redrawCurrentMenu(player);
                     return true;
                  }
               } else {
                  if (session != null) {
                     session.finishRaid(true, (UUID)null);
                  }
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }
            } else {
               if (session != null) {
                  session.startBattleNow();
               }
               RaidAdminCommand.redrawCurrentMenu(player);
               return true;
            }
         } else {
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
               saveRewardsFromEditor(player, null, state);
            }

            if (state.equals("PRICE_EDITOR")) {
               switchMenu(player, "SHOP_EDITOR");
            } else if (state.equals("PURCHASE_UI")) {
               switchMenu(player, "TOKEN_SHOP");
            } else if (state.equals("REWARDS_HUB")) {
               switchMenu(player, "HUB");
            } else if (!state.matches("\\d+") && !state.equals("killshot") && !state.equals("participation")) {
               switchMenu(player, "HUB");
            } else {
               switchMenu(player, "REWARDS_HUB");
            }
            return true;
         }
      } else {
         idx = RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
         RaidAdminCommand.playerShopPage.put(player.getUUID(), Math.max(0, idx - 1));
         player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
         RaidAdminCommand.redrawCurrentMenu(player);
         return true;
      }
   }

   private static void switchMenu(ServerPlayer player, String newState) {
      IS_TRANSITIONING.add(player.getUUID());
      try {
         boolean isEnteringEditor = newState.equals("killshot") || newState.equals("participation") || newState.matches("\\d+");
         if (isEnteringEditor && player.containerMenu instanceof ChestMenu chest) {
            for(int slot : RaidAdminCommand.REWARD_SLOTS) {
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

   private static void saveRewardsFromEditor(ServerPlayer player, Object ignored, String rankId) {
      List<String> serializedItems = new ArrayList<>();
      for(int slot : RaidAdminCommand.REWARD_SLOTS) {
         ItemStack stack = player.containerMenu.getSlot(slot).getItem();
         if (stack != null && !stack.isEmpty() && stack.getItem() != Items.AIR && (!GuiItemUtil.hasGuiTag(stack) || !GuiItemUtil.getGuiTag(stack).getBoolean("RaidGuiItem"))) {
            String entry = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + " " + stack.getCount() + (GuiItemUtil.hasGuiTag(stack) ? " " + GuiItemUtil.getGuiTag(stack).toString() : "");
            serializedItems.add(entry);
         }
      }

      int lvl = RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
      PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(lvl);
      if (rankId.equalsIgnoreCase("killshot")) {
         tier.killshot.items = serializedItems;
      } else if (rankId.equalsIgnoreCase("participation")) {
         tier.participation.items = serializedItems;
      } else {
         try {
            int r = Integer.parseInt(rankId);
            if (!tier.winners.containsKey("Winner_" + r)) {
               tier.winners.put("Winner_" + r, new PixelmonRaidConfig.RankReward());
            }

            tier.winners.get("Winner_" + r).items = serializedItems;
         } catch (NumberFormatException var10) {}
      }

      PixelmonRaidConfig.getInstance().save(); // Explicitly saving changes to file!
      player.sendSystemMessage(Component.literal("§a[Raid] Saved physical items for Tier " + lvl + " - " + rankId + "!"));
   }
}