package com.alan.clients.mixin.render;

import com.alan.clients.util.mc.EntityData;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBiped.class)
public abstract class MixinModelBiped {

    @Shadow public ModelRenderer bipedHead;

    /** The local player's head shows the pitch the server sees (RotationComponent sets renderPitchHead). */
    @Inject(method = "setRotationAngles", at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/ModelRenderer;rotateAngleX:F", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void rise$serverPitch(final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw,
                                  final float headPitch, final float scale, final Entity entity, final CallbackInfo ci) {
        if (Events.isLocalPlayer(entity)) {
            final EntityData data = EntityData.of(entity);
            final float partialTicks = Minecraft.getMinecraft().timer.renderPartialTicks;
            this.bipedHead.rotateAngleX = (data.prevRenderPitchHead + (data.renderPitchHead - data.prevRenderPitchHead) * partialTicks) / (180.0F / (float) Math.PI);
        }
    }
}
