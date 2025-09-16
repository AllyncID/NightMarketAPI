package me.allync.nightmarket.manager;

import me.allync.nightmarket.NightMarket;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ItemManager {

    private final NightMarket plugin;
    private final List<ConfiguredItem> allPossibleItems = new ArrayList<>();
    private final NavigableMap<Double, ConfiguredItem> weightedItems = new TreeMap<>();
    private double totalWeight = 0.0;
    private final Random random = new Random();

    private boolean rerollItemEnabled;
    private ItemStack rerollItem;
    private RerollEffectConfig rerollEffectConfig;

    private PricePlaceholderConfig pricePlaceholderConfig;


    public ItemManager(NightMarket plugin) {
        this.plugin = plugin;
        loadItems();
    }

    public void loadItems() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        FileConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        allPossibleItems.clear();
        weightedItems.clear();
        totalWeight = 0.0;
        rerollItem = null;
        rerollEffectConfig = null;
        rerollItemEnabled = false;

        loadPricePlaceholders(itemsConfig);
        loadRerollItem(itemsConfig);

        ConfigurationSection itemsSection = itemsConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("The 'items' section is missing from items.yml. The Night Market will be empty.");
            return;
        }

        for (String itemKey : itemsSection.getKeys(false)) {
            ConfigurationSection itemDetails = itemsSection.getConfigurationSection(itemKey);
            if (itemDetails != null) {
                try {
                    double chance = itemDetails.getDouble("chance", 0.0);
                    if (chance <= 0) {
                        plugin.getLogger().warning("Item '" + itemKey + "' has a chance of 0 or less and will be skipped.");
                        continue;
                    }

                    boolean isGlobal = itemDetails.getBoolean("global", false); // <-- LOAD NEW VALUE

                    double price = itemDetails.getDouble("price", 0.0);
                    List<RequiredItem> requiredItems = new ArrayList<>();

                    if (itemDetails.isList("required_items")) {
                        List<Map<?, ?>> requiredItemsList = itemDetails.getMapList("required_items");
                        for (Map<?, ?> itemMap : requiredItemsList) {
                            Material material = Material.valueOf(((String) itemMap.get("material")).toUpperCase());
                            int amount = (int) itemMap.get("amount");
                            String name = itemMap.containsKey("name") ? ChatColor.translateAlternateColorCodes('&', (String) itemMap.get("name")) : null;
                            List<String> lore = itemMap.containsKey("lore") ? ((List<?>) itemMap.get("lore")).stream().map(l -> ChatColor.translateAlternateColorCodes('&', String.valueOf(l))).collect(Collectors.toList()) : null;
                            requiredItems.add(new RequiredItem(material, amount, name, lore));
                        }
                    }

                    int stock = itemDetails.getInt("stock", -1);
                    double discountChance = itemDetails.getDouble("discount.chance", 0.0);
                    double discountPercentage = itemDetails.getDouble("discount.percentage", 0.0);

                    String permissionNode = itemDetails.getString("permission.node");
                    boolean invertPermission = itemDetails.getBoolean("permission.invert", false);

                    String materialString = itemDetails.getString("display_item.material", "STONE").toUpperCase();
                    Material material;
                    int customModelData = itemDetails.getInt("display_item.custom_model_data", 0);

                    if (materialString.contains(":")) {
                        String[] parts = materialString.split(":");
                        material = Material.valueOf(parts[0]);
                        if (parts.length > 1) {
                            try {
                                customModelData = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("Invalid custom model data for item '" + itemKey + "': " + parts[1]);
                            }
                        }
                    } else {
                        material = Material.valueOf(materialString);
                    }

                    String name = ChatColor.translateAlternateColorCodes('&', itemDetails.getString("display_item.name", "&fDefault Item"));

                    List<String> lore = itemDetails.getStringList("display_item.lore").stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());

                    ItemStack displayItem = new ItemStack(material);
                    ItemMeta meta = displayItem.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(name);
                        meta.setLore(lore);
                        if (customModelData != 0) {
                            meta.setCustomModelData(customModelData);
                        }

                        ConfigurationSection enchantmentsSection = itemDetails.getConfigurationSection("display_item.enchantments");
                        if (enchantmentsSection != null) {
                            for (String enchKey : enchantmentsSection.getKeys(false)) {
                                Enchantment enchantment = Enchantment.getByName(enchKey.toUpperCase());
                                if (enchantment != null) {
                                    meta.addEnchant(enchantment, enchantmentsSection.getInt(enchKey), true);
                                } else {
                                    plugin.getLogger().warning("Invalid enchantment '" + enchKey + "' for item '" + itemKey + "'.");
                                }
                            }
                        }
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
                        displayItem.setItemMeta(meta);
                    }

                    List<String> commands = itemDetails.getStringList("commands_on_click");
                    ConfiguredItem configuredItem = new ConfiguredItem(itemKey, displayItem, commands, price, requiredItems, chance, stock, discountChance, discountPercentage, permissionNode, invertPermission, isGlobal);
                    allPossibleItems.add(configuredItem);

                    totalWeight += chance;
                    weightedItems.put(totalWeight, configuredItem);

                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load item '" + itemKey + "' from items.yml. Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        if (allPossibleItems.isEmpty()) {
            plugin.getLogger().warning("No valid items loaded from items.yml. The Night Market will be empty.");
        } else {
            plugin.getLogger().info("Loaded " + allPossibleItems.size() + " possible items from items.yml.");
        }
    }

    private void loadPricePlaceholders(FileConfiguration itemsConfig) {
        String normalFormat = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("price-placeholders.normal", "&ePrice: &6%final_price%"));
        String discountedFormat = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("price-placeholders.discounted", "&ePrice: &m%original_price%&r &a%final_price%"));
        this.pricePlaceholderConfig = new PricePlaceholderConfig(normalFormat, discountedFormat);
    }

    private void loadRerollItem(FileConfiguration itemsConfig) {
        ConfigurationSection rerollSection = itemsConfig.getConfigurationSection("reroll-item");
        if (rerollSection == null) {
            this.rerollItemEnabled = false;
            return;
        }

        this.rerollItemEnabled = rerollSection.getBoolean("enabled", false);
        if (!this.rerollItemEnabled) {
            return;
        }

        try {
            String materialString = rerollSection.getString("display.material", "STONE").toUpperCase();
            Material material;
            int customModelData = rerollSection.getInt("display.custom_model_data", 0);

            if (materialString.contains(":")) {
                String[] parts = materialString.split(":");
                material = Material.valueOf(parts[0]);
                if (parts.length > 1) {
                    try {
                        customModelData = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid custom model data for reroll-item: " + parts[1]);
                    }
                }
            } else {
                material = Material.valueOf(materialString);
            }

            String name = ChatColor.translateAlternateColorCodes('&', rerollSection.getString("display.name", "&cReroll Item"));
            List<String> lore = rerollSection.getStringList("display.lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            boolean enchanted = rerollSection.getBoolean("display.enchanted", false);

            this.rerollItem = new ItemStack(material);
            ItemMeta meta = this.rerollItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                if (customModelData != 0) meta.setCustomModelData(customModelData);
                if (enchanted) {
                    meta.addEnchant(Enchantment.LURE, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
                this.rerollItem.setItemMeta(meta);
            }

            String title = ChatColor.translateAlternateColorCodes('&', rerollSection.getString("success_effects.title", ""));
            String subtitle = ChatColor.translateAlternateColorCodes('&', rerollSection.getString("success_effects.subtitle", ""));
            Sound sound = Sound.valueOf(rerollSection.getString("success_effects.sound.name", "UI_BUTTON_CLICK").toUpperCase());
            float volume = (float) rerollSection.getDouble("success_effects.sound.volume", 1.0);
            float pitch = (float) rerollSection.getDouble("success_effects.sound.pitch", 1.0);

            this.rerollEffectConfig = new RerollEffectConfig(title, subtitle, sound, volume, pitch);

        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Failed to load the Reroll item due to an invalid material or sound name in items.yml. The feature will be disabled.");
            this.rerollItemEnabled = false;
        }
    }

    /**
     * Gets a random weighted item from a specific list of items.
     * @param items The list of items to choose from.
     * @return A random ConfiguredItem or null if the list is empty.
     */
    public ConfiguredItem getRandomWeightedItemFromList(List<ConfiguredItem> items) {
        if (items == null || items.isEmpty()) return null;

        NavigableMap<Double, ConfiguredItem> tempWeightedMap = new TreeMap<>();
        double tempTotalWeight = 0.0;

        for (ConfiguredItem item : items) {
            tempTotalWeight += item.getChance();
            tempWeightedMap.put(tempTotalWeight, item);
        }

        if (tempTotalWeight == 0) return null;

        double randomValue = random.nextDouble() * tempTotalWeight;
        Map.Entry<Double, ConfiguredItem> entry = tempWeightedMap.higherEntry(randomValue);
        return (entry != null) ? entry.getValue() : null;
    }


    /**
     * Gets a random weighted item from the entire list of possible items.
     * @return A random ConfiguredItem or null if the list is empty.
     */
    public ConfiguredItem getRandomWeightedItem() {
        if (weightedItems.isEmpty()) return null;
        double randomValue = random.nextDouble() * totalWeight;
        Map.Entry<Double, ConfiguredItem> entry = weightedItems.higherEntry(randomValue);
        return (entry != null) ? entry.getValue() : null;
    }


    public List<ConfiguredItem> getAllPossibleItems() {
        return new ArrayList<>(allPossibleItems);
    }

    public boolean isRerollItem(ItemStack itemStack) {
        if (!rerollItemEnabled || rerollItem == null || itemStack == null) return false;
        return rerollItem.isSimilar(itemStack);
    }

    public ConfiguredItem getItemByKey(String key) {
        if (key == null) return null;
        return allPossibleItems.stream().filter(item -> key.equals(item.getKey())).findFirst().orElse(null);
    }

    public boolean isRerollItemEnabled() { return rerollItemEnabled; }
    public ItemStack getRerollItem() { return rerollItem != null ? rerollItem.clone() : null; }
    public RerollEffectConfig getRerollEffectConfig() { return rerollEffectConfig; }
    public PricePlaceholderConfig getPricePlaceholderConfig() { return pricePlaceholderConfig; }

    public record RerollEffectConfig(String title, String subtitle, Sound sound, float volume, float pitch) {}
    public record PricePlaceholderConfig(String normal, String discounted) {}

    public static class PlayerMarketItem {
        private final ConfiguredItem baseItem;
        private final boolean isDiscounted;
        private final double finalPrice;

        public PlayerMarketItem(ConfiguredItem baseItem, Random random) {
            this.baseItem = baseItem;
            if (baseItem.getDiscountChance() > 0 && random.nextDouble() * 100 < baseItem.getDiscountChance()) {
                this.isDiscounted = true;
                this.finalPrice = baseItem.getPrice() * (1 - (baseItem.getDiscountPercentage() / 100.0));
            } else {
                this.isDiscounted = false;
                this.finalPrice = baseItem.getPrice();
            }
        }

        public PlayerMarketItem(ConfiguredItem baseItem, boolean isDiscounted, double finalPrice) {
            this.baseItem = baseItem;
            this.isDiscounted = isDiscounted;
            this.finalPrice = finalPrice;
        }

        public ConfiguredItem getBaseItem() { return baseItem; }
        public boolean isDiscounted() { return isDiscounted; }
        public double getFinalPrice() { return finalPrice; }
    }

    public static class RequiredItem {
        private final Material material;
        private final int amount;
        private final String name;
        private final List<String> lore;

        public RequiredItem(Material material, int amount, String name, List<String> lore) {
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.lore = lore;
        }

        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }

    public static class ConfiguredItem {
        private final String key;
        private final ItemStack displayItem;
        private final List<String> commandsOnClick;
        private final double price;
        private final List<RequiredItem> requiredItems;
        private final double chance;
        private final int initialStock;
        private final double discountChance;
        private final double discountPercentage;
        private final String permissionNode;
        private final boolean invertPermission;
        private final boolean isGlobal;

        public ConfiguredItem(String key, ItemStack displayItem, List<String> commandsOnClick, double price, List<RequiredItem> requiredItems, double chance, int initialStock, double discountChance, double discountPercentage, String permissionNode, boolean invertPermission, boolean isGlobal) {
            this.key = key;
            this.displayItem = displayItem;
            this.commandsOnClick = commandsOnClick;
            this.price = price;
            this.requiredItems = requiredItems;
            this.chance = chance;
            this.initialStock = initialStock;
            this.discountChance = discountChance;
            this.discountPercentage = discountPercentage;
            this.permissionNode = permissionNode;
            this.invertPermission = invertPermission;
            this.isGlobal = isGlobal;
        }

        public boolean hasItemPrice() {
            return this.requiredItems != null && !this.requiredItems.isEmpty();
        }

        public boolean hasPermission(Player player) {
            if (permissionNode == null || permissionNode.isEmpty()) {
                return true; // No permission required
            }
            boolean hasPerm = player.hasPermission(permissionNode);
            return invertPermission ? !hasPerm : hasPerm;
        }

        public String getKey() { return key; }
        public ItemStack getDisplayItem() { return displayItem.clone(); }
        public List<String> getCommandsOnClick() { return new ArrayList<>(commandsOnClick); }
        public double getPrice() { return price; }
        public List<RequiredItem> getRequiredItems() { return requiredItems; }
        public double getChance() { return chance; }
        public int getInitialStock() { return initialStock; }
        public double getDiscountChance() { return discountChance; }
        public double getDiscountPercentage() { return discountPercentage; }
        public String getPermissionNode() { return permissionNode; }
        public boolean isPermissionInverted() { return invertPermission; }
        public boolean isGlobal() { return isGlobal; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConfiguredItem that = (ConfiguredItem) o;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }
}