package com.alan.clients.component.impl.viamcp;

import com.alan.clients.component.Component;
import com.alan.clients.event.Listener;
import com.alan.clients.event.Priorities;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.packet.PacketSendEvent;
import com.alan.clients.util.ProtocolUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public final class FlyingPacketFixComponent extends Component {

    private boolean lastGround;

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (ProtocolUtil.isNewerThan1_8()) {
            final Packet<?> packet = event.getPacket();

            if (packet instanceof C03PacketPlayer) {
                final C03PacketPlayer wrapper = ((C03PacketPlayer) packet);

                if (!wrapper.isMoving() && !wrapper.getRotating() && wrapper.onGround == this.lastGround) {
                    event.setCancelled();
                }

                this.lastGround = wrapper.onGround;
            }
        }
    };
}
