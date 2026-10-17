package com.alan.clients.component.impl.viamcp;

import com.alan.clients.component.Component;
import com.alan.clients.component.impl.player.PingSpoofComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreUpdateEvent;
import com.alan.clients.event.impl.other.TeleportEvent;
import com.alan.clients.util.ProtocolUtil;

public final class PostFixComponent extends Component {

    @EventLink()
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (ProtocolUtil.isNewerThan1_8()) {
            PingSpoofComponent.spoof(1, true, true, false, true);
        }
    };

    @EventLink()
    public final Listener<TeleportEvent> onTeleport = event -> {
        if (ProtocolUtil.isNewerThan1_8()) {
            PingSpoofComponent.dispatch();
        }
    };

}