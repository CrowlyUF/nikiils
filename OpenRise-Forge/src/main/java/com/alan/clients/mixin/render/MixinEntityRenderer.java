package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.render.MouseOverEvent;
import com.alan.clients.module.impl.render.Ambience;
import com.alan.clients.module.impl.render.FreeLook;
import com.alan.clients.module.impl.render.HurtCamera;
import com.alan.clients.module.impl.render.NoCameraClip;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.util.mc.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow private Minecraft mc;

    @Unique private double rise$reach = 3.0D;
    @Unique private float rise$expand;

    /* -------------------------------------------------------------- reach / hitbox (MouseOverEvent) */

    @Inject(method = "getMouseOver", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;objectMouseOver:Lnet/minecraft/util/MovingObjectPosition;", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER), cancellable = true)
    private void rise$mouseOver(final float partialTicks, final CallbackInfo ci) {
        final MouseOverEvent event = Events.post(new MouseOverEvent(3.0D, 0.0F));
        rise$reach = event.getRange();
        rise$expand = event.getExpand();

        if (event.getMovingObjectPosition() != null) {
            this.mc.objectMouseOver = event.getMovingObjectPosition();
            this.mc.mcProfiler.endSection();
            ci.cancel();
        }
    }

    /** {@code else if (blockReach > 3.0) flag = true}: a custom reach always enables the entity distance check. */
    @ModifyConstant(method = "getMouseOver", constant = @Constant(doubleValue = 3.0D, ordinal = 0))
    private double rise$reachCheckEnable(final double original) {
        return rise$reach > 3.0001D ? Double.NEGATIVE_INFINITY : original;
    }

    @ModifyConstant(method = "getMouseOver", constant = @Constant(doubleValue = 3.0D, ordinal = 1))
    private double rise$reachDistance(final double original) {
        return rise$reach;
    }

    @Redirect(method = "getMouseOver", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getCollisionBorderSize()F"))
    private float rise$hitboxExpand(final Entity target) {
        final Entity viewer = this.mc.getRenderViewEntity();
        return target.getCollisionBorderSize() + (viewer instanceof EntityPlayer && !viewer.isInvisible() ? rise$expand : 0.0F);
    }

    /* -------------------------------------------------------------- camera */

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void rise$hurtCamera(final float partialTicks, final CallbackInfo ci) {
        if (Modules.isEnabled(HurtCamera.class)) {
            ci.cancel();
        }
    }

    @Redirect(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;rayTraceBlocks(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;"))
    private MovingObjectPosition rise$cameraClip(final WorldClient world, final Vec3 start, final Vec3 end) {
        return Modules.isEnabled(NoCameraClip.class) ? null : world.rayTraceBlocks(start, end);
    }

    /* -------------------------------------------------------------- mouse (FreeLook, aim assist deltas) */

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseHelper;mouseXYChange()V"))
    private void rise$mouseInput(final MouseHelper helper) {
        // FreeLook reads the mouse itself while it's active
        if (!Modules.isEnabled(FreeLook.class)) {
            helper.mouseXYChange();
        }
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MouseHelper;deltaX:I", opcode = Opcodes.GETFIELD))
    private int rise$mouseDeltaX(final MouseHelper helper) {
        final int delta = (int) (helper.deltaX + MCHooks.mouseAddedX);
        MCHooks.mouseAddedX = 0;
        return delta;
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MouseHelper;deltaY:I", opcode = Opcodes.GETFIELD))
    private int rise$mouseDeltaY(final MouseHelper helper) {
        final int delta = (int) (helper.deltaY + MCHooks.mouseAddedY);
        MCHooks.mouseAddedY = 0;
        return delta;
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V"))
    private void rise$applyMouse(final EntityPlayerSP player, final float yaw, final float pitch) {
        if (!Modules.isEnabled(FreeLook.class)) {
            player.setAngles(yaw, pitch);
        }
    }

    /* -------------------------------------------------------------- weather (Ambience) */

    @Inject(method = "addRainParticles", at = @At("HEAD"), cancellable = true)
    private void rise$rainParticles(final CallbackInfo ci) {
        final Ambience ambience = Modules.get(Ambience.class);

        if (ambience != null && ambience.skipRainParticles()) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderRainSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/BiomeGenBase;getFloatTemperature(Lnet/minecraft/util/BlockPos;)F"))
    private float rise$weatherTemperature(final BiomeGenBase biome, final BlockPos pos) {
        final Ambience ambience = Modules.get(Ambience.class);
        return ambience == null ? biome.getFloatTemperature(pos) : ambience.getFloatTemperature(pos, biome);
    }
}
