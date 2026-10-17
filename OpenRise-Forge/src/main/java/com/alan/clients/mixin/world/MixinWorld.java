package com.alan.clients.mixin.world;

import com.alan.clients.util.mc.EntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorld {

    @Shadow public boolean isRemote;

    @Inject(method = "updateEntityWithOptionalForce", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastTickPosX:D", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void rise$beforeTickPositions(final Entity entity, final boolean forceUpdate, final CallbackInfo ci) {
        final EntityData data = EntityData.of(entity);
        data.lastLastTickPosX = entity.lastTickPosX;
        data.lastLastTickPosY = entity.lastTickPosY;
        data.lastLastTickPosZ = entity.lastTickPosZ;
    }

    /** Right after {@code ++ticksExisted}, i.e. only when the entity really ticks this frame. */
    @Inject(method = "updateEntityWithOptionalForce", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;ticksExisted:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void rise$tickCounters(final Entity entity, final boolean forceUpdate, final CallbackInfo ci) {
        final EntityData data = EntityData.of(entity);
        ++data.ticksSinceVelocity;
        ++data.ticksVisible;
        ++data.ticksSincePlayerVelocity;
        ++data.ticksSinceTeleport;
        ++data.ticksSinceAttack;
        ++data.ticksSincePlace;

        if (this.isRemote) {
            final Entity player = Minecraft.getMinecraft().thePlayer;
            data.threadDistance = player == null ? 0.0D : player.getDistanceSqToEntity(entity);
        }
    }
}
