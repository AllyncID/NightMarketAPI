package me.allync.nightmarket.economy.provider;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.economy.EconomyProvider;
import me.allync.nightmarket.manager.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ItemEconomyProvider implements EconomyProvider {

    private final NightMarket plugin;
    private static ItemManager.ConfiguredItem currentItemForTransaction;

    public ItemEconomyProvider(NightMarket plugin) {
        this.plugin = plugin;
    }

    public static void setCurrentItemForTransaction(ItemManager.ConfiguredItem item) {
        currentItemForTransaction = item;
    }

    @Override
    public String getName() {
        return "ITEM";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return has(player, 0) ? 1 : 0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null || currentItemForTransaction == null || currentItemForTransaction.getRequiredItems().isEmpty()) {
            return false;
        }

        for (ItemManager.RequiredItem requiredItem : currentItemForTransaction.getRequiredItems()) {
            int totalAmountFound = 0;
            for (ItemStack inventoryItem : onlinePlayer.getInventory().getContents()) {
                if (isMatchingItem(inventoryItem, requiredItem)) {
                    totalAmountFound += inventoryItem.getAmount();
                }
            }
            if (totalAmountFound < requiredItem.getAmount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null || currentItemForTransaction == null || !has(player, 0)) {
            return false;
        }

        for (ItemManager.RequiredItem requiredItem : currentItemForTransaction.getRequiredItems()) {
            int amountToRemove = requiredItem.getAmount();
            ItemStack[] contents = onlinePlayer.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack inventoryItem = contents[i];
                if (isMatchingItem(inventoryItem, requiredItem)) {
                    int amountInSlot = inventoryItem.getAmount();
                    if (amountToRemove >= amountInSlot) {
                        amountToRemove -= amountInSlot;
                        onlinePlayer.getInventory().setItem(i, null);
                    } else {
                        inventoryItem.setAmount(amountInSlot - amountToRemove);
                        amountToRemove = 0;
                    }
                }
                if (amountToRemove == 0) {
                    break;
                }
            }
        }
        onlinePlayer.updateInventory();
        return true;
    }

    @Override
    public String format(double amount) {
        if (currentItemForTransaction != null && !currentItemForTransaction.getRequiredItems().isEmpty()) {
            return currentItemForTransaction.getRequiredItems().stream()
                    .map(item -> item.getAmount() + "x " + (item.getName() != null ? item.getName() : item.getMaterial().toString()))
                    .collect(Collectors.joining(", "));
        }
        return "Item";
    }

    private boolean isMatchingItem(ItemStack inventoryItem, ItemManager.RequiredItem requiredItem) {
        if (inventoryItem == null || inventoryItem.getType() != requiredItem.getMaterial()) {
            return false;
        }

        ItemMeta meta = inventoryItem.getItemMeta();
        boolean nameMatches = true;
        boolean loreMatches = true;

        if (requiredItem.getName() != null) {
            nameMatches = meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(requiredItem.getName());
        } else {
            nameMatches = meta == null || !meta.hasDisplayName();
        }

        if (requiredItem.getLore() != null && !requiredItem.getLore().isEmpty()) {
            loreMatches = meta != null && meta.hasLore() && meta.getLore().equals(requiredItem.getLore());
        } else {
            loreMatches = meta == null || !meta.hasLore();
        }

        return nameMatches && loreMatches;
    }
}
