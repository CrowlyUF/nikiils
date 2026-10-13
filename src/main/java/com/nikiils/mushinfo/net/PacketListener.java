package com.nikiils.mushinfo.net;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import com.nikiils.mushinfo.check.PlayerChecks;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Escuta os pacotes de animação que o servidor repassa: cada clique (mesmo no ar) de outro jogador
 * em 1.8 gera um swing, o que permite calcular o CPS dele. Também repassa os blocos colocados.
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
            if (msg instanceof S14PacketEntity || msg instanceof S18PacketEntityTeleport) {
                PlayerChecks.onMovePacket();
            } else if (msg instanceof S23PacketBlockChange) {
                S23PacketBlockChange packet = (S23PacketBlockChange) msg;
                placed(packet.getBlockPosition(), packet.getBlockState());
            } else if (msg instanceof S22PacketMultiBlockChange) {
                for (S22PacketMultiBlockChange.BlockUpdateData data : ((S22PacketMultiBlockChange) msg).getChangedBlocks()) {
                    placed(data.getPos(), data.getBlockState());
                }
            }
            super.channelRead(ctx, msg);
        }

        /** Blocos que viraram algo sólido (colocados), usados pela detecção de Scaffold. */
        private static void placed(BlockPos pos, IBlockState state) {
            if (state != null && state.getBlock().getMaterial() != Material.air && !state.getBlock().getMaterial().isLiquid()) {
                PlayerChecks.INSTANCE.onBlockPlaced(pos, System.currentTimeMillis());
            }
        }
    }
}
