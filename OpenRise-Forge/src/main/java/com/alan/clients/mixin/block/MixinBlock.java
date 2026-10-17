package com.alan.clients.mixin.block;

import com.alan.clients.event.impl.other.BlockAABBEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Block.class)
public abstract class MixinBlock {

    @Shadow public abstract AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state);

    /** Collision boxes the local player collides with go through BlockAABBEvent (Jesus, Phase, Spider...). */
    @Inject(method = "addCollisionBoxesToList", at = @At("HEAD"), cancellable = true)
    private void rise$collision(final World world, final BlockPos pos, final IBlockState state, final AxisAlignedBB mask,
                                final List<AxisAlignedBB> list, final Entity entity, final CallbackInfo ci) {
        if (!Events.isLocalPlayer(entity)) return;

        ci.cancel();

        final BlockAABBEvent event = Events.post(new BlockAABBEvent(world, (Block) (Object) this, pos, this.getCollisionBoundingBox(world, pos, state), mask));

        if (!event.isCancelled() && event.getBoundingBox() != null && event.getMaskBoundingBox().intersectsWith(event.getBoundingBox())) {
            list.add(event.getBoundingBox());
        }
    }
}
