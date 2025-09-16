package me.allync.nightmarket.economy.provider;

import me.allync.nightmarket.economy.EconomyProvider;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;

public class PlayerPointsProvider implements EconomyProvider {

    private final PlayerPointsAPI playerPointsAPI;

    public PlayerPointsProvider() {
        this.playerPointsAPI = JavaPlugin.getPlugin(PlayerPoints.class).getAPI();
    }

    @Override
    public String getName() {
        return "PlayerPoints";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return playerPointsAPI.look(player.getUniqueId());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return playerPointsAPI.look(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return playerPointsAPI.take(player.getUniqueId(), (int) Math.round(amount));
    }

    @Override
    public String format(double amount) {
        return NumberFormat.getInstance().format(Math.round(amount)) + " Points";
    }
}
