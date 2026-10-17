package com.nikiils.mushinfo.net;

import com.nikiils.mushinfo.check.BlockChecks;
import com.nikiils.mushinfo.check.PlayerChecks;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Lê pacotes antes do jogo: swings (CPS), movimento (Blink), rachaduras e mudanças de bloco
 * (Scaffold, FastMine, Nuker). As mudanças de bloco são agendadas na thread principal antes de o
 * pacote seguir, então rodam antes de o jogo aplicá-las e ainda veem o bloco antigo.
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
            try {
                inspect(msg);
            } catch (Throwable ignored) {
                // nunca derrubar a conexão por causa do mod
            }
            super.channelRead(ctx, msg);
        }

        private static void inspect(Object msg) {
            if (msg instanceof S0BPacketAnimation) {
                S0BPacketAnimation packet = (S0BPacketAnimation) msg;
                if (packet.getAnimationType() == 0) {
                    PlayerChecks.INSTANCE.onSwing(packet.getEntityID(), System.currentTimeMillis());
                }
            } else if (msg instanceof S14PacketEntity || msg instanceof S18PacketEntityTeleport) {
                PlayerChecks.onMovePacket();
            } else if (msg instanceof S23PacketBlockChange) {
                final S23PacketBlockChange p = (S23PacketBlockChange) msg;
                final BlockPos pos = p.getBlockPosition();
                final IBlockState state = p.getBlockState();
                schedule(() -> blocks().onBlockChange(pos, state));
            } else if (msg instanceof S22PacketMultiBlockChange) {
                final S22PacketMultiBlockChange.BlockUpdateData[] changes = ((S22PacketMultiBlockChange) msg).getChangedBlocks();
                schedule(() -> {
                    for (S22PacketMultiBlockChange.BlockUpdateData d : changes) {
                        blocks().onBlockChange(d.getPos(), d.getBlockState());
                    }
                });
            } else if (msg instanceof S25PacketBlockBreakAnim) {
                final S25PacketBlockBreakAnim p = (S25PacketBlockBreakAnim) msg;
                final int breaker = p.getBreakerId();
                final BlockPos pos = p.getPosition();
                final int progress = p.getProgress();
                schedule(() -> blocks().onBreakAnim(breaker, pos, progress));
            }
        }

        private static BlockChecks blocks() {
            return PlayerChecks.INSTANCE.blocks();
        }

        private static void schedule(Runnable r) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    r.run();
                } catch (Throwable ignored) {
                    // idem
                }
            });
        }
    }
}
