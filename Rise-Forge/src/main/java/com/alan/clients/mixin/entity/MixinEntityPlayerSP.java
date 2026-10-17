package com.alan.clients.mixin.entity;

import com.alan.clients.event.impl.input.ChatInputEvent;
import com.alan.clients.event.impl.motion.PostMotionEvent;
import com.alan.clients.event.impl.motion.PreMotionEvent;
import com.alan.clients.event.impl.motion.PreUpdateEvent;
import com.alan.clients.event.impl.motion.PushOutOfBlockEvent;
import com.alan.clients.event.impl.motion.SlowDownEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.EntityData;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer {

    @Shadow @Final public NetHandlerPlayClient sendQueue;
    @Shadow public MovementInput movementInput;
    @Shadow private boolean serverSprintState;
    @Shadow private boolean serverSneakState;
    @Shadow private double lastReportedPosX;
    @Shadow private double lastReportedPosY;
    @Shadow private double lastReportedPosZ;
    @Shadow private float lastReportedYaw;
    @Shadow private float lastReportedPitch;
    @Shadow private int positionUpdateTicks;

    @Shadow protected abstract boolean isCurrentViewEntity();

    @Shadow(remap = false)
    private boolean isHeadspaceFree(final BlockPos pos, final int height) {
        throw new AssertionError();
    }

    @Unique private SlowDownEvent rise$slowDown;

    public MixinEntityPlayerSP(final World worldIn, final GameProfile playerProfile) {
        super(worldIn, playerProfile);
    }

    @Unique
    private boolean rise$isLocal() {
        return Events.isLocalPlayer(this);
    }

    /* -------------------------------------------------------------- update loop */

    /**
     * @author Rise
     * @reason PreUpdateEvent / PostMotionEvent around the update, and riding look packets are sent from
     * onUpdateWalkingPlayer so they go through PreMotionEvent too.
     */
    @Overwrite
    public void onUpdate() {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            final EntityData data = EntityData.of(this);
            data.prevRenderPitchHead = data.renderPitchHead;
            data.renderPitchHead = this.rotationPitch;

            final boolean local = rise$isLocal();

            if (local) {
                Events.post(new PreUpdateEvent());
            }

            super.onUpdate();

            this.onUpdateWalkingPlayer();

            if (local) {
                Events.post(new PostMotionEvent());
            }
        }
    }

    /**
     * @author Rise
     * @reason Movement packets are built from PreMotionEvent, so modules can spoof position, rotation and ground state.
     */
    @Overwrite
    public void onUpdateWalkingPlayer() {
        final PreMotionEvent event = new PreMotionEvent(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch, this.onGround, this.isSprinting());

        if (rise$isLocal()) {
            Events.post(event);
        }

        final boolean sprinting = event.isSprinting();

        if (sprinting != this.serverSprintState) {
            this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, sprinting ? C0BPacketEntityAction.Action.START_SPRINTING : C0BPacketEntityAction.Action.STOP_SPRINTING));
            this.serverSprintState = sprinting;
        }

        final boolean sneaking = this.isSneaking();

        if (sneaking != this.serverSneakState) {
            this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, sneaking ? C0BPacketEntityAction.Action.START_SNEAKING : C0BPacketEntityAction.Action.STOP_SNEAKING));
            this.serverSneakState = sneaking;
        }

        if (event.isCancelled()) return;

        if (this.isCurrentViewEntity()) {
            final double dx = event.getPosX() - this.lastReportedPosX;
            final double dy = event.getPosY() - this.lastReportedPosY;
            final double dz = event.getPosZ() - this.lastReportedPosZ;
            final double dYaw = event.getYaw() - this.lastReportedYaw;
            final double dPitch = event.getPitch() - this.lastReportedPitch;

            final boolean moved = dx * dx + dy * dy + dz * dz > 9.0E-4D || this.positionUpdateTicks >= 20;
            final boolean rotated = dYaw != 0.0D || dPitch != 0.0D;

            if (this.ridingEntity == null) {
                if (moved && rotated) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(event.getPosX(), event.getPosY(), event.getPosZ(), event.getYaw(), event.getPitch(), event.isOnGround()));
                } else if (moved) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(event.getPosX(), event.getPosY(), event.getPosZ(), event.isOnGround()));
                } else if (rotated) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(event.getYaw(), event.getPitch(), event.isOnGround()));
                } else {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer(event.isOnGround()));
                }

                ++this.positionUpdateTicks;

                if (moved) {
                    this.lastReportedPosX = event.getPosX();
                    this.lastReportedPosY = event.getPosY();
                    this.lastReportedPosZ = event.getPosZ();
                    this.positionUpdateTicks = 0;
                }

                if (rotated) {
                    this.lastReportedYaw = event.getYaw();
                    this.lastReportedPitch = event.getPitch();
                }
            } else {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(event.getYaw(), event.getPitch(), event.isOnGround()));
                this.sendQueue.addToSendQueue(new C0CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
            }
        }
    }

    /* -------------------------------------------------------------- chat */

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void rise$chatInput(final String message, final CallbackInfo ci) {
        final ChatInputEvent event = Events.post(new ChatInputEvent(message));

        if (event.isCancelled()) {
            ci.cancel();
        } else if (!message.equals(event.getMessage())) {
            ci.cancel();
            this.sendQueue.addToSendQueue(new C01PacketChatMessage(event.getMessage()));
        }
    }

    /* -------------------------------------------------------------- push out of blocks */

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void rise$pushOutOfBlocks(final double x, final double y, final double z, final CallbackInfoReturnable<Boolean> cir) {
        if (this.noClip || !rise$isLocal()) return;

        if (!this.isHeadspaceFree(new BlockPos(x, y, z), Math.max((int) Math.ceil(this.height), 1))) {
            if (Events.post(new PushOutOfBlockEvent()).isCancelled()) {
                cir.setReturnValue(false);
            }
        }
    }

    /* -------------------------------------------------------------- item-use slowdown (NoSlow) */

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0))
    private boolean rise$slowDownCheck(final EntityPlayerSP player) {
        rise$slowDown = Events.post(new SlowDownEvent(0.2F, 0.2F, player.isUsingItem()));
        return !rise$slowDown.isCancelled() && rise$slowDown.isUseItem();
    }

    @ModifyConstant(method = "onLivingUpdate", constant = @Constant(floatValue = 0.2F, ordinal = 0))
    private float rise$strafeMultiplier(final float original) {
        return rise$slowDown != null ? rise$slowDown.getStrafeMultiplier() : original;
    }

    @ModifyConstant(method = "onLivingUpdate", constant = @Constant(floatValue = 0.2F, ordinal = 1))
    private float rise$forwardMultiplier(final float original) {
        return rise$slowDown != null ? rise$slowDown.getForwardMultiplier() : original;
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 1))
    private boolean rise$sprintUseCheck(final EntityPlayerSP player) {
        return rise$slowDown != null ? rise$slowDown.isUseItem() : player.isUsingItem();
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 2))
    private boolean rise$sprintKeyUseCheck(final EntityPlayerSP player) {
        return rise$slowDown != null ? rise$slowDown.isUseItem() && !rise$slowDown.isCancelled() : player.isUsingItem();
    }

    /* -------------------------------------------------------------- omni sprint */

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MovementInput;moveForward:F", opcode = Opcodes.GETFIELD, ordinal = 2))
    private float rise$omniSprintStart(final MovementInput input) {
        return EntityData.of(this).omniSprint ? 1.0F : input.moveForward;
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MovementInput;moveForward:F", opcode = Opcodes.GETFIELD, ordinal = 3))
    private float rise$omniSprintKey(final MovementInput input) {
        return EntityData.of(this).omniSprint ? 1.0F : input.moveForward;
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setSprinting(Z)V", ordinal = 3))
    private void rise$omniSprintStop(final EntityPlayerSP player, final boolean sprinting) {
        if (!EntityData.of(this).omniSprint) {
            player.setSprinting(sprinting);
        }
    }
}
