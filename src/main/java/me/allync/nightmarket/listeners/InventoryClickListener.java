package me.allync.nightmarket.listeners;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.economy.EconomyProvider;
import me.allync.nightmarket.economy.provider.ItemEconomyProvider;
import me.allync.nightmarket.gui.NightMarketGUI;
import me.allync.nightmarket.manager.ConfigManager;
import me.allync.nightmarket.manager.ItemManager;
import me.allync.nightmarket.manager.MarketManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class InventoryClickListener implements Listener {

    private final NightMarket plugin;
    private final NightMarketGUI nightMarketGUI;

    public InventoryClickListener(NightMarket plugin) {
        this.plugin = plugin;
        this.nightMarketGUI = plugin.getNightMarketGUI();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) return;

        ConfigManager configManager = plugin.getConfigManager();
        String viewTitle = event.getView().getTitle();

        if (viewTitle.equals(configManager.getGuiTitle())) {
            handleNightMarketClick(event, player, currentItem);
        } else if (viewTitle.equals(configManager.getConfirmationMenuTitle())) {
            handleConfirmationMenuClick(event, player, currentItem);
        }
    }

    private void handleNightMarketClick(InventoryClickEvent event, Player player, ItemStack currentItem) {
        event.setCancelled(true);
        ConfigManager configManager = plugin.getConfigManager();
        MarketManager marketManager = plugin.getMarketManager();
        int clickedSlot = event.getRawSlot();

        if (clickedSlot >= event.getView().getTopInventory().getSize() || !configManager.isMarketItemSlot(clickedSlot)) {
            return;
        }

        if (currentItem.isSimilar(configManager.getGlobalPlaceholderItem())) {
            configManager.playSound(player, configManager.getItemRevealSound());
            nightMarketGUI.revealItem(player, clickedSlot);
            return;
        }

        ItemManager.PlayerMarketItem assignedItem = marketManager.getPlayerAssignedItem(player, clickedSlot);
        if (assignedItem == null) return;

        if (!assignedItem.getBaseItem().hasPermission(player)) {
            player.sendMessage(configManager.getMsgNoPermissionToBuyItem());
            configManager.playSound(player, configManager.getPermissionDeniedSound());
            return;
        }

        if (!currentItem.isSimilar(configManager.getGlobalPurchasedItemDisplay()) && !currentItem.isSimilar(configManager.getGlobalOutOfStockItemDisplay())) {
            if (marketManager.hasPlayerMetPurchaseLimit(player.getUniqueId(), assignedItem.getBaseItem())) {
                player.sendMessage(configManager.getMsgItemAlreadyPurchasedDesync());
                nightMarketGUI.openNightMarketGUI(player);
            } else {
                nightMarketGUI.openConfirmationGUI(player, currentItem.clone(), clickedSlot);
            }
        } else {
            if (configManager.getStockMode() == ConfigManager.StockMode.GLOBAL) {
                if (marketManager.getRemainingStock(assignedItem.getBaseItem().getKey()) == 0) {
                    player.sendMessage(configManager.getMsgItemSoldOut());
                } else {
                    player.sendMessage(configManager.getMsgItemAlreadyPurchased());
                }
            } else {
                player.sendMessage(configManager.getMsgItemOutOfStockPlayer());
            }
        }
    }

    private void handleConfirmationMenuClick(InventoryClickEvent event, Player player, ItemStack currentItem) {
        event.setCancelled(true);
        ConfigManager configManager = plugin.getConfigManager();
        ItemStack confirmButtonItem = createMenuItem(configManager.getConfirmationMenuConfirmButton());
        ItemStack cancelButtonItem = createMenuItem(configManager.getConfirmationMenuCancelButton());

        if (confirmButtonItem != null && currentItem.isSimilar(confirmButtonItem)) {
            purchaseItem(player);
        } else if (cancelButtonItem != null && currentItem.isSimilar(cancelButtonItem)) {
            NightMarketGUI.getPendingConfirmation().remove(player.getUniqueId());
            nightMarketGUI.openNightMarketGUI(player);
        }
    }

    private void purchaseItem(Player player) {
        UUID playerUUID = player.getUniqueId();
        Integer sourceSlot = NightMarketGUI.getPendingConfirmation().remove(playerUUID);
        if (sourceSlot == null) return;

        MarketManager marketManager = plugin.getMarketManager();
        ConfigManager configManager = plugin.getConfigManager();
        ItemManager.PlayerMarketItem playerMarketItem = marketManager.getPlayerAssignedItem(player, sourceSlot);
        if (playerMarketItem == null) return;

        ItemManager.ConfiguredItem configuredItem = playerMarketItem.getBaseItem();

        if (!configuredItem.hasPermission(player)) {
            player.sendMessage(configManager.getMsgNoPermissionToBuyItem());
            nightMarketGUI.openNightMarketGUI(player);
            return;
        }

        if (configManager.getStockMode() == ConfigManager.StockMode.GLOBAL) {
            if (marketManager.getRemainingStock(configuredItem.getKey()) == 0) {
                player.sendMessage(configManager.getMsgItemSoldOut());
                nightMarketGUI.openNightMarketGUI(player);
                return;
            }
            if (marketManager.hasPlayerMetPurchaseLimit(playerUUID, configuredItem)) {
                player.sendMessage(configManager.getMsgItemAlreadyPurchased());
                nightMarketGUI.openNightMarketGUI(player);
                return;
            }
        } else { // PLAYER mode
            if (marketManager.hasPlayerMetPurchaseLimit(playerUUID, configuredItem)) {
                player.sendMessage(configManager.getMsgItemOutOfStockPlayer());
                nightMarketGUI.openNightMarketGUI(player);
                return;
            }
        }

        boolean transactionSuccess = false;
        if (configuredItem.hasItemPrice()) {

            ItemEconomyProvider itemEcon = plugin.getEconomyManager().getItemEconomyProvider();
            ItemEconomyProvider.setCurrentItemForTransaction(configuredItem);

            if (!itemEcon.has(player, 0)) {
                player.sendMessage(configManager.getMsgNotEnoughItems());
                nightMarketGUI.openNightMarketGUI(player);
                return;
            }
            transactionSuccess = itemEcon.withdraw(player, 0);

        } else if (plugin.getEconomyManager().isEconomyHooked()) {
            EconomyProvider econ = plugin.getEconomyManager().getProvider();
            double price = playerMarketItem.getFinalPrice();

            if (price > 0) {
                if (!econ.has(player, price)) {
                    player.sendMessage(configManager.getMsgNotEnoughMoney());
                    nightMarketGUI.openNightMarketGUI(player);
                    return;
                }
                transactionSuccess = econ.withdraw(player, price);
            } else {
                transactionSuccess = true;
            }
        } else {
            transactionSuccess = true;
        }

        if (!transactionSuccess) {
            player.sendMessage("§cTransaction Failed: Could not process payment.");
            nightMarketGUI.openNightMarketGUI(player);
            return;
        }


        String itemName = configuredItem.getDisplayItem().hasItemMeta() && configuredItem.getDisplayItem().getItemMeta().hasDisplayName()
                ? configuredItem.getDisplayItem().getItemMeta().getDisplayName()
                : configuredItem.getDisplayItem().getType().toString();

        player.sendMessage(configManager.getMsgItemSuccessfullyPurchased(
                configuredItem.hasItemPrice() ? "the required items" : String.format("%,.0f", playerMarketItem.getFinalPrice()),
                itemName
        ));

        configManager.playSound(player, configManager.getItemPurchaseSound());
        for (String cmd : configuredItem.getCommandsOnClick()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        if (configManager.getStockMode() == ConfigManager.StockMode.GLOBAL) {
            marketManager.decrementStock(configuredItem.getKey());
        }

        marketManager.recordPlayerPurchase(playerUUID, configuredItem.getKey());
        marketManager.saveMarketState();
        nightMarketGUI.openNightMarketGUI(player);
    }

    private ItemStack createMenuItem(ConfigManager.DecorativeItemConfig config) {
        if (config == null) return null;
        ItemStack stack = new ItemStack(config.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.name);
            meta.setLore(config.lore);
            if (config.customModelData != 0) meta.setCustomModelData(config.customModelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
