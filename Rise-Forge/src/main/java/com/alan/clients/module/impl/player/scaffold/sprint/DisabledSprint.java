package com.alan.clients.module.impl.player.scaffold.sprint;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.event.Listener;
import com.alan.clients.event.Priorities;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.StrafeEvent;
import com.alan.clients.module.impl.player.Scaffold;
import com.alan.clients.value.Mode;

public class DisabledSprint extends Mode<Scaffold> {

    public DisabledSprint(String name, Scaffold parent) {
        super(name, parent);
    }

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<StrafeEvent> onPreMotionEvent = event -> {
        MCHooks.setPressed(mc.gameSettings.keyBindSprint, false);
        mc.thePlayer.setSprinting(false);
    };
}
