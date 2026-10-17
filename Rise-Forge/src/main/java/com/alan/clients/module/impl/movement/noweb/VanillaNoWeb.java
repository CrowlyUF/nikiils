package com.alan.clients.module.impl.movement.noweb;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreMotionEvent;
import com.alan.clients.event.impl.motion.StrafeEvent;
import com.alan.clients.module.impl.movement.NoWeb;
import com.alan.clients.value.Mode;

public class VanillaNoWeb extends Mode<NoWeb> {

    public VanillaNoWeb(String name, NoWeb parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<PreMotionEvent> onPreMotionEvent = event -> {
        if (mc.thePlayer != null) {
            mc.thePlayer.isInWeb = false;
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = event -> {
        if (mc.thePlayer != null) {
            mc.thePlayer.isInWeb = false;
        }
    };
}
