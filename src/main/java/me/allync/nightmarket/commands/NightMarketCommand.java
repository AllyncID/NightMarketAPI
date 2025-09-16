package me.allync.nightmarket.commands;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.gui.NightMarketGUI;
import me.allync.nightmarket.manager.ConfigManager;
import me.allync.nightmarket.manager.ItemManager;
import me.allync.nightmarket.manager.MarketManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NightMarketCommand implements CommandExecutor, TabCompleter {

    private final NightMarket plugin;
    private final MarketManager marketManager;
    private final ConfigManager configManager;
    private final ItemManager itemManager;

    public NightMarketCommand(NightMarket plugin) {
        this.plugin = plugin;
        this.marketManager = plugin.getMarketManager();
        this.configManager = plugin.getConfigManager();
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "reload":
                    if (!sender.hasPermission("nightmarket.reload")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    plugin.reload();
                    sender.sendMessage(configManager.getMsgReloadSuccess());
                    return true;

                case "forceopen":
                    if (!sender.hasPermission("nightmarket.admin.forceopen")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    marketManager.forceOpenMarket();
                    sender.sendMessage(configManager.getMsgForceOpenSuccess());
                    return true;

                case "forceclose":
                    if (!sender.hasPermission("nightmarket.admin.forceclose")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    marketManager.forceCloseMarket();
                    sender.sendMessage(configManager.getMsgForceCloseSuccess());
                    return true;

                case "resetplayer":
                    if (!sender.hasPermission("nightmarket.admin.resetplayer")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(configManager.getMsgResetPlayerUsage());
                        return true;
                    }
                    OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(args[1]);
                    if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
                        sender.sendMessage(configManager.getMsgPlayerNotFound(args[1]));
                        return true;
                    }
                    marketManager.resetPlayerData(targetOfflinePlayer.getUniqueId());
                    sender.sendMessage(configManager.getMsgResetPlayerSuccess(targetOfflinePlayer.getName()));
                    if (targetOfflinePlayer.isOnline()) {
                        Player targetPlayer = (Player) targetOfflinePlayer;
                        targetPlayer.sendMessage(configManager.getMsgResetPlayerNotification());

                        if (targetPlayer.getOpenInventory().getTitle().equals(configManager.getGuiTitle())) {
                            plugin.getNightMarketGUI().openNightMarketGUI(targetPlayer);
                        }
                    }
                    return true;

                case "resetall":
                    if (!sender.hasPermission("nightmarket.admin.resetall")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    marketManager.resetAllPlayersData();
                    sender.sendMessage(configManager.getMsgResetAllSuccess());
                    Bukkit.broadcastMessage(configManager.getMsgResetAllBroadcast());
                    return true;

                case "give":
                    if (!sender.hasPermission("nightmarket.command.give")) {
                        sender.sendMessage(configManager.getMsgNoPermission());
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(configManager.getMsgGiveUsage());
                        return true;
                    }
                    if (!itemManager.isRerollItemEnabled()) {
                        sender.sendMessage(configManager.getMsgRerollItemDisabled());
                        return true;
                    }

                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage(configManager.getMsgPlayerNotFound(args[1]));
                        return true;
                    }

                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount <= 0) {
                            sender.sendMessage(configManager.getMsgInvalidAmount());
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(configManager.getMsgInvalidAmount());
                        return true;
                    }

                    ItemStack rerollItem = itemManager.getRerollItem();
                    if (rerollItem == null) {
                        sender.sendMessage(configManager.getMsgRerollItemDisabled());
                        return true;
                    }

                    rerollItem.setAmount(amount);
                    targetPlayer.getInventory().addItem(rerollItem);

                    sender.sendMessage(configManager.getMsgGiveSuccess(String.valueOf(amount), targetPlayer.getName()));
                    targetPlayer.sendMessage(configManager.getMsgReceiveRerollItem(String.valueOf(amount)));
                    return true;
            }
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (player.hasPermission("nightmarket.command.use")) {
                plugin.getNightMarketGUI().openNightMarketGUI(player);
            } else {
                player.sendMessage(configManager.getMsgNoPermissionOpen());
            }
        } else {
            sender.sendMessage(ChatColor.GOLD + "NightMarket Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket reload" + ChatColor.GRAY + " - Reloads configs.");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket forceopen" + ChatColor.GRAY + " - Forces market open.");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket forceclose" + ChatColor.GRAY + " - Forces market close.");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket resetplayer <player>" + ChatColor.GRAY + " - Resets a player's data.");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket resetall" + ChatColor.GRAY + " - Resets all players' data.");
            sender.sendMessage(ChatColor.YELLOW + "/nightmarket give <player> <amount>" + ChatColor.GRAY + " - Gives a player a reroll item.");
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> subCommands = new ArrayList<>();

        if (sender.hasPermission("nightmarket.reload")) subCommands.add("reload");
        if (sender.hasPermission("nightmarket.admin.forceopen")) subCommands.add("forceopen");
        if (sender.hasPermission("nightmarket.admin.forceclose")) subCommands.add("forceclose");
        if (sender.hasPermission("nightmarket.admin.resetplayer")) subCommands.add("resetplayer");
        if (sender.hasPermission("nightmarket.admin.resetall")) subCommands.add("resetall");
        if (sender.hasPermission("nightmarket.command.give")) subCommands.add("give");


        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("resetplayer") && sender.hasPermission("nightmarket.admin.resetplayer")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            } else if (subCommand.equals("give") && sender.hasPermission("nightmarket.command.give")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") && sender.hasPermission("nightmarket.command.give")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "16", "32", "64"), completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
