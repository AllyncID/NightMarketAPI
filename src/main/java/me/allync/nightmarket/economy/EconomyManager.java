package me.allync.nightmarket.economy;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.api.APIManager;
import me.allync.nightmarket.economy.provider.CoinsEngineProvider;
import me.allync.nightmarket.economy.provider.ItemEconomyProvider;
import me.allync.nightmarket.economy.provider.PlayerPointsProvider;
import me.allync.nightmarket.economy.provider.TokenManagerProvider;
import me.allync.nightmarket.economy.provider.VaultProvider;
import org.bukkit.Bukkit;

import java.util.logging.Level;

public class EconomyManager {

    private final NightMarket plugin;
    private EconomyProvider economyProvider;
    private final ItemEconomyProvider itemEconomyProvider;

    public EconomyManager(NightMarket plugin) {
        this.plugin = plugin;
        this.itemEconomyProvider = new ItemEconomyProvider(plugin);
        // The call to setupEconomy() has been moved to the main plugin's onEnable,
        // to ensure it runs after all other plugins have loaded.
    }

    public void setupEconomy() {
        this.economyProvider = null;

        if (!plugin.getConfigManager().isEconomyEnabled()) {
            plugin.getLogger().info("Economy features are disabled in config.yml. All items will be free.");
            return;
        }

        String providerName = plugin.getConfigManager().getEconomyProviderName().toUpperCase();
        if (providerName.isEmpty()) {
            plugin.getLogger().severe("The 'economy.provider' setting is empty in config.yml! Economy features will be disabled.");
            return;
        }

        plugin.getLogger().info("Attempting to hook into economy provider: " + providerName);


        if (APIManager.isCustomProvider(providerName)) {
            this.economyProvider = APIManager.getProvider(providerName);
            if (this.economyProvider != null) {
                plugin.getLogger().info("Successfully hooked into custom economy provider: " + this.economyProvider.getName() + " (Registered as '" + providerName + "').");
            } else {
                plugin.getLogger().severe("Failed to retrieve custom provider '" + providerName + "' even though it was registered.");
            }
            return;
        }


        switch (providerName) {
            case "VAULT":
                if (isPluginAvailable("Vault")) {
                    try {
                        this.economyProvider = new VaultProvider();
                        plugin.getLogger().info("Successfully hooked into Vault.");
                    } catch (IllegalStateException e) {
                        plugin.getLogger().log(Level.SEVERE, "Vault is installed, but failed to register its service. Is an economy plugin installed alongside Vault?");
                    }
                } else {
                    logProviderNotFound(providerName);
                }
                break;

            case "PLAYER_POINTS":
            case "PLAYERPOINTS":
                if (isPluginAvailable("PlayerPoints")) {
                    this.economyProvider = new PlayerPointsProvider();
                    plugin.getLogger().info("Successfully hooked into PlayerPoints.");
                } else {
                    logProviderNotFound("PlayerPoints");
                }
                break;

            case "TOKEN_MANAGER":
            case "TOKENMANAGER":
                if (isPluginAvailable("TokenManager")) {
                    this.economyProvider = new TokenManagerProvider();
                    plugin.getLogger().info("Successfully hooked into TokenManager.");
                } else {
                    logProviderNotFound("TokenManager");
                }
                break;

            case "COINSENGINE":
                if (isPluginAvailable("CoinsEngine")) {
                    try {
                        this.economyProvider = new CoinsEngineProvider();
                        plugin.getLogger().info("Successfully hooked into CoinsEngine.");
                    } catch (IllegalStateException e) {
                        plugin.getLogger().log(Level.SEVERE, "CoinsEngine is installed, but an error occurred during hook: " + e.getMessage());
                    }
                } else {
                    logProviderNotFound("CoinsEngine");
                }
                break;

            default:
                plugin.getLogger().severe("Invalid or unregistered economy provider specified in config.yml: '" + providerName + "'.");
                plugin.getLogger().severe("Valid options are: VAULT, PLAYER_POINTS, TOKEN_MANAGER, COINSENGINE, or any custom registered provider.");
                plugin.getLogger().severe("Economy features will be disabled.");
                break;
        }

        if (this.economyProvider == null) {
            plugin.getLogger().warning("No primary economy provider was hooked. Only item-based trades will be available.");
        }
    }

    private boolean isPluginAvailable(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    private void logProviderNotFound(String providerName) {
        plugin.getLogger().severe("The economy provider specified in config.yml is '" + providerName + "', but the plugin was not found.");
        plugin.getLogger().severe("Please install " + providerName + " or change the configuration.");
        plugin.getLogger().severe("Economy features will be disabled.");
    }

    public EconomyProvider getProvider() {
        return economyProvider;
    }

    public ItemEconomyProvider getItemEconomyProvider() {
        return itemEconomyProvider;
    }

    public boolean isEconomyHooked() {
        return economyProvider != null;
    }
}
