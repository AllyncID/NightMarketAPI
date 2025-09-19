package me.allync.nightmarket.api.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the Night Market closes, either on schedule or by force.
 */
public class MarketCloseEvent extends MarketEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean isForced;

    /**
     * Constructs the MarketCloseEvent.
     * @param isForced true if the market was closed by an admin command, false otherwise.
     */
    public MarketCloseEvent(boolean isForced) {
        this.isForced = isForced;
    }

    /**
     * Checks if the market closing was triggered by an admin command.
     * @return true if forced, false if it was a scheduled closing.
     */
    public boolean isForced() {
        return isForced;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
