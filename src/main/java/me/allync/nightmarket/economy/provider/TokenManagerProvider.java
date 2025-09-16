package me.allync.nightmarket.economy.provider;

import me.allync.nightmarket.economy.EconomyProvider;
import me.realized.tokenmanager.api.TokenManager; // The API interface from the user's code
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.NumberFormat;

public class TokenManagerProvider implements EconomyProvider {

    private final TokenManager tokenManagerApi;

    public TokenManagerProvider() {
        Plugin tokenManagerPlugin = Bukkit.getPluginManager().getPlugin("TokenManager");
        if (tokenManagerPlugin == null) {
            throw new IllegalStateException("TokenManager plugin was not found on the server!");
        }

        if (!(tokenManagerPlugin instanceof TokenManager)) {
            throw new IllegalStateException("TokenManager plugin was found, but it does not seem to be a valid API implementation.");
        }
        this.tokenManagerApi = (TokenManager) tokenManagerPlugin;
    }

    @Override
    public String getName() {
        return "TokenManager";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return 0;
        }
        return tokenManagerApi.getTokens(onlinePlayer).orElse(0L);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return false;
        }
        long amountToWithdraw = (long) Math.round(amount);
        return tokenManagerApi.removeTokens(onlinePlayer, amountToWithdraw);
    }

    @Override
    public String format(double amount) {
        return NumberFormat.getInstance().format(Math.round(amount)) + " Tokens";
    }
}