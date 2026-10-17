package com.alan.clients.module.impl.combat.criticals;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.other.AttackEvent;
import com.alan.clients.module.impl.combat.Criticals;
import com.alan.clients.value.Mode;

public final class JumpCriticals extends Mode<Criticals> {

    public JumpCriticals(String name, Criticals parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<AttackEvent> onAttack = event -> {
        if (canJump()) {
            mc.thePlayer.jump();
            mc.thePlayer.onCriticalHit(event.getTarget());
        }
    };

    private boolean canJump() {
        return mc.thePlayer != null &&
                mc.thePlayer.onGround &&
                !mc.thePlayer.isOnLadder() &&
                !mc.thePlayer.isInWater() &&
                !mc.thePlayer.isInLava() &&
                mc.thePlayer.ridingEntity == null;
    }
}
