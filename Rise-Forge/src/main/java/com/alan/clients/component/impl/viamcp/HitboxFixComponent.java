package com.alan.clients.component.impl.viamcp;

import com.alan.clients.component.Component;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.render.MouseOverEvent;
import com.alan.clients.util.ProtocolUtil;

public final class HitboxFixComponent extends Component {

    @EventLink
    public final Listener<MouseOverEvent> onMouseOver = event -> {
        if (ProtocolUtil.isNewerThan1_8()) {
//            event.setExpand(event.getExpand() - 0.1F);
        }
    };
}
