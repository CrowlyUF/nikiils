package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.render.HurtRenderEvent;
import com.alan.clients.event.impl.render.RenderModelEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> {

    @Shadow protected ModelBase mainModel;

    @Shadow protected abstract void unsetBrightness();

    @Shadow protected abstract void renderLayers(T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale);

    @Unique private boolean rise$deferUnsetBrightness;
    @Unique private float rise$partialTicks;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void rise$capturePartialTicks(final T entity, final double x, final double y, final double z, final float yaw, final float partialTicks, final CallbackInfo ci) {
        rise$partialTicks = partialTicks;
    }

    /** OldDamage: keep the red hurt tint on while armour layers render (1.7 look). */
    @Redirect(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;unsetBrightness()V"))
    private void rise$hurtTint(final RendererLivingEntity<T> renderer) {
        if (Events.post(new HurtRenderEvent(false)).isOldDamage()) {
            rise$deferUnsetBrightness = true;
        } else {
            this.unsetBrightness();
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;disableRescaleNormal()V", ordinal = 0))
    private void rise$hurtTintAfterLayers(final T entity, final double x, final double y, final double z, final float yaw, final float partialTicks, final CallbackInfo ci) {
        if (rise$deferUnsetBrightness) {
            rise$deferUnsetBrightness = false;
            this.unsetBrightness();
        }
    }

    /** RenderModelEvent lets ESP/chams redraw the model (and its layers) right after vanilla draws it. */
    @Redirect(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void rise$renderModel(final ModelBase model, final Entity entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks,
                                  final float netHeadYaw, final float headPitch, final float scale) {
        model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        @SuppressWarnings("unchecked") final T living = (T) entity;
        final float partialTicks = rise$partialTicks;
        Events.post(new RenderModelEvent(living,
                () -> model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale),
                () -> this.renderLayers(living, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale)));
    }
}
