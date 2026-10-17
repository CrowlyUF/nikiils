package com.alan.clients.mixin.entity;

import com.alan.clients.component.impl.player.RotationComponent;
import com.alan.clients.event.impl.motion.JumpEvent;
import com.alan.clients.event.impl.motion.MinimumMotionEvent;
import com.alan.clients.event.impl.render.LookEvent;
import com.alan.clients.event.impl.render.SwingAnimationEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.EntityData;
import com.alan.clients.util.player.MoveUtil;
import com.alan.clients.util.vector.Vector2f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    @Shadow public int hurtTime;

    @Shadow protected abstract float getJumpUpwardsMotion();

    @Shadow public abstract boolean isPotionActive(Potion potion);

    @Shadow public abstract PotionEffect getActivePotionEffect(Potion potion);

    @Unique private double rise$minimumMotion = 0.005D;

    public MixinEntityLivingBase(final World worldIn) {
        super(worldIn);
    }

    @Unique
    private EntityData rise$data() {
        return EntityData.of(this);
    }

    @Inject(method = "onEntityUpdate", at = @At("HEAD"))
    private void rise$lastHurtTime(final CallbackInfo ci) {
        rise$data().lastHurtTime = this.hurtTime;
    }

    /* -------------------------------------------------------------- jumping */

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void rise$jump(final CallbackInfo ci) {
        if (!Events.isLocalPlayer(this)) return;

        ci.cancel();

        final EntityData data = rise$data();

        // Rise blocks a second jump inside the same two ticks
        if (data.ticksSinceJump <= 1) return;

        float jumpMotion = this.getJumpUpwardsMotion();

        if (this.isPotionActive(Potion.jump)) {
            jumpMotion += (float) (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        }

        final JumpEvent event = Events.post(new JumpEvent(jumpMotion, data.movementYaw));
        jumpMotion = event.getJumpMotion();
        data.movementYaw = event.getYaw();
        data.velocityYaw = event.getYaw();

        if (event.isCancelled()) {
            return;
        }

        data.ticksSinceJump = 0;
        this.motionY = jumpMotion;

        if (this.isSprinting()) {
            float f = data.movementYaw * 0.017453292F;

            if (data.omniSprint) {
                f = (float) MoveUtil.direction();
            }

            this.motionX -= MathHelper.sin(f) * 0.2F;
            this.motionZ += MathHelper.cos(f) * 0.2F;
        }

        this.isAirBorne = true;
        ForgeHooks.onLivingJump((EntityLivingBase) (Object) this);
    }

    @Inject(method = "moveEntityWithHeading", at = @At("HEAD"))
    private void rise$countJumpTicks(final float strafe, final float forward, final CallbackInfo ci) {
        if (this.isServerWorld()) {
            rise$data().ticksSinceJump++;
        }
    }

    @Shadow public abstract boolean isServerWorld();

    /* -------------------------------------------------------------- minimum motion */

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void rise$minimumMotion(final CallbackInfo ci) {
        rise$minimumMotion = Events.isLocalPlayer(this) ? Events.post(new MinimumMotionEvent(0.005D)).getMinimumMotion() : 0.005D;
    }

    @ModifyConstant(method = "onLivingUpdate", constant = @Constant(doubleValue = 0.005D))
    private double rise$applyMinimumMotion(final double original) {
        return rise$minimumMotion;
    }

    /* -------------------------------------------------------------- swing speed */

    @Inject(method = "getArmSwingAnimationEnd", at = @At("RETURN"), cancellable = true)
    private void rise$swingAnimation(final CallbackInfoReturnable<Integer> cir) {
        final int end = Events.post(new SwingAnimationEvent(cir.getReturnValue())).getAnimationEnd();
        cir.setReturnValue((int) (end * Minecraft.getMinecraft().timer.timerSpeed));
    }

    /* -------------------------------------------------------------- server-side rotations on the body model */

    @Redirect(method = "onUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F", opcode = Opcodes.GETFIELD, ordinal = 0))
    private float rise$bodyYaw(final EntityLivingBase entity) {
        return rise$renderYaw(entity);
    }

    @Redirect(method = "updateDistance", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F", opcode = Opcodes.GETFIELD))
    private float rise$headYaw(final EntityLivingBase entity) {
        return rise$renderYaw(entity);
    }

    @Unique
    private static float rise$renderYaw(final EntityLivingBase entity) {
        if (Events.isLocalPlayer(entity) && RotationComponent.rotations != null) {
            return RotationComponent.rotations.x;
        }

        return entity.rotationYaw;
    }

    /* -------------------------------------------------------------- look vector (silent aim ray casts) */

    /**
     * Rise moved getLook into AbstractClientPlayer and ran it through LookEvent so ray traces can use the server-side
     * rotation instead of the camera's.
     */
    @Inject(method = "getLook", at = @At("HEAD"), cancellable = true)
    private void rise$getLook(final float partialTicks, final CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof AbstractClientPlayer)) return;

        final LookEvent event = Events.post(new LookEvent(new Vector2f(this.rotationYaw, this.rotationPitch)));
        cir.setReturnValue(this.getVectorForRotation(event.getRotation().y, event.getRotation().x));
    }
}
