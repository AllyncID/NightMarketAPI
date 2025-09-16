package me.allync.nightmarket.economy.provider;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.economy.EconomyProvider;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;
import org.bukkit.OfflinePlayer;

import java.text.NumberFormat;
import java.util.Locale;

public class CoinsEngineProvider implements EconomyProvider {

    private final Currency currency;

    public CoinsEngineProvider() {
        String currencyId = NightMarket.getInstance().getConfig().getString("economy.coinsengine_currency_id", "coins");
        this.currency = CoinsEngineAPI.getCurrency(currencyId);

        if (this.currency == null) {
            throw new IllegalStateException("Mata uang CoinsEngine '" + currencyId + "' tidak ditemukan! Harap periksa ID mata uang di config.yml NightMarket.");
        }
    }

    @Override
    public String getName() {
        return "CoinsEngine";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (currency == null) {
            return 0.0;
        }
        return CoinsEngineAPI.getBalance(player.getUniqueId(), currency);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (currency == null) {
            return false;
        }
        if (has(player, amount)) {
            CoinsEngineAPI.removeBalance(player.getUniqueId(), currency, amount);
            return true;
        }
        return false;
    }

    @Override
    public String format(double amount) {
        if (currency != null && currency.getSymbol() != null && !currency.getSymbol().isEmpty()) {
            return String.format(Locale.US, "%,.0f%s", amount, currency.getSymbol());
        }
        return NumberFormat.getInstance(Locale.US).format(amount);
    }
}

