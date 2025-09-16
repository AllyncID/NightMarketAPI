package me.allync.nightmarket.listeners;

import me.allync.nightmarket.NightMarket;
import me.allync.nightmarket.manager.ItemManager;
import me.allync.nightmarket.manager.MarketManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener implements Listener {

    private final NightMarket plugin;
    private final ItemManager itemManager;
    private final MarketManager marketManager;

    public PlayerInteractListener(NightMarket plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        this.marketManager = plugin.getMarketManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand != null && itemManager.isRerollItem(itemInHand)) {
            event.setCancelled(true);

            if (!marketManager.isMarketOpen()) {
                return;
            }

            marketManager.rerollMarket(player);

            ItemManager.RerollEffectConfig effects = itemManager.getRerollEffectConfig();
            if (effects != null) {
                player.sendTitle(effects.title(), effects.subtitle(), 10, 70, 20);
                player.playSound(player.getLocation(), effects.sound(), effects.volume(), effects.pitch());
            }

            itemInHand.setAmount(itemInHand.getAmount() - 1);

            if (player.getOpenInventory().getTitle().equals(plugin.getConfigManager().getGuiTitle())) {
                plugin.getNightMarketGUI().openNightMarketGUI(player);
            }
        }
    }
}
