package com.nikiils.mushinfo.net;

import com.nikiils.mushinfo.check.PlayerChecks;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Escuta os pacotes de animação que o servidor repassa: cada clique (mesmo no ar) de outro jogador
 * em 1.8 gera um swing, o que permite calcular o CPS dele.
 */
public class PacketListener {

    private static final String NAME = "mushinfo_listener";

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ChannelPipeline pipeline = event.manager.channel().pipeline();
        if (pipeline.get(NAME) == null && pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", NAME, new Handler());
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        PlayerChecks.INSTANCE.reset();
    }

    private static final class Handler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof S0BPacketAnimation) {
                S0BPacketAnimation packet = (S0BPacketAnimation) msg;
                if (packet.getAnimationType() == 0) {
                    PlayerChecks.INSTANCE.onSwing(packet.getEntityID(), System.currentTimeMillis());
                }
            }
            super.channelRead(ctx, msg);
        }
    }
}
