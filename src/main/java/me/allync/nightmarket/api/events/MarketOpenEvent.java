package me.allync.nightmarket.api.events;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the Night Market opens, either on schedule or by force.
 */
public class MarketOpenEvent extends MarketEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean isForced;

    /**
     * Constructs the MarketOpenEvent.
     * @param isForced true if the market was opened by an admin command, false otherwise.
     */
    public MarketOpenEvent(boolean isForced) {
        this.isForced = isForced;
    }

    /**
     * Checks if the market opening was triggered by an admin command.
     * @return true if forced, false if it was a scheduled opening.
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
