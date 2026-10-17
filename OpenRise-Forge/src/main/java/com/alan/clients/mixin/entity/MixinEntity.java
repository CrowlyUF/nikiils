package com.alan.clients.mixin.entity;

import com.alan.clients.event.impl.motion.PostStrafeEvent;
import com.alan.clients.event.impl.motion.SafeWalkEvent;
import com.alan.clients.event.impl.motion.StrafeEvent;
import com.alan.clients.event.impl.motion.WaterEvent;
import com.alan.clients.event.impl.other.MoveEvent;
import com.alan.clients.event.impl.other.StepEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.ProtocolUtil;
import com.alan.clients.util.mc.EntityData;
import com.alan.clients.util.time.StopWatch;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityData.Holder {

    @Shadow public double posX, posY, posZ;
    @Shadow public double lastTickPosX, lastTickPosY, lastTickPosZ;
    @Shadow public double motionX, motionZ;
    @Shadow public float rotationYaw;
    @Shadow public boolean onGround;
    @Shadow protected boolean inWater;

    @Shadow public abstract boolean isSprinting();

    @Shadow public abstract boolean isInvisible();

    @Shadow public abstract AxisAlignedBB getEntityBoundingBox();

    @Shadow public abstract void moveEntity(double x, double y, double z);

    @Unique private final EntityData rise$data = new EntityData();
    @Unique private final StopWatch rise$waterWatch = new StopWatch();
    @Unique private boolean rise$movingWithoutEvent;
    @Unique private double rise$safeWalkHeight = -1.0D;
    @Unique private boolean rise$stepped;

    @Override
    public EntityData rise$getData() {
        return rise$data;
    }

    @Unique
    private boolean rise$isLocalPlayer() {
        return Events.isLocalPlayer((Object) this);
    }

    /* -------------------------------------------------------------- per-tick bookkeeping */

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void rise$onUpdate(final CallbackInfo ci) {
        final EntityData data = rise$data;

        if (this.isInvisible()) {
            data.ticksVisible = 0;
        }

        if (this.onGround) {
            data.offGroundTicks = 0;
            data.onGroundTicks++;
        } else {
            data.onGroundTicks = 0;
            data.offGroundTicks++;
        }

        final double dx = posX - lastTickPosX, dy = posY - lastTickPosY, dz = posZ - lastTickPosZ;
        if (dx * dx + dy * dy + dz * dz > 0.01D) {
            data.moved = true;
        }

        data.ticksSprint = this.isSprinting() ? data.ticksSprint + 1 : 0;
    }

    /* -------------------------------------------------------------- MoveEvent */

    @Inject(method = "moveEntity", at = @At("HEAD"), cancellable = true)
    private void rise$moveEvent(final double x, final double y, final double z, final CallbackInfo ci) {
        if (rise$movingWithoutEvent || !rise$isLocalPlayer()) return;

        final MoveEvent event = Events.post(new MoveEvent(x, y, z));

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.getPosX() != x || event.getPosY() != y || event.getPosZ() != z) {
            ci.cancel();
            rise$movingWithoutEvent = true;

            try {
                this.moveEntity(event.getPosX(), event.getPosY(), event.getPosZ());
            } finally {
                rise$movingWithoutEvent = false;
            }
        }
    }

    /* -------------------------------------------------------------- safe walk */

    /**
     * Vanilla: {@code flag = onGround && isSneaking() && this instanceof EntityPlayer}. Rise turned that into
     * {@code flag || safeWalk}; both operands are widened here, which gives the same result for players.
     */
    @Redirect(method = "moveEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private boolean rise$safeWalkGround(final Entity entity) {
        rise$safeWalkHeight = -1.0D;

        if (rise$isLocalPlayer()) {
            rise$safeWalkHeight = Events.post(new SafeWalkEvent(-1.0D)).getHeight();
            return entity.onGround || rise$data.safeWalk;
        }

        return entity.onGround;
    }

    @Redirect(method = "moveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z", ordinal = 0))
    private boolean rise$safeWalkSneak(final Entity entity) {
        return entity.isSneaking() || (rise$isLocalPlayer() && rise$data.safeWalk);
    }

    @ModifyConstant(method = "moveEntity", constant = @Constant(doubleValue = -1.0D))
    private double rise$safeWalkEdgeHeight(final double original) {
        return rise$safeWalkHeight;
    }

    /* -------------------------------------------------------------- step + ground tracking */

    @Inject(method = "moveEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;stepHeight:F", opcode = Opcodes.GETFIELD, ordinal = 1))
    private void rise$markStep(final double x, final double y, final double z, final CallbackInfo ci) {
        rise$stepped = true;
    }

    @Inject(method = "moveEntity", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V", args = "ldc=rest"))
    private void rise$afterStep(final double x, final double y, final double z, final CallbackInfo ci) {
        if (rise$stepped && (Object) this instanceof EntityPlayerSP) {
            rise$data.ticksSinceStep = 0;
            Events.post(new StepEvent(this.getEntityBoundingBox().minY - this.posY));
        }

        rise$stepped = false;
        rise$data.ticksSinceStep++;
        rise$data.safeWalk = false;
    }

    @Inject(method = "moveEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void rise$trackGround(final double x, final double y, final double z, final CallbackInfo ci) {
        if (this.onGround) {
            final EntityData data = rise$data;
            data.lastLastGroundX = data.lastGroundX;
            data.lastLastGroundY = data.lastGroundY;
            data.lastLastGroundZ = data.lastGroundZ;
            data.lastGroundX = posX;
            data.lastGroundY = posY;
            data.lastGroundZ = posZ;
        }
    }

    /* -------------------------------------------------------------- strafe / movement correction */

    /**
     * Rise's moveFlying for the local player: runs the StrafeEvent and accelerates along the event's yaw (the
     * "movement yaw") instead of the camera yaw, which is what keeps silent rotations from flagging.
     */
    @Inject(method = "moveFlying", at = @At("HEAD"), cancellable = true)
    private void rise$moveFlying(float strafe, float forward, float friction, final CallbackInfo ci) {
        if (!rise$isLocalPlayer()) return;

        ci.cancel();

        final StrafeEvent event = Events.post(new StrafeEvent(forward, strafe, friction, rise$data.movementYaw));

        if (event.isCancelled()) {
            return;
        }

        forward = event.getForward();
        strafe = event.getStrafe();
        friction = event.getFriction();
        final float yaw = event.getYaw();

        float f = strafe * strafe + forward * forward;

        if (f >= 1.0E-4F) {
            f = MathHelper.sqrt_float(f);

            if (f < 1.0F) {
                f = 1.0F;
            }

            f = friction / f;
            strafe *= f;
            forward *= f;
            final float sin = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
            final float cos = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
            this.motionX += strafe * cos - forward * sin;
            this.motionZ += forward * cos + strafe * sin;
        }

        Events.post(new PostStrafeEvent());
    }

    /* -------------------------------------------------------------- misc */

    @Inject(method = "isInWater", at = @At("HEAD"))
    private void rise$waterEvent(final CallbackInfoReturnable<Boolean> cir) {
        if (rise$isLocalPlayer() && rise$waterWatch.finished(100)) {
            this.inWater = Events.post(new WaterEvent(this.inWater)).isWater();
            rise$waterWatch.reset();
        }
    }

    /** 1.9+ removed the 0.1 hitbox expansion; matters when connected through ViaForge. */
    @Inject(method = "getCollisionBorderSize", at = @At("HEAD"), cancellable = true)
    private void rise$collisionBorder(final CallbackInfoReturnable<Float> cir) {
        if (ProtocolUtil.isNewerThan1_8()) {
            cir.setReturnValue(0.0F);
        }
    }
}
