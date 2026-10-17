package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.render.GlintEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @Unique private IBakedModel rise$model;

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V", at = @At("HEAD"))
    private void rise$captureModel(final ItemStack stack, final IBakedModel model, final CallbackInfo ci) {
        rise$model = model;
    }

    /** Enchantment glint goes through GlintEvent (the Glint module recolours it or draws its own). */
    @Redirect(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect()Z"))
    private boolean rise$glint(final ItemStack stack) {
        final GlintEvent event = Events.post(new GlintEvent(stack.hasEffect(), true, stack, rise$model));
        return event.isEnchanted() && event.isRender() && !event.isCancelled();
    }
}
