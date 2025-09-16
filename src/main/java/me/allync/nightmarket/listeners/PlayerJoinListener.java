package me.allync.nightmarket.listeners;

import me.allync.nightmarket.NightMarket;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinListener implements Listener {

    private final NightMarket plugin;

    public PlayerJoinListener(NightMarket plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.isOp() && plugin.isUpdateAvailable()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(plugin.getUpdateMessage());
                }
            }.runTaskLater(plugin, 60L);
        }
    }
}
