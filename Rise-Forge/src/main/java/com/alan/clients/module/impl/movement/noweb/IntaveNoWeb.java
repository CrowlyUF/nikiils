package com.alan.clients.module.impl.movement.noweb;

import com.alan.clients.module.impl.movement.NoWeb;
import com.alan.clients.util.player.MoveUtil;
import com.alan.clients.value.Mode;

public class IntaveNoWeb extends Mode<NoWeb> {

    public IntaveNoWeb(String name, NoWeb parent) {
        super(name, parent);
    }

    public boolean handleEntityCollision() {
        if (mc.thePlayer == null || !MoveUtil.isMoving() || !mc.thePlayer.onGround) {
            return false;
        }

        if (mc.thePlayer.ticksExisted % 3 == 0) {
            MoveUtil.strafe(0.734D);
        } else {
            mc.thePlayer.jump();
            MoveUtil.strafe(0.346D);
        }

        return false;
    }
}
