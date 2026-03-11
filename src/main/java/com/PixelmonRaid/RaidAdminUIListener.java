package com.example.PixelmonRaid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.play.server.SSetSlotPacket;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent.Close;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
public class RaidAdminUIListener {
   public static final Map<UUID, Integer> shopQuantities = new HashMap();
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
   public static void onContainerClose(Close event) {
      UUID id = event.getPlayer().getUUID();
      if (!IS_TRANSITIONING.contains(id)) {
         String state = (String)RaidAdminCommand.playerMenuState.get(id);
         if (state != null && (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) && event.getPlayer() instanceof ServerPlayerEntity) {
            saveRewardsFromEditor((ServerPlayerEntity)event.getPlayer(), (Object)null, state);
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
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.START && !event.player.level.isClientSide) {
         ServerPlayerEntity player = (ServerPlayerEntity)event.player;
         boolean isMenuOpen = RaidAdminCommand.playerMenuState.containsKey(player.getUUID());
         String state = isMenuOpen ? (String)RaidAdminCommand.playerMenuState.get(player.getUUID()) : null;
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

         for(int i = 0; i < player.inventory.getContainerSize(); ++i) {
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
            boolean isRewardEditor = state.equals("killshot") || state.equals("participation") || state.matches("\\d+");
            if (isRewardEditor && player.containerMenu != null && !IS_TRANSITIONING.contains(player.getUUID())) {
               boolean backgroundTampered = false;
               for(int i = 0; i < 54 && i < player.containerMenu.slots.size(); ++i) {
                  if (i != 49) {
                     boolean isRewardSlot = false;
                     int[] var12 = RaidAdminCommand.REWARD_SLOTS;
                     int var13 = var12.length;

                     for(int var14 = 0; var14 < var13; ++var14) {
                        int slot = var12[var14];
                        if (i == slot) {
                           isRewardSlot = true;
                           break;
                        }
                     }

                     if (!isRewardSlot) {
                        Slot s = player.containerMenu.getSlot(i);
                        if (s != null) {
                           if (s.hasItem() && !isGuiItem(s.getItem())) {
                              ItemStack stolenItem = s.getItem().copy();
                              s.set(ItemStack.EMPTY);
                              if (!player.inventory.add(stolenItem)) {
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
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.hasTag() && stack.getTag().getBoolean("RaidGuiItem");
      }
   }

   private static boolean handleButtonClick(ServerPlayerEntity player, String rawName, String state, ItemStack stack) {
      String cleanName = TextFormatting.stripFormatting(rawName);
      String lowName = cleanName.toLowerCase();
      ServerWorld world = (ServerWorld)player.level;
      RaidSession session = RaidSpawner.getSessionSafe(world);
      int idx;
      if (lowName.contains("next page")) {
         idx = (Integer)RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
         RaidAdminCommand.playerShopPage.put(player.getUUID(), idx + 1);
         player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
         RaidAdminCommand.redrawCurrentMenu(player);
         return true;
      } else if (!lowName.contains("prev page") && !lowName.contains("previous page")) {
         if (lowName.contains("close shop")) {
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
            player.closeContainer();
            return true;
         } else if (!lowName.contains("return") && !lowName.contains("back") && !lowName.contains("cancel") && !lowName.contains("exit") && !lowName.contains("save & return")) {
            if (state.equals("REWARDS_HUB")) {
               if (lowName.contains("editing tier")) {
                  idx = (Integer)RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  idx = idx >= 5 ? 1 : idx + 1;
                  RaidAdminCommand.editingLootLevel.put(player.getUUID(), idx);
                  player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (lowName.contains("add winner")) {
                  idx = (Integer)RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  PixelmonRaidConfig.getInstance().getTierRewards(idx).addRank();
                  PixelmonRaidConfig.getInstance().save();
                  player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (lowName.contains("remove winner")) {
                  idx = (Integer)RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
                  PixelmonRaidConfig.getInstance().getTierRewards(idx).removeLastRank();
                  PixelmonRaidConfig.getInstance().save();
                  player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
                  RaidAdminCommand.redrawCurrentMenu(player);
                  return true;
               }

               if (stack.hasTag() && stack.getTag().contains("RankIndex")) {
                  idx = stack.getTag().getInt("RankIndex");
                  player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
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
                           idx = (Integer)RaidAdminCommand.editingItemIndex.getOrDefault(player.getUUID(), -1);
                           List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
                           if (idx < 0 || idx >= items.size()) {
                              switchMenu(player, "SHOP_EDITOR");
                              return true;
                           }

                           PixelmonRaidConfig.ShopItem entry = (PixelmonRaidConfig.ShopItem)items.get(idx);
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
                              player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 2.0F);
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
                           player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.5F);
                           RaidAdminCommand.redrawCurrentMenu(player);
                           return true;
                        } else if ((state.equals("SHOP_EDITOR") || state.equals("TOKEN_SHOP")) && stack.hasTag() && stack.getTag().contains("ShopIndex")) {
                           idx = stack.getTag().getInt("ShopIndex");
                           player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
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
                                 idx = (Integer)RaidAdminCommand.purchasingItemIndex.get(player.getUUID());
                                 int currentQty = (Integer)shopQuantities.getOrDefault(player.getUUID(), 1);
                                 boolean update = false;
                                 List<PixelmonRaidConfig.ShopItem> items = PixelmonRaidConfig.getInstance().getRaidTokenShop();
                                 if (idx < items.size()) {
                                    PixelmonRaidConfig.ShopItem entry = (PixelmonRaidConfig.ShopItem)items.get(idx);
                                    int baseCost;
                                    int baseCount;
                                    int totalCost;
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
                                       player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.5F);
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
                                                Iterator var30 = entry.commands.iterator();
                                                while(var30.hasNext()) {
                                                   String cmdTemplate = (String)var30.next();
                                                   String cmd = cmdTemplate.replace("%player%", player.getGameProfile().getName());

                                                   try {
                                                      player.getServer().getCommands().performCommand(player.getServer().createCommandSourceStack(), cmd);
                                                   } catch (Exception var24) {
                                                   }
                                                }
                                             }
                                          } else {
                                             ResourceLocation res = new ResourceLocation(entry.itemID);
                                             ItemStack give = new ItemStack((IItemProvider)ForgeRegistries.ITEMS.getValue(res), totalItems);

                                             try {
                                                if (entry.nbt != null && !entry.nbt.isEmpty()) {
                                                   give.setTag(JsonToNBT.parseTag(entry.nbt));
                                                }
                                             } catch (Exception var23) {
                                             }

                                             if (!player.inventory.add(give)) {
                                                player.drop(give, false);
                                             }
                                          }

                                          player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
                                          player.sendMessage(new StringTextComponent("§aSuccessful Purchase!"), Util.NIL_UUID);
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
                           player.sendMessage(new StringTextComponent("§c❌ Shop Editor is locked! You must enable the internal shop in the JSON file first."), Util.NIL_UUID);
                           return true;
                        } else {
                           switchMenu(player, "SHOP_EDITOR");
                           return true;
                        }
                     } else {
                        boolean newState = !PixelmonRaidConfig.getInstance().isAutoRaidEnabled();
                        PixelmonRaidConfig.getInstance().setAutoRaidEnabled(newState);
                        player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
                        if (newState) {
                           player.sendMessage(new StringTextComponent("§a[Raid] Auto-Spawn Enabled!"), Util.NIL_UUID);
                        } else {
                           player.sendMessage(new StringTextComponent("§c[Raid] Auto-Spawn Paused."), Util.NIL_UUID);
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
            player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
            if (state.matches("\\d+") || state.equals("killshot") || state.equals("participation")) {
               saveRewardsFromEditor(player, (Object)null, state);
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
         idx = (Integer)RaidAdminCommand.playerShopPage.getOrDefault(player.getUUID(), 0);
         RaidAdminCommand.playerShopPage.put(player.getUUID(), Math.max(0, idx - 1));
         player.playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F, 1.0F);
         RaidAdminCommand.redrawCurrentMenu(player);
         return true;
      }
   }

   private static void switchMenu(ServerPlayerEntity player, String newState) {
      IS_TRANSITIONING.add(player.getUUID());
      try {
         boolean isEnteringEditor = newState.equals("killshot") || newState.equals("participation") || newState.matches("\\d+");
         if (isEnteringEditor && player.containerMenu instanceof ChestContainer) {
            ChestContainer chest = (ChestContainer)player.containerMenu;
            int[] var4 = RaidAdminCommand.REWARD_SLOTS;
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
               int slot = var4[var6];
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
      List<String> serializedItems = new ArrayList();
      int[] var4 = RaidAdminCommand.REWARD_SLOTS;
      int var5 = var4.length;

      int r;
      for(r = 0; r < var5; ++r) {
         int slot = var4[r];
         ItemStack stack = player.containerMenu.getSlot(slot).getItem();
         if (stack != null && !stack.isEmpty() && stack.getItem() != Items.AIR && (!stack.hasTag() || !stack.getTag().getBoolean("RaidGuiItem"))) {
            String entry = stack.getItem().getRegistryName().toString() + " " + stack.getCount() + (stack.hasTag() ? " " + stack.getTag().toString() : "");
            serializedItems.add(entry);
         }
      }

      int lvl = (Integer)RaidAdminCommand.editingLootLevel.getOrDefault(player.getUUID(), 1);
      PixelmonRaidConfig.TierRewardConfig tier = PixelmonRaidConfig.getInstance().getTierRewards(lvl);
      if (rankId.equalsIgnoreCase("killshot")) {
         tier.killshot.items = serializedItems;
      } else if (rankId.equalsIgnoreCase("participation")) {
         tier.participation.items = serializedItems;
      } else {
         try {
            r = Integer.parseInt(rankId);
            if (!tier.winners.containsKey("Winner_" + r)) {
               tier.winners.put("Winner_" + r, new PixelmonRaidConfig.RankReward());
            }

            ((PixelmonRaidConfig.RankReward)tier.winners.get("Winner_" + r)).items = serializedItems;
         } catch (NumberFormatException var10) {
         }
      }

      PixelmonRaidConfig.getInstance().save();
      player.sendMessage(new StringTextComponent("§a[Raid] Saved physical items for Tier " + lvl + " - " + rankId + "!"), Util.NIL_UUID);
   }
}