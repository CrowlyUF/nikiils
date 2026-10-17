package com.alan.clients.mixin.world;

import com.alan.clients.module.impl.render.Ambience;
import com.alan.clients.util.mc.Modules;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Ambience "Nether Particles" tints the overworld fog warm. */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider {

    private static boolean rise$netherFog() {
        final Ambience ambience = Modules.get(Ambience.class);
        return ambience != null && ambience.isEnabled() && ambience.getWeather().getValue().getName().equals("Nether Particles");
    }

    @ModifyConstant(method = "getFogColor", constant = @Constant(floatValue = 0.7529412F))
    private float rise$fogRed(final float original) {
        return rise$netherFog() ? 0.85F : original;
    }

    @ModifyConstant(method = "getFogColor", constant = @Constant(floatValue = 0.84705883F))
    private float rise$fogGreen(final float original) {
        return rise$netherFog() ? 0.68F : original;
    }

    @ModifyConstant(method = "getFogColor", constant = @Constant(floatValue = 1.0F, ordinal = 1))
    private float rise$fogBlue(final float original) {
        return rise$netherFog() ? 0.62F : original;
    }
}
