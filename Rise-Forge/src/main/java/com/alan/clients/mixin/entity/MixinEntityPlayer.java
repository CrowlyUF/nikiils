package com.alan.clients.mixin.entity;

import com.alan.clients.Client;
import com.alan.clients.component.impl.render.SmoothCameraComponent;
import com.alan.clients.event.impl.motion.HitSlowDownEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.module.impl.player.Scaffold;
import com.alan.clients.util.mc.EntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import org.objectweb.asm.Opcodes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer extends EntityLivingBase {

    @Shadow(remap = false) public float eyeHeight;

    @Shadow public abstract boolean isPlayerSleeping();

    @Unique private HitSlowDownEvent rise$hitSlowDown;
    @Unique private Scaffold rise$scaffold;

    public MixinEntityPlayer(final World worldIn) {
        super(worldIn);
    }

    /* -------------------------------------------------------------- KeepSprint / hit slowdown */

    @ModifyConstant(method = "attackTargetEntityWithCurrentItem", constant = @Constant(doubleValue = 0.6D))
    private double rise$hitSlowDown(final double original) {
        if (!Events.isLocalPlayer(this)) return original;

        // first of the two multipliers (motionX); reuse the same event for motionZ
        if (rise$hitSlowDown == null) {
            rise$hitSlowDown = Events.post(new HitSlowDownEvent(original, false));
            return rise$hitSlowDown.slowDown;
        }

        return rise$hitSlowDown.slowDown;
    }

    @Redirect(method = "attackTargetEntityWithCurrentItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"))
    private void rise$hitSprint(final EntityPlayer player, final boolean sprinting) {
        if (rise$hitSlowDown != null) {
            player.setSprinting(rise$hitSlowDown.sprint);
            rise$hitSlowDown = null;
        } else {
            player.setSprinting(sprinting);
        }
    }

    /* -------------------------------------------------------------- silent slot */

    @Redirect(method = {"onItemUseFinish", "destroyCurrentEquippedItem"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.GETFIELD))
    private int rise$heldSlot(final InventoryPlayer inventory) {
        return EntityData.heldSlot(inventory);
    }

    /* -------------------------------------------------------------- Scaffold "ignore speed" */

    @Inject(method = "getAIMoveSpeed", at = @At("HEAD"), cancellable = true)
    private void rise$scaffoldSpeed(final CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof EntityPlayerSP) || Client.INSTANCE.getModuleManager() == null) return;

        if (rise$scaffold == null) {
            rise$scaffold = Client.INSTANCE.getModuleManager().get(Scaffold.class);
        }

        if (rise$scaffold != null && rise$scaffold.isEnabled() && rise$scaffold.ignoreSpeed.getValue()) {
            cir.setReturnValue(Minecraft.getMinecraft().gameSettings.keyBindSprint.isKeyDown() ? 0.13000001F : 0.1F);
        }
    }

    /* -------------------------------------------------------------- camera height */

    /**
     * Rise's eye height: smooth camera interpolation while the component is active, and the sneak offset can be
     * hidden for a moment after a module un-sneaks the player.
     */
    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    private void rise$eyeHeight(final CallbackInfoReturnable<Float> cir) {
        float f = this.eyeHeight;

        if (!SmoothCameraComponent.stopWatch.finished(60)) {
            f = (float) (this.eyeHeight - (lastTickPosY + ((posY - lastTickPosY) * Minecraft.getMinecraft().timer.renderPartialTicks) - SmoothCameraComponent.y));
        }

        if (this.isPlayerSleeping()) {
            f = 0.2F;
        }

        if (this.isSneaking() && EntityData.of(this).hideSneakHeight().finished(100)) {
            f -= 0.08F;
        }

        cir.setReturnValue(f);
    }
}
