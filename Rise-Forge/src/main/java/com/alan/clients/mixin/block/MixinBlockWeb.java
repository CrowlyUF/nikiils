package com.alan.clients.mixin.block;

import com.alan.clients.module.impl.movement.NoWeb;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.Modules;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockWeb.class)
public abstract class MixinBlockWeb {

    @Inject(method = "onEntityCollidedWithBlock", at = @At("HEAD"), cancellable = true)
    private void rise$noWeb(final World world, final BlockPos pos, final IBlockState state, final Entity entity, final CallbackInfo ci) {
        if (!Events.isLocalPlayer(entity)) return;

        final NoWeb noWeb = Modules.get(NoWeb.class);
        if (noWeb != null && noWeb.isEnabled() && noWeb.handleWebCollision(pos)) {
            ci.cancel();
        }
    }
}
