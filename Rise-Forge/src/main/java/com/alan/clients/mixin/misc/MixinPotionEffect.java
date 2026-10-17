package com.alan.clients.mixin.misc;

import com.alan.clients.module.impl.movement.PotionExtender;
import com.alan.clients.util.mc.Modules;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionEffect.class)
public abstract class MixinPotionEffect {

    @Shadow private int duration;

    /** PotionExtender freezes the client-side countdown of tracked effects. */
    @Inject(method = "deincrementDuration", at = @At("HEAD"), cancellable = true)
    private void rise$potionExtender(final CallbackInfoReturnable<Integer> cir) {
        final PotionExtender extender = Modules.get(PotionExtender.class);

        if (extender != null && extender.isEnabled() && extender.potions.containsKey((PotionEffect) (Object) this)) {
            cir.setReturnValue(this.duration);
        }
    }
}
