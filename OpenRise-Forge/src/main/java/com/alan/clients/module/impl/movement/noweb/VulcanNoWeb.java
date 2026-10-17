package com.alan.clients.module.impl.movement.noweb;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.input.MoveInputEvent;
import com.alan.clients.event.impl.motion.PreMotionEvent;
import com.alan.clients.event.impl.motion.StrafeEvent;
import com.alan.clients.module.impl.movement.NoWeb;
import com.alan.clients.util.player.MoveUtil;
import com.alan.clients.value.Mode;
import net.minecraft.block.BlockWeb;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

public class VulcanNoWeb extends Mode<NoWeb> {

    private static final double STRAFE_BYPASS_SPEED = 4.90D / 20.0D;
    private static final double MIN_ASCEND_SPEED = 0.36D;
    private static final double MAX_ASCEND_SPEED = 0.40D;
    private static final double TARGET_EPSILON = 0.015D;
    private static final double WEB_SURFACE_OFFSET = 1.1D;
    private static final int WEB_KEEP_ALIVE_TICKS = 4;

    private float strafeForward;
    private float strafeSide;
    private boolean hasStrafeInput;
    private boolean active;
    private boolean lifted;
    private double floorY;
    private double ascendSpeed;
    private int keepAliveTicks;

    public VulcanNoWeb(String name, NoWeb parent) {
        super(name, parent);
    }

    public boolean handleEntityCollision(BlockPos pos) {
        if (mc.thePlayer != null && mc.theWorld != null) {
            arm(targetYFromWebStack(pos));
        }

        return true;
    }

    @EventLink
    public final Listener<PreMotionEvent> onPreMotionEvent = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }

        if (!active) {
            return;
        }

        final Double supportY = findSupportTarget();

        if (supportY != null) {
            arm(supportY);
        } else if (--keepAliveTicks <= 0) {
            reset();
            return;
        }

        tickActive(event);
    };

    @EventLink
    public final Listener<MoveInputEvent> onMoveInput = event -> {
        if (!active) {
            hasStrafeInput = false;
            return;
        }

        strafeForward = event.getForward();
        strafeSide = event.getStrafe();
        hasStrafeInput = true;
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
        event.setSneak(false);
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = event -> {
        if (mc.thePlayer == null || mc.theWorld == null || !active) {
            return;
        }

        final float forward = hasStrafeInput ? strafeForward : event.getForward();
        final float strafe = hasStrafeInput ? strafeSide : event.getStrafe();

        if (forward == 0.0F && strafe == 0.0F) {
            MoveUtil.stop();
            event.setCancelled();
            return;
        }

        mc.thePlayer.isInWeb = false;
        event.setForward(forward);
        event.setStrafe(strafe);
        event.setSpeed(STRAFE_BYPASS_SPEED);
    };

    @Override
    public void onDisable() {
        reset();
    }

    private void tickActive(PreMotionEvent event) {
        mc.thePlayer.isInWeb = false;
        mc.thePlayer.fallDistance = 0.0F;
        event.setOnGround(true);

        if (!lifted) {
            ascendToFloor(event);
        } else {
            holdVertical();
        }
    }

    private void ascendToFloor(PreMotionEvent event) {
        final double remaining = floorY - mc.thePlayer.posY;

        if (remaining <= TARGET_EPSILON) {
            if (remaining >= -TARGET_EPSILON) {
                mc.thePlayer.setPosition(mc.thePlayer.posX, floorY, mc.thePlayer.posZ);
                event.setPosY(floorY);
            }

            mc.thePlayer.motionY = 0.0D;
            lifted = true;
            return;
        }

        mc.thePlayer.motionY = Math.min(currentAscendSpeed(), remaining);
        lifted = false;
    }

    private void holdVertical() {
        if (Math.abs(mc.thePlayer.motionY) > 1.0E-6D) {
            mc.thePlayer.motionY = 0.0D;
        }
    }

    private void arm(double targetY) {
        if (!active || targetY > floorY + TARGET_EPSILON) {
            ascendSpeed = randomAscendSpeed();
        }

        floorY = active ? Math.max(floorY, targetY) : targetY;
        active = true;
        lifted = mc.thePlayer.posY >= floorY - TARGET_EPSILON;
        keepAliveTicks = WEB_KEEP_ALIVE_TICKS;
    }

    private double currentAscendSpeed() {
        if (ascendSpeed <= 0.0D) {
            ascendSpeed = randomAscendSpeed();
        }

        return ascendSpeed;
    }

    private double randomAscendSpeed() {
        return ThreadLocalRandom.current().nextDouble(MIN_ASCEND_SPEED, MAX_ASCEND_SPEED);
    }

    private Double findSupportTarget() {
        if (mc.theWorld == null || !active) {
            return null;
        }

        final AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().expand(0.08D, 0.0D, 0.08D);
        final int minX = MathHelper.floor_double(box.minX);
        final int maxX = MathHelper.floor_double(box.maxX);
        final int minZ = MathHelper.floor_double(box.minZ);
        final int maxZ = MathHelper.floor_double(box.maxZ);
        final int centerY = MathHelper.floor_double(floorY - WEB_SURFACE_OFFSET);
        double bestTargetY = Double.NEGATIVE_INFINITY;

        for (int x = minX; x <= maxX; x++) {
            for (int y = centerY - 1; y <= centerY + 1; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final BlockPos position = new BlockPos(x, y, z);

                    if (mc.theWorld.getBlockState(position).getBlock() instanceof BlockWeb) {
                        bestTargetY = Math.max(bestTargetY, targetYFromWebStack(position));
                    }
                }
            }
        }

        return bestTargetY == Double.NEGATIVE_INFINITY ? null : bestTargetY;
    }

    private double targetYFromWebStack(BlockPos pos) {
        int topY = pos.getY();

        while (mc.theWorld != null && mc.theWorld.getBlockState(new BlockPos(pos.getX(), topY + 1, pos.getZ())).getBlock() instanceof BlockWeb) {
            topY++;
        }

        return topY + WEB_SURFACE_OFFSET;
    }

    private void reset() {
        strafeForward = 0.0F;
        strafeSide = 0.0F;
        hasStrafeInput = false;
        active = false;
        lifted = false;
        floorY = 0.0D;
        ascendSpeed = 0.0D;
        keepAliveTicks = 0;
    }
}
