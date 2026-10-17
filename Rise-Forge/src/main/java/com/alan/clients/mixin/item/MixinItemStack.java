package com.alan.clients.mixin.item;

import com.alan.clients.util.mc.ItemStackData;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements ItemStackData.Holder {

    @Shadow public int stackSize;

    /** -1 until the first inventory tick: report the real size until then. */
    @Unique private int rise$realStackSize = -1;
    @Unique private int rise$ticksSinceModified = Integer.MAX_VALUE / 2;

    @Override
    public int rise$getRealStackSize() {
        return rise$realStackSize < 0 ? this.stackSize : rise$realStackSize;
    }

    @Override
    public void rise$setRealStackSize(final int size) {
        rise$realStackSize = size;
    }

    @Override
    public void rise$markModified() {
        rise$ticksSinceModified = 0;
    }

    @Inject(method = "updateAnimation", at = @At("HEAD"))
    private void rise$syncRealSize(final World world, final Entity entity, final int slot, final boolean current, final CallbackInfo ci) {
        if (++rise$ticksSinceModified >= 20) {
            rise$realStackSize = this.stackSize;
        }
    }
}
