package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.other.UpdatePlayerAnglesEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPlayer.class)
public abstract class MixinModelPlayer {

    @Inject(method = "setRotationAngles", at = @At("TAIL"))
    private void rise$anglesEvent(final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw,
                                  final float headPitch, final float scale, final Entity entity, final CallbackInfo ci) {
        if (entity instanceof EntityPlayer) {
            Events.post(new UpdatePlayerAnglesEvent((EntityPlayer) entity, (ModelPlayer) (Object) this));
        }
    }
}
