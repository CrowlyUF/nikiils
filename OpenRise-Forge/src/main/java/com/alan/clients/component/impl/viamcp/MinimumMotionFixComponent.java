package com.alan.clients.component.impl.viamcp;

import com.alan.clients.component.Component;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.MinimumMotionEvent;
import com.alan.clients.util.ProtocolUtil;

public final class MinimumMotionFixComponent extends Component {

    @EventLink
    public final Listener<MinimumMotionEvent> onMinimumMotion = event -> {
        if (ProtocolUtil.isNewerThan1_8()) {
            event.setMinimumMotion(0.003D);
        }
    };
}