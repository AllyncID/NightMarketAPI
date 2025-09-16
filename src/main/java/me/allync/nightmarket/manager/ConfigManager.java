package me.allync.nightmarket.manager;

import me.allync.nightmarket.NightMarket;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ConfigManager {

    private final NightMarket plugin;
    private FileConfiguration generalConfig;
    private FileConfiguration guiConfig;

    private Material globalPlaceholderMaterial, globalPurchasedItemMaterial, globalOutOfStockItemMaterial;
    private String globalPlaceholderName, globalPurchasedItemName, globalOutOfStockItemName;
    private List<String> globalPlaceholderLore, globalPurchasedItemLore, globalOutOfStockItemLore, permissionDeniedLore;
    private long openDurationSeconds, closeDurationSeconds;
    private List<String> marketOpenBroadcast, marketCloseBroadcast, forceOpenMessage, forceCloseMessage;
    private boolean economyEnabled;
    private String economyProviderName;
    private boolean checkUpdates;
    private StockMode stockMode;
    private int numberOfGlobalItemsToShow;

    private String placeholderOpenText, placeholderClosedText;

    private String msgNoPermission, msgNoPermissionOpen, msgReloadSuccess, msgForceOpenSuccess, msgForceCloseSuccess, msgResetPlayerUsage, msgPlayerNotFound, msgResetPlayerSuccess, msgResetPlayerNotification, msgResetAllSuccess, msgResetAllBroadcast, msgNotEnoughMoney, msgItemSuccessfullyPurchased, msgItemAlreadyPurchased, msgItemAlreadyPurchasedDesync, msgItemSoldOut, msgItemOutOfStockPlayer, msgNoPermissionToBuyItem, msgNotEnoughItems;
    private String msgGiveUsage, msgGiveSuccess, msgReceiveRerollItem, msgRerollItemDisabled, msgInvalidAmount;
    private List<String> msgMarketClosed;
    private String msgUpdateNotify;

    private SoundConfig itemPurchaseSound, itemRevealSound, marketOpenSound, marketClosedSound, guiOpenSound, permissionDeniedSound;

    private List<Announcement> beforeOpenAnnouncements;
    private List<Announcement> beforeCloseAnnouncements;

    private String guiTitle;
    private int guiRows;
    private Map<Integer, String> marketSlotToYmlKey;
    private List<DecorativeItemConfig> decorativeItems;

    private String confirmationMenuTitle;
    private int confirmationMenuRows;
    private int confirmationMenuItemDisplaySlot;
    private DecorativeItemConfig confirmationMenuConfirmButton, confirmationMenuCancelButton, confirmationMenuFillItem;

    public enum StockMode {
        GLOBAL, PLAYER
    }

    public record SoundConfig(Sound sound, float volume, float pitch) {}
    public record Announcement(long timeBeforeSeconds, List<String> message) {}
    public static class DecorativeItemConfig {
        public final Material material;
        public final String name;
        public final List<String> lore;
        public final List<Integer> slots;
        public final int customModelData;

        public DecorativeItemConfig(Material material, String name, List<String> lore, List<Integer> slots, int customModelData) {
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.slots = slots;
            this.customModelData = customModelData;
        }
    }

    public ConfigManager(NightMarket plugin) {
        this.plugin = plugin;
        loadGeneralConfig();
        loadGuiConfig();
    }

    private void loadGeneralConfig() {
        generalConfig = plugin.getConfig();

        // Stock System
        try {
            stockMode = StockMode.valueOf(generalConfig.getString("stock_system.mode", "GLOBAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            stockMode = StockMode.GLOBAL;
            plugin.getLogger().warning("Invalid stock system mode in config.yml. Defaulting to GLOBAL.");
        }

        // Item Displays
        globalPlaceholderMaterial = Material.valueOf(generalConfig.getString("global_placeholder_item.material", "ORANGE_STAINED_GLASS_PANE").toUpperCase());
        globalPlaceholderName = ChatColor.translateAlternateColorCodes('&', generalConfig.getString("global_placeholder_item.name", "&7????"));
        globalPlaceholderLore = getColoredList("global_placeholder_item.lore");

        globalPurchasedItemMaterial = Material.valueOf(generalConfig.getString("global_purchased_item_display.material", "RED_STAINED_GLASS_PANE").toUpperCase());
        globalPurchasedItemName = ChatColor.translateAlternateColorCodes('&', generalConfig.getString("global_purchased_item_display.name", "&cSOLD OUT"));
        globalPurchasedItemLore = getColoredList("global_purchased_item_display.lore");

        globalOutOfStockItemMaterial = Material.valueOf(generalConfig.getString("global_out_of_stock_item_display.material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
        globalOutOfStockItemName = ChatColor.translateAlternateColorCodes('&', generalConfig.getString("global_out_of_stock_item_display.name", "&7&lOUT OF STOCK"));
        globalOutOfStockItemLore = getColoredList("global_out_of_stock_item_display.lore");

        // Market Schedule & General
        openDurationSeconds = generalConfig.getLong("market_schedule.open_duration", 604800L);
        closeDurationSeconds = generalConfig.getLong("market_schedule.close_duration", 259200L);
        marketOpenBroadcast = getColoredList("market_schedule.market_open_broadcast");
        marketCloseBroadcast = getColoredList("market_schedule.market_close_broadcast");
        forceOpenMessage = getColoredList("market_schedule.force_open_message");
        forceCloseMessage = getColoredList("market_schedule.force_close_message");
        economyEnabled = generalConfig.getBoolean("economy.enabled", true);
        economyProviderName = generalConfig.getString("economy.provider", "VAULT");
        checkUpdates = generalConfig.getBoolean("check-updates", true);
        numberOfGlobalItemsToShow = generalConfig.getInt("market_settings.number_of_global_items_to_show", 1); // <-- LOAD NEW VALUE

        // Placeholders
        placeholderOpenText = ChatColor.translateAlternateColorCodes('&', generalConfig.getString("placeholders.open_text", "YES"));
        placeholderClosedText = ChatColor.translateAlternateColorCodes('&', generalConfig.getString("placeholders.closed_text", "NO"));

        loadMessages();
        loadSounds();
        loadAnnouncements();
    }

    private List<String> getColoredList(String path) {
        return generalConfig.getStringList(path).stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    private void loadMessages() {
        ConfigurationSection messagesSection = generalConfig.getConfigurationSection("messages");
        if (messagesSection == null) {
            plugin.getLogger().severe("The 'messages' section is missing from your config.yml!");
            messagesSection = generalConfig.createSection("messages");
        }

        final ConfigurationSection finalMessagesSection = messagesSection;
        BiFunction<String, String, String> getString = (path, def) ->
                ChatColor.translateAlternateColorCodes('&', finalMessagesSection.getString(path, def));

        msgNoPermission = getString.apply("no_permission", "&cYou do not have permission.");
        msgNoPermissionOpen = getString.apply("no_permission_open", "&cYou cannot open the Night Market.");
        msgReloadSuccess = getString.apply("reload_success", "&aConfigs reloaded!");
        msgForceOpenSuccess = getString.apply("force_open_success", "&aMarket forcefully opened.");
        msgForceCloseSuccess = getString.apply("force_close_success", "&aMarket forcefully closed.");
        msgResetPlayerUsage = getString.apply("reset_player_usage", "&cUsage: /nm resetplayer <player>");
        msgPlayerNotFound = getString.apply("player_not_found", "&cPlayer '%player%' not found.");
        msgResetPlayerSuccess = getString.apply("reset_player_success", "&aReset data for %player%.");
        msgResetPlayerNotification = getString.apply("reset_player_notification", "&eYour market data was reset.");
        msgResetAllSuccess = getString.apply("reset_all_success", "&aReset all player data.");
        msgResetAllBroadcast = getString.apply("reset_all_broadcast", "&aAll market data has been reset.");
        msgNotEnoughMoney = getString.apply("not_enough_money", "&cYou don't have enough money.");
        msgNotEnoughItems = getString.apply("not_enough_items", "&cYou do not have the required items to purchase this.");
        msgItemSuccessfullyPurchased = getString.apply("item_successfully_purchased", "&aPurchased %item_name% for %price%.");
        msgItemAlreadyPurchased = getString.apply("item_already_purchased", "&eYou already bought this.");
        msgItemOutOfStockPlayer = getString.apply("item_out_of_stock_player", "&cYou have no stock left.");
        msgItemAlreadyPurchasedDesync = getString.apply("item_already_purchased_desync", "&cAlready bought (Desync).");
        msgItemSoldOut = getString.apply("item_sold_out", "&cThis item is sold out globally.");
        msgNoPermissionToBuyItem = getString.apply("no_permission_to_buy_item", "&cYou cannot purchase this item.");
        msgGiveUsage = getString.apply("give_usage", "&cUsage: /nm give <player> <amount>");
        msgGiveSuccess = getString.apply("give_success", "&aGave %amount% reroll item(s) to %player%.");
        msgReceiveRerollItem = getString.apply("receive_reroll_item", "&aYou received %amount% reroll item(s).");
        msgRerollItemDisabled = getString.apply("reroll_item_disabled", "&cReroll item is disabled.");
        msgInvalidAmount = getString.apply("invalid_amount", "&cInvalid amount.");
        msgUpdateNotify = getString.apply("update_notify", "&eA new version is available!");

        permissionDeniedLore = getColoredList("messages.permission_denied_lore");
        msgMarketClosed = getColoredList("messages.market_closed");
    }

    private void loadSounds() {
        itemPurchaseSound = loadSoundConfig("sounds.item_purchase", Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        itemRevealSound = loadSoundConfig("sounds.item_reveal", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        marketOpenSound = loadSoundConfig("sounds.market_open", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        marketClosedSound = loadSoundConfig("sounds.market_closed", Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        guiOpenSound = loadSoundConfig("sounds.gui_open", Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
        permissionDeniedSound = loadSoundConfig("sounds.permission_denied", Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }

    private SoundConfig loadSoundConfig(String path, Sound defaultSound, float defaultVolume, float defaultPitch) {
        String soundName = generalConfig.getString(path + ".sound", defaultSound.name()).toUpperCase();
        float volume = (float) generalConfig.getDouble(path + ".volume", defaultVolume);
        float pitch = (float) generalConfig.getDouble(path + ".pitch", defaultPitch);
        try {
            return new SoundConfig(Sound.valueOf(soundName), volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name '" + soundName + "' at path '" + path + "'. Using default: " + defaultSound.name());
            return new SoundConfig(defaultSound, defaultVolume, defaultPitch);
        }
    }

    private void loadAnnouncements() {
        beforeOpenAnnouncements = new ArrayList<>();
        beforeCloseAnnouncements = new ArrayList<>();

        ConfigurationSection announcementsSection = generalConfig.getConfigurationSection("market_schedule.announcements");
        if (announcementsSection == null) {
            return;
        }

        List<Map<?, ?>> beforeOpenList = announcementsSection.getMapList("before_open");
        if (beforeOpenList != null) {
            for (Map<?, ?> item : beforeOpenList) {
                try {
                    long timeBefore = -1;
                    if (item.get("time_before") instanceof Number) {
                        timeBefore = ((Number) item.get("time_before")).longValue();
                    }

                    List<String> message = new ArrayList<>();
                    if (item.get("message") instanceof List) {
                        message = ((List<?>) item.get("message")).stream()
                                .map(obj -> ChatColor.translateAlternateColorCodes('&', String.valueOf(obj)))
                                .collect(Collectors.toList());
                    }

                    if (timeBefore >= 0 && !message.isEmpty()) {
                        beforeOpenAnnouncements.add(new Announcement(timeBefore, message));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error parsing 'before_open' announcement entry. Entry: " + item);
                }
            }
        }

        List<Map<?, ?>> beforeCloseList = announcementsSection.getMapList("before_close");
        if (beforeCloseList != null) {
            for (Map<?, ?> item : beforeCloseList) {
                try {
                    long timeBefore = -1;
                    if (item.get("time_before") instanceof Number) {
                        timeBefore = ((Number) item.get("time_before")).longValue();
                    }

                    List<String> message = new ArrayList<>();
                    if (item.get("message") instanceof List) {
                        message = ((List<?>) item.get("message")).stream()
                                .map(obj -> ChatColor.translateAlternateColorCodes('&', String.valueOf(obj)))
                                .collect(Collectors.toList());
                    }

                    if (timeBefore >= 0 && !message.isEmpty()) {
                        beforeCloseAnnouncements.add(new Announcement(timeBefore, message));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error parsing 'before_close' announcement entry. Entry: " + item);
                }
            }
        }
    }

    private void loadGuiConfig() {
        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        guiTitle = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("title", "&6&lDefault Night Market"));
        guiRows = guiConfig.getInt("rows", 3);
        if (guiRows < 1 || guiRows > 6) {
            guiRows = 3;
        }

        marketSlotToYmlKey = new HashMap<>();
        ConfigurationSection marketSlotsSection = guiConfig.getConfigurationSection("market_item_slots");
        if (marketSlotsSection != null) {
            for (String key : marketSlotsSection.getKeys(false)) {
                int inventorySlot = marketSlotsSection.getInt(key + ".inventory_slot", -1);
                String itemSourceKey = marketSlotsSection.getString(key + ".item_source_key_from_items_yml");
                if (inventorySlot >= 0 && inventorySlot < guiRows * 9 && itemSourceKey != null && !itemSourceKey.isEmpty()) {
                    marketSlotToYmlKey.put(inventorySlot, itemSourceKey);
                }
            }
        }

        decorativeItems = new ArrayList<>();
        ConfigurationSection decorativeItemsSection = guiConfig.getConfigurationSection("decorative_items");
        if (decorativeItemsSection != null) {
            for (String key : decorativeItemsSection.getKeys(false)) {
                DecorativeItemConfig item = loadDecorativeItem(decorativeItemsSection, key);
                if (item != null) {
                    decorativeItems.add(item);
                }
            }
        }

        ConfigurationSection confirmSection = guiConfig.getConfigurationSection("confirmation_menu");
        if (confirmSection != null) {
            confirmationMenuTitle = ChatColor.translateAlternateColorCodes('&', confirmSection.getString("title", "&8Confirm Purchase"));
            confirmationMenuRows = confirmSection.getInt("rows", 3);
            confirmationMenuItemDisplaySlot = confirmSection.getInt("item_display_slot", 13);
            confirmationMenuConfirmButton = loadDecorativeItem(confirmSection, "confirm_button");
            confirmationMenuCancelButton = loadDecorativeItem(confirmSection, "cancel_button");
            confirmationMenuFillItem = loadDecorativeItem(confirmSection, "fill_item");
        }
    }

    private DecorativeItemConfig loadDecorativeItem(ConfigurationSection section, String key) {
        try {
            Material material = Material.valueOf(section.getString(key + ".material", "STONE").toUpperCase());
            String name = ChatColor.translateAlternateColorCodes('&', section.getString(key + ".name", " "));
            List<String> lore = section.getStringList(key + ".lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            int customModelData = section.getInt(key + ".custom_model_data", 0);
            List<Integer> slots = section.getIntegerList(key + ".slots");
            if (slots.isEmpty() && section.isInt(key + ".slot")) {
                slots.add(section.getInt(key + ".slot"));
            }

            if (!slots.isEmpty()) {
                return new DecorativeItemConfig(material, name, lore, slots, customModelData);
            }
            return null;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for decorative_item '" + key + "' in gui.yml.");
            return null;
        }
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        loadGeneralConfig();
        loadGuiConfig();
        plugin.getItemManager().loadItems();
    }

    public void playSound(Player player, SoundConfig soundConfig) {
        if (soundConfig == null || soundConfig.sound() == null) return;
        player.playSound(player.getLocation(), soundConfig.sound(), soundConfig.volume(), soundConfig.pitch());
    }

    public ItemStack getGlobalPlaceholderItem() { return createDisplayItem(globalPlaceholderMaterial, globalPlaceholderName, globalPlaceholderLore); }
    public ItemStack getGlobalPurchasedItemDisplay() { return createDisplayItem(globalPurchasedItemMaterial, globalPurchasedItemName, globalPurchasedItemLore); }
    public ItemStack getGlobalOutOfStockItemDisplay() { return createDisplayItem(globalOutOfStockItemMaterial, globalOutOfStockItemName, globalOutOfStockItemLore); }

    private ItemStack createDisplayItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public long getOpenDurationSeconds() { return openDurationSeconds; }
    public long getCloseDurationSeconds() { return closeDurationSeconds; }
    public List<String> getMarketOpenBroadcast() { return new ArrayList<>(marketOpenBroadcast); }
    public List<String> getMarketCloseBroadcast() { return new ArrayList<>(marketCloseBroadcast); }
    public List<String> getForceOpenMessage() { return new ArrayList<>(forceOpenMessage); }
    public List<String> getForceCloseMessage() { return new ArrayList<>(forceCloseMessage); }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public String getEconomyProviderName() { return economyProviderName; }
    public boolean isCheckUpdatesEnabled() { return checkUpdates; }
    public StockMode getStockMode() { return stockMode; }
    public int getNumberOfGlobalItemsToShow() { return numberOfGlobalItemsToShow; } // <-- NEW GETTER

    public String getPlaceholderOpenText() { return placeholderOpenText; }
    public String getPlaceholderClosedText() { return placeholderClosedText; }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public Map<Integer, String> getMarketSlotMappings() { return new HashMap<>(marketSlotToYmlKey); }
    public List<DecorativeItemConfig> getDecorativeItems() { return new ArrayList<>(decorativeItems); }
    public String getYmlKeyForGuiSlot(int guiSlot) { return marketSlotToYmlKey.get(guiSlot); }
    public boolean isMarketItemSlot(int guiSlot) { return marketSlotToYmlKey.containsKey(guiSlot); }

    public String getMsgNoPermission() { return msgNoPermission; }
    public String getMsgNoPermissionOpen() { return msgNoPermissionOpen; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgForceOpenSuccess() { return msgForceOpenSuccess; }
    public String getMsgForceCloseSuccess() { return msgForceCloseSuccess; }
    public String getMsgResetPlayerUsage() { return msgResetPlayerUsage; }
    public String getMsgPlayerNotFound(String playerName) { return msgPlayerNotFound.replace("%player%", playerName); }
    public String getMsgResetPlayerSuccess(String playerName) { return msgResetPlayerSuccess.replace("%player%", playerName); }
    public String getMsgResetPlayerNotification() { return msgResetPlayerNotification; }
    public String getMsgResetAllSuccess() { return msgResetAllSuccess; }
    public String getMsgResetAllBroadcast() { return msgResetAllBroadcast; }
    public List<String> getMsgMarketClosed() { return new ArrayList<>(msgMarketClosed); }
    public String getMsgNotEnoughMoney() { return msgNotEnoughMoney; }
    public String getMsgNotEnoughItems() { return msgNotEnoughItems; }
    public String getMsgItemSuccessfullyPurchased(String price, String itemName) { return msgItemSuccessfullyPurchased.replace("%price%", price).replace("%item_name%", itemName); }
    public String getMsgItemAlreadyPurchased() { return msgItemAlreadyPurchased; }
    public String getMsgItemOutOfStockPlayer() { return msgItemOutOfStockPlayer; }
    public String getMsgItemAlreadyPurchasedDesync() { return msgItemAlreadyPurchasedDesync; }
    public String getMsgItemSoldOut() { return msgItemSoldOut; }
    public String getMsgNoPermissionToBuyItem() { return msgNoPermissionToBuyItem; }
    public List<String> getPermissionDeniedLore() { return new ArrayList<>(permissionDeniedLore); }

    public String getMsgUpdateNotify(String currentVersion, String latestVersion) { return msgUpdateNotify.replace("%current_version%", currentVersion).replace("%latest_version%", latestVersion); }

    public String getMsgGiveUsage() { return msgGiveUsage; }
    public String getMsgGiveSuccess(String amount, String playerName) { return msgGiveSuccess.replace("%amount%", amount).replace("%player%", playerName); }
    public String getMsgReceiveRerollItem(String amount) { return msgReceiveRerollItem.replace("%amount%", amount); }
    public String getMsgRerollItemDisabled() { return msgRerollItemDisabled; }
    public String getMsgInvalidAmount() { return msgInvalidAmount; }
    public SoundConfig getItemPurchaseSound() { return itemPurchaseSound; }
    public SoundConfig getItemRevealSound() { return itemRevealSound; }
    public SoundConfig getMarketOpenSound() { return marketOpenSound; }
    public SoundConfig getMarketClosedSound() { return marketClosedSound; }
    public SoundConfig getGuiOpenSound() { return guiOpenSound; }
    public SoundConfig getPermissionDeniedSound() { return permissionDeniedSound; }
    public String getConfirmationMenuTitle() { return confirmationMenuTitle; }
    public int getConfirmationMenuRows() { return confirmationMenuRows; }
    public int getConfirmationMenuItemDisplaySlot() { return confirmationMenuItemDisplaySlot; }
    public DecorativeItemConfig getConfirmationMenuConfirmButton() { return confirmationMenuConfirmButton; }
    public DecorativeItemConfig getConfirmationMenuCancelButton() { return confirmationMenuCancelButton; }
    public DecorativeItemConfig getConfirmationMenuFillItem() { return confirmationMenuFillItem; }

    public List<Announcement> getBeforeOpenAnnouncements() { return new ArrayList<>(beforeOpenAnnouncements); }
    public List<Announcement> getBeforeCloseAnnouncements() { return new ArrayList<>(beforeCloseAnnouncements); }
}