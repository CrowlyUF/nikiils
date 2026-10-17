package com.alan.clients.mixin.network;

import com.alan.clients.event.impl.packet.PacketReceiveEvent;
import com.alan.clients.event.impl.packet.PacketSendEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.NetworkHooks;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet events for the client's own connection. The integrated server's connections use the same class, so events
 * only fire on the clientbound-receiving side (the player's connection).
 */
@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    @Shadow @Final private EnumPacketDirection direction;

    @Shadow
    private void dispatchPacket(final Packet inPacket, final GenericFutureListener<? extends Future<? super Void>>[] futureListeners) {
        throw new AssertionError();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void rise$receive(final ChannelHandlerContext context, final Packet<?> packet, final CallbackInfo ci) {
        if (this.direction != EnumPacketDirection.CLIENTBOUND) return;

        if (Events.post(new PacketReceiveEvent(packet, (NetworkManager) (Object) this)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "dispatchPacket", at = @At("HEAD"), cancellable = true)
    private void rise$send(final Packet<?> packet, final GenericFutureListener<? extends Future<? super Void>>[] listeners, final CallbackInfo ci) {
        if (this.direction != EnumPacketDirection.CLIENTBOUND || NetworkHooks.skipSendEvent) return;

        final PacketSendEvent event = Events.post(new PacketSendEvent(packet, (NetworkManager) (Object) this));

        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.getPacket() != packet) {
            ci.cancel();

            final boolean previous = NetworkHooks.skipSendEvent;
            NetworkHooks.skipSendEvent = true;

            try {
                this.dispatchPacket(event.getPacket(), listeners);
            } finally {
                NetworkHooks.skipSendEvent = previous;
            }
        }
    }
}
