package com.alan.clients.util.mc;

import com.alan.clients.Client;
import com.alan.clients.event.impl.packet.PacketReceiveEvent;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

/**
 * Backing for Rise's "unregistered" send/receive helpers, which move packets without firing the packet events.
 */
public final class NetworkHooks {

    /** Set while a packet is being sent without a {@code PacketSendEvent}; read by {@code MixinNetworkManager}. */
    public static boolean skipSendEvent;

    private NetworkHooks() {
    }

    public static void sendNoEvent(final NetworkManager networkManager, final Packet<?> packet) {
        if (networkManager == null) return;

        final boolean previous = skipSendEvent;
        skipSendEvent = true;

        try {
            networkManager.sendPacket(packet);
        } finally {
            skipSendEvent = previous;
        }
    }

    @SuppressWarnings("unchecked")
    public static void receive(final NetworkManager networkManager, final Packet<?> packet, final boolean fireEvent) {
        if (networkManager == null || !networkManager.isChannelOpen()) return;

        if (fireEvent) {
            final PacketReceiveEvent event = new PacketReceiveEvent(packet, networkManager);
            Client.INSTANCE.getEventBus().handle(event);

            if (event.isCancelled()) return;
        }

        final INetHandler handler = networkManager.getNetHandler();
        if (handler != null) {
            ((Packet<INetHandler>) packet).processPacket(handler);
        }
    }
}
