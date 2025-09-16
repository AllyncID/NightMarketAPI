package me.allync.nightmarket.manager;

import me.allync.nightmarket.NightMarket;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MarketManager {

    private final NightMarket plugin;
    private boolean marketOpen = false;
    private long timeRemainingSeconds;
    private BukkitTask marketTask;
    private final List<BukkitTask> announcementTasks = new ArrayList<>();
    private final Random random = new Random();

    private final File marketStateFile;
    private FileConfiguration marketStateConfig;

    private final ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem> globalAssignedItems = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem>> playerAssignedItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> playerPurchasedItemKeys = new ConcurrentHashMap<>(); // Used for GLOBAL stock mode
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> playerPurchaseCounts = new ConcurrentHashMap<>(); // Used for PLAYER stock mode
    private final ConcurrentHashMap<UUID, Set<Integer>> playerRevealedSlots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> globalItemStock = new ConcurrentHashMap<>();


    public MarketManager(NightMarket plugin) {
        this.plugin = plugin;
        this.marketStateFile = new File(plugin.getDataFolder(), "market_state.yml");
    }

    public void loadMarketState() {
        if (!marketStateFile.exists()) {
            this.marketOpen = false;
            this.timeRemainingSeconds = plugin.getConfigManager().getCloseDurationSeconds();
            saveMarketState();
        } else {
            marketStateConfig = YamlConfiguration.loadConfiguration(marketStateFile);
            this.marketOpen = marketStateConfig.getBoolean("market_open", false);
            long lastSavedTime = marketStateConfig.getLong("last_saved_timestamp", System.currentTimeMillis() / 1000);
            long timePassed = (System.currentTimeMillis() / 1000) - lastSavedTime;
            long savedTimeRemaining = marketStateConfig.getLong("time_remaining_seconds");

            if (timePassed >= savedTimeRemaining) {
                if (marketOpen) {
                    this.marketOpen = false;
                    this.timeRemainingSeconds = plugin.getConfigManager().getCloseDurationSeconds();
                } else {
                    this.marketOpen = true;
                    this.timeRemainingSeconds = plugin.getConfigManager().getOpenDurationSeconds();
                    selectGlobalItemsForCycle();
                }
                clearAllPlayerData();
                resetGlobalStock();
                plugin.getLogger().info("Market cycle changed due to time expiry. Player data and global stock reset.");

            } else {
                this.timeRemainingSeconds = savedTimeRemaining - timePassed;
                clearAllPlayerData();

                ConfigurationSection globalItemsSection = marketStateConfig.getConfigurationSection("global_assigned_items");
                if (globalItemsSection != null) {
                    ItemManager itemManager = plugin.getItemManager();
                    for (String slotString : globalItemsSection.getKeys(false)) {
                        int slot = Integer.parseInt(slotString);
                        ConfigurationSection itemDataSection = globalItemsSection.getConfigurationSection(slotString);
                        if (itemDataSection != null) {
                            String itemKey = itemDataSection.getString("key");
                            boolean isDiscounted = itemDataSection.getBoolean("discounted");
                            double finalPrice = itemDataSection.getDouble("price");

                            ItemManager.ConfiguredItem baseItem = itemManager.getItemByKey(itemKey);
                            if (baseItem != null) {
                                ItemManager.PlayerMarketItem pmi = new ItemManager.PlayerMarketItem(baseItem, isDiscounted, finalPrice);
                                globalAssignedItems.put(slot, pmi);
                            }
                        }
                    }
                }


                ConfigurationSection purchasedSection = marketStateConfig.getConfigurationSection("player_purchases");
                if (purchasedSection != null) {
                    for (String uuidString : purchasedSection.getKeys(false)) {
                        try {
                            UUID playerUUID = UUID.fromString(uuidString);
                            List<String> purchasedKeys = purchasedSection.getStringList(uuidString);
                            playerPurchasedItemKeys.put(playerUUID, new HashSet<>(purchasedKeys));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid UUID string in market_state.yml under player_purchases: " + uuidString);
                        }
                    }
                }

                // Load PLAYER mode purchase data
                ConfigurationSection purchaseCountsSection = marketStateConfig.getConfigurationSection("player_purchase_counts");
                if (purchaseCountsSection != null) {
                    for (String uuidString : purchaseCountsSection.getKeys(false)) {
                        try {
                            UUID playerUUID = UUID.fromString(uuidString);
                            ConfigurationSection playerCounts = purchaseCountsSection.getConfigurationSection(uuidString);
                            if (playerCounts != null) {
                                ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
                                for (String itemKey : playerCounts.getKeys(false)) {
                                    counts.put(itemKey, playerCounts.getInt(itemKey));
                                }
                                playerPurchaseCounts.put(playerUUID, counts);
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid UUID string in market_state.yml under player_purchase_counts: " + uuidString);
                        }
                    }
                }


                ConfigurationSection revealedSection = marketStateConfig.getConfigurationSection("player_revealed_slots");
                if (revealedSection != null) {
                    for (String uuidString : revealedSection.getKeys(false)) {
                        try {
                            UUID playerUUID = UUID.fromString(uuidString);
                            Set<Integer> revealed = new HashSet<>(revealedSection.getIntegerList(uuidString));
                            playerRevealedSlots.put(playerUUID, revealed);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid UUID string in market_state.yml under player_revealed_slots: " + uuidString);
                        }
                    }
                }

                ConfigurationSection assignedSection = marketStateConfig.getConfigurationSection("player_assigned_items");
                if (assignedSection != null) {
                    ItemManager itemManager = plugin.getItemManager();
                    for (String uuidString : assignedSection.getKeys(false)) {
                        try {
                            UUID playerUUID = UUID.fromString(uuidString);
                            ConfigurationSection playerSection = assignedSection.getConfigurationSection(uuidString);
                            if (playerSection != null) {
                                ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem> assignedItemsMap = new ConcurrentHashMap<>();
                                for (String slotString : playerSection.getKeys(false)) {
                                    int slot = Integer.parseInt(slotString);
                                    ConfigurationSection itemDataSection = playerSection.getConfigurationSection(slotString);
                                    if (itemDataSection != null) {
                                        String itemKey = itemDataSection.getString("key");
                                        boolean isDiscounted = itemDataSection.getBoolean("discounted");
                                        double finalPrice = itemDataSection.getDouble("price");

                                        ItemManager.ConfiguredItem baseItem = itemManager.getItemByKey(itemKey);
                                        if (baseItem != null) {
                                            ItemManager.PlayerMarketItem pmi = new ItemManager.PlayerMarketItem(baseItem, isDiscounted, finalPrice);
                                            assignedItemsMap.put(slot, pmi);
                                        }
                                    }
                                }
                                playerAssignedItems.put(playerUUID, assignedItemsMap);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to load assigned items for a player in market_state.yml.", e);
                        }
                    }
                }

                globalItemStock.clear();
                ConfigurationSection stockSection = marketStateConfig.getConfigurationSection("global_item_stock");
                if (stockSection != null) {
                    for (String itemKey : stockSection.getKeys(false)) {
                        globalItemStock.put(itemKey, stockSection.getInt(itemKey));
                    }
                } else {
                    resetGlobalStock();
                }
            }
        }
        plugin.getLogger().info("Market state loaded: Open=" + marketOpen + ", Time Remaining=" + formatTime(timeRemainingSeconds));
        scheduleAnnouncements();
    }

    public void saveMarketState() {
        if (marketStateConfig == null) {
            marketStateConfig = YamlConfiguration.loadConfiguration(marketStateFile);
        }
        marketStateConfig.set("market_open", this.marketOpen);
        marketStateConfig.set("time_remaining_seconds", this.timeRemainingSeconds);
        marketStateConfig.set("last_saved_timestamp", System.currentTimeMillis() / 1000);

        // --- NEW: Save global items ---
        marketStateConfig.set("global_assigned_items", null);
        ConfigurationSection globalItemsSection = marketStateConfig.createSection("global_assigned_items");
        for (Map.Entry<Integer, ItemManager.PlayerMarketItem> entry : globalAssignedItems.entrySet()) {
            ConfigurationSection itemDataSection = globalItemsSection.createSection(String.valueOf(entry.getKey()));
            ItemManager.PlayerMarketItem pmi = entry.getValue();
            itemDataSection.set("key", pmi.getBaseItem().getKey());
            itemDataSection.set("discounted", pmi.isDiscounted());
            itemDataSection.set("price", pmi.getFinalPrice());
        }


        // Save GLOBAL mode data
        marketStateConfig.set("player_purchases", null);
        ConfigurationSection purchasedSection = marketStateConfig.createSection("player_purchases");
        for (Map.Entry<UUID, Set<String>> entry : playerPurchasedItemKeys.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                purchasedSection.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
        }

        // Save PLAYER mode data
        marketStateConfig.set("player_purchase_counts", null);
        ConfigurationSection purchaseCountsSection = marketStateConfig.createSection("player_purchase_counts");
        for (Map.Entry<UUID, ConcurrentHashMap<String, Integer>> entry : playerPurchaseCounts.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                ConfigurationSection playerCountsSection = purchaseCountsSection.createSection(entry.getKey().toString());
                for (Map.Entry<String, Integer> countEntry : entry.getValue().entrySet()) {
                    playerCountsSection.set(countEntry.getKey(), countEntry.getValue());
                }
            }
        }

        marketStateConfig.set("player_revealed_slots", null);
        ConfigurationSection revealedSection = marketStateConfig.createSection("player_revealed_slots");
        for (Map.Entry<UUID, Set<Integer>> entry : playerRevealedSlots.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                revealedSection.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
        }

        marketStateConfig.set("player_assigned_items", null);
        ConfigurationSection assignedSection = marketStateConfig.createSection("player_assigned_items");
        for (Map.Entry<UUID, ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem>> entry : playerAssignedItems.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                ConfigurationSection playerSection = assignedSection.createSection(entry.getKey().toString());
                for (Map.Entry<Integer, ItemManager.PlayerMarketItem> itemEntry : entry.getValue().entrySet()) {
                    ConfigurationSection itemDataSection = playerSection.createSection(String.valueOf(itemEntry.getKey()));
                    ItemManager.PlayerMarketItem pmi = itemEntry.getValue();
                    itemDataSection.set("key", pmi.getBaseItem().getKey());
                    itemDataSection.set("discounted", pmi.isDiscounted());
                    itemDataSection.set("price", pmi.getFinalPrice());
                }
            }
        }

        marketStateConfig.set("global_item_stock", null);
        ConfigurationSection stockSection = marketStateConfig.createSection("global_item_stock");
        for (Map.Entry<String, Integer> entry : globalItemStock.entrySet()) {
            stockSection.set(entry.getKey(), entry.getValue());
        }

        try {
            marketStateConfig.save(marketStateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save market state to market_state.yml: " + e.getMessage());
        }
    }

    public void startMarketCycle() {
        if (marketTask != null) {
            marketTask.cancel();
        }
        marketTask = new BukkitRunnable() {
            @Override
            public void run() {
                timeRemainingSeconds--;
                if (timeRemainingSeconds <= 0) {
                    if (marketOpen) {
                        closeMarketScheduled();
                        timeRemainingSeconds = plugin.getConfigManager().getCloseDurationSeconds();
                    } else {
                        openMarketScheduled();
                        timeRemainingSeconds = plugin.getConfigManager().getOpenDurationSeconds();
                    }
                    scheduleAnnouncements();
                }
                if (timeRemainingSeconds > 0 && timeRemainingSeconds % 300 == 0) {
                    saveMarketState();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void openMarketScheduled() {
        marketOpen = true;
        List<String> openMessages = plugin.getConfigManager().getMarketOpenBroadcast();
        for (String message : openMessages) {
            Bukkit.broadcastMessage(message);
        }

        ConfigManager.SoundConfig openSound = plugin.getConfigManager().getMarketOpenSound();
        if (openSound != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), openSound.sound(), openSound.volume(), openSound.pitch());
            }
        }

        clearAllPlayerData();
        resetGlobalStock();
        selectGlobalItemsForCycle();
        plugin.getLogger().info("Night Market (Scheduled) is now OPEN. Player data and global stock reset for this cycle.");
        saveMarketState();
    }

    private void closeMarketScheduled() {
        marketOpen = false;
        List<String> closeMessages = plugin.getConfigManager().getMarketCloseBroadcast();
        for (String message : closeMessages) {
            Bukkit.broadcastMessage(message);
        }

        ConfigManager.SoundConfig closeSound = plugin.getConfigManager().getMarketClosedSound();
        if (closeSound != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), closeSound.sound(), closeSound.volume(), closeSound.pitch());
            }
        }

        plugin.getLogger().info("Night Market (Scheduled) is now CLOSED.");
        saveMarketState();
    }

    public void forceOpenMarket() {
        marketOpen = true;
        timeRemainingSeconds = plugin.getConfigManager().getOpenDurationSeconds();
        clearAllPlayerData();
        resetGlobalStock();
        selectGlobalItemsForCycle();

        List<String> forceOpenMessages = plugin.getConfigManager().getForceOpenMessage();
        for (String message : forceOpenMessages) {
            Bukkit.broadcastMessage(message);
        }

        ConfigManager.SoundConfig openSound = plugin.getConfigManager().getMarketOpenSound();
        if (openSound != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), openSound.sound(), openSound.volume(), openSound.pitch());
            }
        }

        plugin.getLogger().info("Night Market has been FORCE OPENED. Player data and global stock reset. New cycle duration: " + formatTime(timeRemainingSeconds));
        saveMarketState();
        if (marketTask != null) marketTask.cancel();
        startMarketCycle();
        scheduleAnnouncements();
    }

    public void forceCloseMarket() {
        marketOpen = false;
        timeRemainingSeconds = plugin.getConfigManager().getCloseDurationSeconds();

        List<String> forceCloseMessages = plugin.getConfigManager().getForceCloseMessage();
        for (String message : forceCloseMessages) {
            Bukkit.broadcastMessage(message);
        }

        ConfigManager.SoundConfig closeSound = plugin.getConfigManager().getMarketClosedSound();
        if (closeSound != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), closeSound.sound(), closeSound.volume(), closeSound.pitch());
            }
        }

        plugin.getLogger().info("Night Market has been FORCE CLOSED. New cycle duration: " + formatTime(timeRemainingSeconds));
        saveMarketState();
        if (marketTask != null) marketTask.cancel();
        startMarketCycle();
        scheduleAnnouncements();
    }

    public void resetPlayerData(UUID playerUUID) {
        playerAssignedItems.remove(playerUUID);
        playerPurchasedItemKeys.remove(playerUUID);
        playerPurchaseCounts.remove(playerUUID);
        playerRevealedSlots.remove(playerUUID);
        plugin.getLogger().info("Reset Night Market data for player " + playerUUID + " for the current cycle.");
        saveMarketState();
    }

    public void rerollMarket(Player player) {
        if (player == null) return;
        resetPlayerData(player.getUniqueId());
        plugin.getLogger().info("Full market data reset for player " + player.getName() + " (" + player.getUniqueId() + ") due to reroll.");
    }

    private void clearAllPlayerData() {
        playerAssignedItems.clear();
        playerPurchasedItemKeys.clear();
        playerPurchaseCounts.clear();
        playerRevealedSlots.clear();
        globalAssignedItems.clear();
    }

    public void resetAllPlayersData() {
        clearAllPlayerData();
        resetGlobalStock();
        selectGlobalItemsForCycle();
        plugin.getLogger().info("Reset Night Market data for ALL players and global stock for the current cycle.");
        saveMarketState();
    }

    private void resetGlobalStock() {
        globalItemStock.clear();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager != null) {
            for (ItemManager.ConfiguredItem item : itemManager.getAllPossibleItems()) {
                globalItemStock.put(item.getKey(), item.getInitialStock());
            }
        }
    }

    private void selectGlobalItemsForCycle() {
        globalAssignedItems.clear();
        int itemsToSelect = plugin.getConfigManager().getNumberOfGlobalItemsToShow();
        if (itemsToSelect <= 0) {
            return;
        }

        ItemManager itemManager = plugin.getItemManager();
        List<ItemManager.ConfiguredItem> globalCandidates = itemManager.getAllPossibleItems().stream()
                .filter(ItemManager.ConfiguredItem::isGlobal)
                .collect(Collectors.toList());

        if (globalCandidates.isEmpty()) {
            return;
        }

        List<Integer> availableSlots = new ArrayList<>(plugin.getConfigManager().getMarketSlotMappings().keySet());
        Collections.shuffle(availableSlots);

        int selectedCount = 0;
        while (selectedCount < itemsToSelect && !globalCandidates.isEmpty() && !availableSlots.isEmpty()) {
            ItemManager.ConfiguredItem selected = itemManager.getRandomWeightedItemFromList(globalCandidates);
            if (selected != null) {
                int slot = availableSlots.remove(0);
                ItemManager.PlayerMarketItem pmi = new ItemManager.PlayerMarketItem(selected, random);
                globalAssignedItems.put(slot, pmi);

                globalCandidates.remove(selected);
                selectedCount++;
            } else {
                break;
            }
        }

        if (selectedCount > 0) {
            plugin.getLogger().info("Selected " + selectedCount + " global item(s) for this market cycle.");
        }
    }


    public int getRemainingStock(String itemKey) {
        return globalItemStock.getOrDefault(itemKey, 0);
    }

    public void decrementStock(String itemKey) {
        Integer currentStock = globalItemStock.get(itemKey);
        if (currentStock != null && currentStock > 0) { // -1 stock is infinite, so no decrement
            globalItemStock.put(itemKey, currentStock - 1);
        }
    }

    public void markSlotAsRevealed(UUID playerUUID, int guiSlot) {
        playerRevealedSlots.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(guiSlot);
    }

    public boolean isSlotRevealed(UUID playerUUID, int guiSlot) {
        Set<Integer> revealed = playerRevealedSlots.get(playerUUID);
        return revealed != null && revealed.contains(guiSlot);
    }

    public boolean isMarketOpen() { return marketOpen; }
    public long getTimeRemainingSeconds() { return timeRemainingSeconds; }
    public String getFormattedTimeRemaining() { return formatTime(timeRemainingSeconds); }

    private String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = ((totalSeconds % 86400) % 3600) / 60;
        long seconds = ((totalSeconds % 86400) % 3600) % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public ItemManager.PlayerMarketItem getOrAssignPlayerItemForSlot(Player player, int guiInventorySlot, String ymlItemSourceKey) {
        if (globalAssignedItems.containsKey(guiInventorySlot)) {
            return globalAssignedItems.get(guiInventorySlot);
        }

        UUID playerUUID = player.getUniqueId();
        playerAssignedItems.putIfAbsent(playerUUID, new ConcurrentHashMap<>());
        ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem> playerItems = playerAssignedItems.get(playerUUID);

        if (playerItems.containsKey(guiInventorySlot)) {
            return playerItems.get(guiInventorySlot);
        }

        Set<String> alreadyAssignedKeys = playerItems.values().stream()
                .map(pmi -> pmi.getBaseItem().getKey())
                .collect(Collectors.toSet());

        globalAssignedItems.values().forEach(pmi -> alreadyAssignedKeys.add(pmi.getBaseItem().getKey()));

        List<ItemManager.ConfiguredItem> personalItemPool = plugin.getItemManager().getAllPossibleItems().stream()
                .filter(item -> !item.isGlobal())
                .collect(Collectors.toList());

        ItemManager.ConfiguredItem newItem = null;
        int attempts = 0;
        int maxAttempts = 50;

        while (attempts < maxAttempts) {
            ItemManager.ConfiguredItem potentialItem = plugin.getItemManager().getRandomWeightedItemFromList(personalItemPool);
            if (potentialItem != null && !alreadyAssignedKeys.contains(potentialItem.getKey())) {
                newItem = potentialItem;
                break;
            }
            attempts++;
        }

        if (newItem != null) {
            ItemManager.PlayerMarketItem playerMarketItem = new ItemManager.PlayerMarketItem(newItem, random);
            playerItems.put(guiInventorySlot, playerMarketItem);
            return playerMarketItem;
        } else {
            plugin.getLogger().warning("Failed to assign a unique PERSONAL item for player " + player.getName() + " at slot " + guiInventorySlot + " after " + maxAttempts + " attempts.");
            return null;
        }
    }


    public ItemManager.PlayerMarketItem getPlayerAssignedItem(Player player, int guiInventorySlot) {
        if (globalAssignedItems.containsKey(guiInventorySlot)) {
            return globalAssignedItems.get(guiInventorySlot);
        }

        ConcurrentHashMap<Integer, ItemManager.PlayerMarketItem> playerItems = playerAssignedItems.get(player.getUniqueId());
        if (playerItems != null) {
            return playerItems.get(guiInventorySlot);
        }
        return null;
    }

    public void recordPlayerPurchase(UUID playerUUID, String configuredItemKey) {
        if (plugin.getConfigManager().getStockMode() == ConfigManager.StockMode.GLOBAL) {
            playerPurchasedItemKeys.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(configuredItemKey);
        } else {
            playerPurchaseCounts.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                    .compute(configuredItemKey, (key, count) -> (count == null) ? 1 : count + 1);
        }
    }

    public boolean hasPlayerMetPurchaseLimit(UUID playerUUID, ItemManager.ConfiguredItem item) {
        if (plugin.getConfigManager().getStockMode() == ConfigManager.StockMode.GLOBAL) {
            Set<String> purchasedKeys = playerPurchasedItemKeys.get(playerUUID);
            return purchasedKeys != null && purchasedKeys.contains(item.getKey());
        } else {
            if (item.getInitialStock() == -1) return false; // Unlimited personal stock
            return getPlayerPurchaseCount(playerUUID, item.getKey()) >= item.getInitialStock();
        }
    }

    public int getPlayerPurchaseCount(UUID playerUUID, String itemKey) {
        return playerPurchaseCounts.getOrDefault(playerUUID, new ConcurrentHashMap<>()).getOrDefault(itemKey, 0);
    }


    public void stopMarketCycle() {
        if (marketTask != null) {
            marketTask.cancel();
            marketTask = null;
        }
        cancelAnnouncementTasks();
    }

    private void cancelAnnouncementTasks() {
        announcementTasks.forEach(BukkitTask::cancel);
        announcementTasks.clear();
    }

    private void scheduleAnnouncements() {
        cancelAnnouncementTasks();

        List<ConfigManager.Announcement> announcements = marketOpen
                ? plugin.getConfigManager().getBeforeCloseAnnouncements()
                : plugin.getConfigManager().getBeforeOpenAnnouncements();

        for (ConfigManager.Announcement announcement : announcements) {
            long timeBeforeEvent = announcement.timeBeforeSeconds();
            long delayInSeconds = timeRemainingSeconds - timeBeforeEvent;

            if (delayInSeconds >= 0) {
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        announcement.message().forEach(Bukkit::broadcastMessage);
                    }
                }.runTaskLater(plugin, delayInSeconds * 20L);
                announcementTasks.add(task);
            }
        }
    }
}