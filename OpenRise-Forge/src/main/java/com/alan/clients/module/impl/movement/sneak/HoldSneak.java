package com.alan.clients.module.impl.movement.sneak;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreMotionEvent;
import com.alan.clients.module.impl.movement.Sneak;
import com.alan.clients.value.Mode;
import org.lwjgl.input.Keyboard;

public class HoldSneak extends Mode<Sneak> {

    public HoldSneak(String name, Sneak parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<PreMotionEvent> onPreMotionEvent = event -> {
        MCHooks.setPressed(mc.gameSettings.keyBindSneak, true);
    };

    @Override
    public void onDisable() {
        MCHooks.setPressed(mc.gameSettings.keyBindSneak, Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
    }
}