package com.infobip.openapi.mcp.openapi.tool;

import org.jspecify.annotations.NullMarked;

/**
 * Controls the pause between successive progress notification ticks while an HTTP API call is in flight.
 *
 * <p>The production implementation sleeps for the configured interval. Tests can inject an alternative
 * to make tick firing deterministic without relying on wall-clock time.
 */
@NullMarked
@FunctionalInterface
interface NotificationTicker {

    /**
     * Blocks until the next tick should fire.
     *
     * @throws InterruptedException if the calling thread is interrupted, signalling the notification
     *                              loop to stop
     */
    void tick() throws InterruptedException;
}
