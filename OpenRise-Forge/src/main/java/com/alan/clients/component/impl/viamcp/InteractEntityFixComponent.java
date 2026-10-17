package com.alan.clients.component.impl.viamcp;

import com.alan.clients.component.Component;
import com.alan.clients.event.Listener;
import com.alan.clients.event.Priorities;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.packet.PacketSendEvent;
import com.alan.clients.util.ProtocolUtil;
import net.minecraft.network.play.client.C02PacketUseEntity;

public final class InteractEntityFixComponent extends Component {

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (!event.isCancelled() && ProtocolUtil.isNewerThan1_8()) {

            if (event.getPacket() instanceof C02PacketUseEntity) {
                C02PacketUseEntity use = ((C02PacketUseEntity) event.getPacket());

                event.setCancelled(event.isCancelled() || !use.getAction().equals(C02PacketUseEntity.Action.ATTACK));
            }
        }
    };

}