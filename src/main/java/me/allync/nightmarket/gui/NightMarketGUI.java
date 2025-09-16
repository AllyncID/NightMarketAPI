package me.allync.nightmarket.gui;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.manager.ConfigManager;
import me.allync.nightmarket.manager.ItemManager;
import me.allync.nightmarket.manager.MarketManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NightMarketGUI {

    private final NightMarket plugin;
    private static final Map<UUID, Integer> pendingConfirmation = new HashMap<>();

    public NightMarketGUI(NightMarket plugin) {
        this.plugin = plugin;
    }

    public void openNightMarketGUI(Player player) {
        MarketManager marketManager = plugin.getMarketManager();
        ConfigManager configManager = plugin.getConfigManager();

        if (!marketManager.isMarketOpen()) {
            configManager.playSound(player, configManager.getMarketClosedSound());
            List<String> closedMessages = configManager.getMsgMarketClosed();
            String timeRemaining = marketManager.getFormattedTimeRemaining();
            for (String message : closedMessages) {
                player.sendMessage(message.replace("%time_remaining%", timeRemaining));
            }
            return;
        }

        String title = configManager.getGuiTitle();
        int rows = configManager.getGuiRows();
        Inventory gui = Bukkit.createInventory(player, rows * 9, title);

        populateMarketItems(gui, player);
        populateDecorativeItems(gui);

        configManager.playSound(player, configManager.getGuiOpenSound());
        player.openInventory(gui);
    }

    private void populateMarketItems(Inventory gui, Player player) {
        MarketManager marketManager = plugin.getMarketManager();
        ConfigManager configManager = plugin.getConfigManager();
        UUID playerUUID = player.getUniqueId();

        for (int guiSlot : configManager.getMarketSlotMappings().keySet()) {
            if (guiSlot >= gui.getSize()) continue;

            ItemManager.PlayerMarketItem assignedItem = marketManager.getOrAssignPlayerItemForSlot(player, guiSlot, configManager.getYmlKeyForGuiSlot(guiSlot));

            if (assignedItem == null) {
                if (marketManager.isSlotRevealed(playerUUID, guiSlot)) {
                } else {
                    gui.setItem(guiSlot, configManager.getGlobalPlaceholderItem());
                }
                continue;
            }

            if (!marketManager.isSlotRevealed(playerUUID, guiSlot)) {
                gui.setItem(guiSlot, configManager.getGlobalPlaceholderItem());
            } else {
                if (!assignedItem.getBaseItem().hasPermission(player)) {
                    gui.setItem(guiSlot, getPermissionDeniedItem(assignedItem));
                } else if (configManager.getStockMode() == ConfigManager.StockMode.GLOBAL && marketManager.getRemainingStock(assignedItem.getBaseItem().getKey()) == 0) {
                    gui.setItem(guiSlot, configManager.getGlobalOutOfStockItemDisplay());
                } else if (marketManager.hasPlayerMetPurchaseLimit(playerUUID, assignedItem.getBaseItem())) {
                    gui.setItem(guiSlot, configManager.getGlobalPurchasedItemDisplay());
                } else {
                    gui.setItem(guiSlot, getDisplayItemWithDynamicLore(player, assignedItem));
                }
            }
        }
    }

    private void populateDecorativeItems(Inventory gui) {
        ConfigManager configManager = plugin.getConfigManager();
        List<ConfigManager.DecorativeItemConfig> decorativeItems = configManager.getDecorativeItems();
        for (ConfigManager.DecorativeItemConfig decoItem : decorativeItems) {
            ItemStack itemStack = createMenuItem(decoItem);
            if (itemStack != null) {
                for (int slot : decoItem.slots) {
                    if (slot >= 0 && slot < gui.getSize()) {
                        gui.setItem(slot, itemStack);
                    }
                }
            }
        }
    }

    public void openConfirmationGUI(Player player, ItemStack itemToPurchase, int sourceSlot) {
        ConfigManager configManager = plugin.getConfigManager();
        String title = configManager.getConfirmationMenuTitle();
        int rows = configManager.getConfirmationMenuRows();
        Inventory confirmationGui = Bukkit.createInventory(player, rows * 9, title);

        confirmationGui.setItem(configManager.getConfirmationMenuItemDisplaySlot(), itemToPurchase);

        ConfigManager.DecorativeItemConfig confirmConfig = configManager.getConfirmationMenuConfirmButton();
        if (confirmConfig != null && !confirmConfig.slots.isEmpty()) {
            confirmationGui.setItem(confirmConfig.slots.get(0), createMenuItem(confirmConfig));
        }

        ConfigManager.DecorativeItemConfig cancelConfig = configManager.getConfirmationMenuCancelButton();
        if (cancelConfig != null && !cancelConfig.slots.isEmpty()) {
            confirmationGui.setItem(cancelConfig.slots.get(0), createMenuItem(cancelConfig));
        }

        ConfigManager.DecorativeItemConfig fillConfig = configManager.getConfirmationMenuFillItem();
        if (fillConfig != null) {
            ItemStack fillStack = createMenuItem(fillConfig);
            if (fillStack != null) {
                for (int slot : fillConfig.slots) {
                    if (slot >= 0 && slot < confirmationGui.getSize()) {
                        confirmationGui.setItem(slot, fillStack);
                    }
                }
            }
        }

        pendingConfirmation.put(player.getUniqueId(), sourceSlot);
        player.openInventory(confirmationGui);
    }

    public void revealItem(Player player, int clickedGuiSlot) {
        MarketManager marketManager = plugin.getMarketManager();
        ItemManager.PlayerMarketItem itemToReveal = marketManager.getPlayerAssignedItem(player, clickedGuiSlot);
        Inventory openInventory = player.getOpenInventory().getTopInventory();

        if (itemToReveal != null && openInventory.getViewers().contains(player)) {
            marketManager.markSlotAsRevealed(player.getUniqueId(), clickedGuiSlot);

            ItemStack display;
            if (!itemToReveal.getBaseItem().hasPermission(player)) {
                display = getPermissionDeniedItem(itemToReveal);
            } else {
                display = getDisplayItemWithDynamicLore(player, itemToReveal);
            }
            openInventory.setItem(clickedGuiSlot, display);
        }
    }

    private ItemStack getPermissionDeniedItem(ItemManager.PlayerMarketItem playerMarketItem) {
        ItemStack displayItem = getDisplayItemWithDynamicLore(null, playerMarketItem);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> newLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            newLore.addAll(plugin.getConfigManager().getPermissionDeniedLore());
            meta.setLore(newLore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack getDisplayItemWithDynamicLore(Player player, ItemManager.PlayerMarketItem playerMarketItem) {
        ItemStack displayItem = playerMarketItem.getBaseItem().getDisplayItem();
        ItemMeta meta = displayItem.getItemMeta();
        ItemManager.ConfiguredItem baseItem = playerMarketItem.getBaseItem();
        MarketManager marketManager = plugin.getMarketManager();
        ConfigManager configManager = plugin.getConfigManager();

        if (meta != null && meta.hasLore()) {
            List<String> newLore = new ArrayList<>();
            String stockDisplay;

            if (player != null) {
                if (configManager.getStockMode() == ConfigManager.StockMode.GLOBAL) {
                    int globalStock = marketManager.getRemainingStock(baseItem.getKey());
                    stockDisplay = (globalStock == -1) ? "Infinite" : String.valueOf(globalStock);
                } else { // PLAYER mode
                    int playerPurchaseCount = marketManager.getPlayerPurchaseCount(player.getUniqueId(), baseItem.getKey());
                    int itemMaxStock = baseItem.getInitialStock();
                    int playerRemainingStock = (itemMaxStock == -1) ? -1 : itemMaxStock - playerPurchaseCount;
                    stockDisplay = (playerRemainingStock == -1) ? "Infinite" : String.valueOf(playerRemainingStock);
                }
            } else {
                stockDisplay = String.valueOf(baseItem.getInitialStock());
                if (stockDisplay.equals("-1")) stockDisplay = "Infinite";
            }


            for (String line : meta.getLore()) {
                String processedLine = line;

                // Logika Harga Baru
                if (baseItem.hasItemPrice()) {
                    if (processedLine.contains("%price_placeholder%")) {
                        continue;
                    }
                } else {
                    // Logika Harga Uang (yang sudah ada)
                    if (processedLine.contains("%price_placeholder%")) {
                        ItemManager.PricePlaceholderConfig priceFormats = plugin.getItemManager().getPricePlaceholderConfig();
                        String priceLine;
                        if (playerMarketItem.isDiscounted()) {
                            priceLine = priceFormats.discounted()
                                    .replace("%original_price%", String.format("%,.0f", baseItem.getPrice()));
                        } else {
                            priceLine = priceFormats.normal();
                        }
                        priceLine = priceLine.replace("%final_price%", String.format("%,.0f", playerMarketItem.getFinalPrice()));
                        processedLine = priceLine;
                    }
                }

                processedLine = processedLine.replace("%stock%", stockDisplay);

                newLore.add(processedLine);
            }
            meta.setLore(newLore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
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

    public static Map<UUID, Integer> getPendingConfirmation() {
        return pendingConfirmation;
    }

    public static void cleanUp() {
        pendingConfirmation.clear();
    }
}
