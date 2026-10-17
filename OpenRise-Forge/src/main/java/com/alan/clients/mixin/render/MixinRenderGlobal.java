package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.other.BlockDamageEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow @Final private Minecraft mc;

    /** Breaking progress (ours or someone else's) on the block under the crosshair. */
    @Inject(method = "sendBlockBreakProgress", at = @At("HEAD"))
    private void rise$blockDamage(final int breakerId, final BlockPos pos, final int progress, final CallbackInfo ci) {
        final MovingObjectPosition over = this.mc.objectMouseOver;

        if (progress >= 0 && progress < 10 && this.mc.thePlayer != null && over != null
                && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(over.getBlockPos())) {
            Events.post(new BlockDamageEvent(this.mc.thePlayer, this.mc.thePlayer.worldObj, pos));
        }
    }
}
