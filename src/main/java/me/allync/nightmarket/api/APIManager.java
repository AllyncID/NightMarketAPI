package me.allync.nightmarket.api;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.economy.EconomyProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Public API class for NightMarket.
 * Allows other plugins to register a custom economy provider.
 */
public class APIManager {

    private static final Map<String, EconomyProvider> customProviders = new ConcurrentHashMap<>();

    /**
     * Registers a custom EconomyProvider.
     * This method should be called from your plugin's onEnable() method.
     * The registered name must be unique and should not conflict with built-in providers (VAULT, PLAYER_POINTS, etc.).
     *
     * @param name     The unique name for your economy provider (e.g., "GEMS", "SOUL_ESSENCE"). This name will be used in NightMarket's config.yml.
     * @param provider Your implementation of the EconomyProvider interface.
     */
    public static void registerEconomyProvider(String name, EconomyProvider provider) {
        if (name == null || name.trim().isEmpty() || provider == null) {
            NightMarket.getInstance().getLogger().warning("API: EconomyProvider registration failed: Name or provider cannot be null.");
            return;
        }

        String upperCaseName = name.toUpperCase().trim();

        // Check if the name is already used by a built-in provider
        if (isBuiltInProvider(upperCaseName)) {
            NightMarket.getInstance().getLogger().severe("API: EconomyProvider registration for '" + upperCaseName + "' failed. This name is reserved for a built-in provider.");
            return;
        }

        // Check if the name is already used by another custom provider
        if (customProviders.containsKey(upperCaseName)) {
            NightMarket.getInstance().getLogger().severe("API: EconomyProvider registration for '" + upperCaseName + "' failed. This name has already been registered by another plugin.");
            return;
        }

        customProviders.put(upperCaseName, provider);
        NightMarket.getInstance().getLogger().log(Level.INFO, "API: Successfully registered a new custom EconomyProvider: " + upperCaseName);
    }

    /**
     * Gets a registered custom provider by its name.
     * This method is used internally by the EconomyManager.
     *
     * @param name The name of the provider.
     * @return The EconomyProvider instance, or null if not found.
     */
    public static EconomyProvider getProvider(String name) {
        if (name == null) return null;
        return customProviders.get(name.toUpperCase().trim());
    }

    /**
     * Checks if a given provider name is a registered custom provider.
     *
     * @param name The name of the provider.
     * @return true if it's a custom provider, false otherwise.
     */
    public static boolean isCustomProvider(String name) {
        if (name == null) return false;
        return customProviders.containsKey(name.toUpperCase().trim());
    }

    /**
     * Checks if a provider name is one of the built-in providers.
     */
    private static boolean isBuiltInProvider(String name) {
        switch (name) {
            case "VAULT":
            case "PLAYER_POINTS":
            case "PLAYERPOINTS":
            case "TOKEN_MANAGER":
            case "TOKENMANAGER":
            case "COINSENGINE":
                return true;
            default:
                return false;
        }
    }
}

