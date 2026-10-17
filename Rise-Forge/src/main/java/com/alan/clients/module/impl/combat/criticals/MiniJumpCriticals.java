package com.alan.clients.module.impl.combat.criticals;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.other.AttackEvent;
import com.alan.clients.module.impl.combat.Criticals;
import com.alan.clients.value.Mode;

public final class MiniJumpCriticals extends Mode<Criticals> {

    public MiniJumpCriticals(String name, Criticals parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<AttackEvent> onAttack = event -> {
        if (canMiniJump()) {
            mc.thePlayer.motionY = 0.2D;
            mc.thePlayer.onGround = false;
            mc.thePlayer.isAirBorne = true;
            mc.thePlayer.onCriticalHit(event.getTarget());
        }
    };

    private boolean canMiniJump() {
        return mc.thePlayer != null &&
                mc.thePlayer.onGround &&
                !mc.thePlayer.isOnLadder() &&
                !mc.thePlayer.isInWater() &&
                !mc.thePlayer.isInLava() &&
                mc.thePlayer.ridingEntity == null;
    }
}
