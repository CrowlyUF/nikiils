package com.alan.clients.util.mc;

import com.alan.clients.Client;
import com.alan.clients.event.Event;
import com.alan.clients.event.bus.impl.EventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/**
 * Entry point for events fired from mixins. Safe to call before the client has finished loading.
 */
public final class Events {

    private Events() {
    }

    public static <T extends Event> T post(final T event) {
        final EventBus<Event> bus = Client.INSTANCE.getEventBus();

        if (bus != null) {
            bus.handle(event);
        }

        return event;
    }

    public static boolean isLocalPlayer(final Object entity) {
        return entity != null && entity == Minecraft.getMinecraft().thePlayer;
    }

    public static boolean isLocalPlayer(final Entity entity) {
        return entity != null && entity == Minecraft.getMinecraft().thePlayer;
    }
}
