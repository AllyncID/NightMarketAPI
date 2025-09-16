package me.allync.nightmarket.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {
    String getName();
    double getBalance(OfflinePlayer player);
    boolean has(OfflinePlayer player, double amount);
    boolean withdraw(OfflinePlayer player, double amount);
    String format(double amount);
}
