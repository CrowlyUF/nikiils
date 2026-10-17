package com.alan.clients.mixin.render;

import com.alan.clients.Client;
import com.alan.clients.event.impl.render.ModelVisibilityEvent;
import com.alan.clients.module.impl.combat.KillAura;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer {

    @Shadow public abstract ModelPlayer getMainModel();

    @Unique private KillAura rise$killAura;

    /** Show the blocking arm pose while KillAura auto-blocks, then let modules adjust it. */
    @Inject(method = "setModelVisibilities", at = @At("TAIL"))
    private void rise$armPose(final AbstractClientPlayer player, final CallbackInfo ci) {
        if (!(player instanceof EntityPlayerSP) || player.isSpectator() || Client.INSTANCE.getModuleManager() == null) return;

        final ModelPlayer model = this.getMainModel();
        final ItemStack stack = player.inventory.getCurrentItem();

        if (stack == null) return;

        if (rise$killAura == null) {
            rise$killAura = Client.INSTANCE.getModuleManager().get(KillAura.class);
        }

        final KillAura aura = rise$killAura;
        if (aura != null && aura.isEnabled() && !aura.autoBlock.getValue().getName().equals("None") && aura.target != null && aura.canBlock()
                && stack.getItemUseAction() == EnumAction.BLOCK) {
            model.heldItemRight = 3;
        }

        model.heldItemRight = Events.post(new ModelVisibilityEvent(stack, model.heldItemRight)).getHeldItemRight();
    }
}
