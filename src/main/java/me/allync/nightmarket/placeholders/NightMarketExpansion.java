package me.allync.nightmarket.placeholders;

import me.allync.nightmarket.NightMarket;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NightMarketExpansion extends PlaceholderExpansion {

    private final NightMarket plugin;

    public NightMarketExpansion(NightMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "nightmarket";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "Allync" : String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("time")) {
            if (plugin.getMarketManager().isMarketOpen()) {
                return "Closes in: " + plugin.getMarketManager().getFormattedTimeRemaining();
            } else {
                return "Opens in: " + plugin.getMarketManager().getFormattedTimeRemaining();
            }
        }

        if (params.equalsIgnoreCase("open")) {
            return plugin.getMarketManager().isMarketOpen() ? plugin.getConfigManager().getPlaceholderOpenText() : plugin.getConfigManager().getPlaceholderClosedText();
        }

        return null;
    }
}
