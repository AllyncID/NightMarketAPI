package me.allync.nightmarket;

import me.allync.nightmarket.api.APIManager;
import me.allync.nightmarket.commands.NightMarketCommand;
import me.allync.nightmarket.economy.EconomyManager;
import me.allync.nightmarket.gui.NightMarketGUI;
import me.allync.nightmarket.listeners.InventoryClickListener;
import me.allync.nightmarket.listeners.PlayerInteractListener;
import me.allync.nightmarket.listeners.PlayerJoinListener;
import me.allync.nightmarket.manager.ConfigManager;
import me.allync.nightmarket.manager.ItemManager;
import me.allync.nightmarket.manager.MarketManager;
import me.allync.nightmarket.placeholders.NightMarketExpansion;


import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import me.allync.nightmarket.util.UpdateChecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public final class NightMarket extends JavaPlugin {

    private static NightMarket instance;
    private ConfigManager configManager;
    private ItemManager itemManager;
    private MarketManager marketManager;
    private NightMarketGUI nightMarketGUI;
    private EconomyManager economyManager;
    private APIManager apiManager;

    private boolean updateAvailable = false;
    private String updateMessage = "";

    private final int LATEST_CONFIG_VERSION = 10;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("============================================================");
        getLogger().info("Enabling " + getDescription().getName() + " v" + getDescription().getVersion());
        getLogger().info("Author: allync");
        getLogger().info(" ");

        this.apiManager = new APIManager();

        getLogger().info("Loading configuration files...");
        try {
            saveDefaultConfig();

            updateConfig();

            this.configManager = new ConfigManager(this);
            this.itemManager = new ItemManager(this);
            getLogger().info("Configuration files loaded successfully.");

            if (configManager.isCheckUpdatesEnabled()) {
                new UpdateChecker(this, 127886).getLatestVersion(latestVersion -> {
                    String currentVersion = this.getDescription().getVersion();
                    if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                        this.updateAvailable = true;
                        this.updateMessage = configManager.getMsgUpdateNotify(currentVersion, latestVersion);
                        getLogger().warning(this.updateMessage);
                    } else {
                        getLogger().info("You are running the latest version of " + getDescription().getName() + ".");
                    }
                });
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred while loading configuration files.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.marketManager = new MarketManager(this);
        this.nightMarketGUI = new NightMarketGUI(this);
        this.economyManager = new EconomyManager(this); // EconomyManager is created but not yet hooked.

        // Defer the economy setup to ensure all other plugins (including custom economy providers) have loaded.
        // This runs on the next server tick after all plugins have completed their onEnable() sequence.
        getServer().getScheduler().runTask(this, () -> {
            getLogger().info("Initializing Economy Manager...");
            this.economyManager.setupEconomy();
        });

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NightMarketExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders will not be available.");
        }

        getLogger().info("Registering commands and listeners...");
        PluginCommand nightMarketCmd = getCommand("nightmarket");
        if (nightMarketCmd != null) {
            NightMarketCommand commandHandler = new NightMarketCommand(this);
            nightMarketCmd.setExecutor(commandHandler);
            nightMarketCmd.setTabCompleter(commandHandler);
        } else {
            getLogger().severe("Failed to register /nightmarket command! This is a critical error.");
        }
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getLogger().info("Commands and listeners registered.");


        marketManager.loadMarketState();
        marketManager.startMarketCycle();

        getLogger().info(" ");
        getLogger().info(getDescription().getName() + " has been enabled successfully.");
        getLogger().info("============================================================");
    }

    private void updateConfig() {
        FileConfiguration userConfig = getConfig();
        int currentVersion = userConfig.getInt("config-version", 0);

        if (currentVersion < LATEST_CONFIG_VERSION) {
            getLogger().info("Your config.yml is outdated. Automatically adding new options...");

            InputStream defaultConfigStream = getResource("config.yml");
            if (defaultConfigStream != null) {
                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));

                try (InputStream headerStream = getResource("config.yml");
                     BufferedReader reader = new BufferedReader(new InputStreamReader(headerStream))) {
                    StringBuilder header = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null && (line.trim().startsWith("#") || line.trim().isEmpty())) {
                        header.append(line).append("\n");
                    }
                    userConfig.options().header(header.toString());
                    userConfig.options().copyHeader(true);
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Could not read default config header.", e);
                }

                userConfig.setDefaults(defaultConfig);
                userConfig.options().copyDefaults(true);
            }

            userConfig.set("config-version", LATEST_CONFIG_VERSION);

            saveConfig();

            reloadConfig();

            getLogger().info("Config update complete. Your settings have been preserved.");
        }
    }


    @Override
    public void onDisable() {
        getLogger().info("============================================================");
        getLogger().info("Disabling " + getDescription().getName() + " v" + getDescription().getVersion());
        getLogger().info(" ");

        getLogger().info("Saving market data and stopping tasks...");
        if (marketManager != null) {
            marketManager.saveMarketState();
            marketManager.stopMarketCycle();
            getLogger().info("Market state saved and scheduled tasks have been cancelled.");
        } else {
            getLogger().warning("MarketManager was not initialized, skipping save/stop procedures.");
        }

        getLogger().info(" ");
        getLogger().info(getDescription().getName() + " has been disabled.");
        getLogger().info("============================================================");
    }

    public void reload() {
        // Muat ulang file config dari disk
        reloadConfig();

        if (marketManager != null) {
            marketManager.saveMarketState();
        }

        if (configManager != null) {
            configManager.reloadConfigs();
        }

        if (economyManager != null) {
            getLogger().info("Reloading Economy Manager based on new configuration...");
            economyManager.setupEconomy();
        }
    }

    public boolean isUpdateAvailable() {
        return this.updateAvailable;
    }

    public String getUpdateMessage() {
        return this.updateMessage;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public static NightMarket getInstance() {
        return instance;
    }

    /**
     * Mendapatkan instance dari APIManager.
     * @return APIManager instance.
     */
    public APIManager getApiManager() {
        return apiManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public NightMarketGUI getNightMarketGUI() {
        return nightMarketGUI;
    }
}
