package com.alan.clients.mixin.entity;

import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.EntityData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Silent slot switching: while a module holds an alternative slot, the local player's "held item" is the item in
 * that slot (Rise's {@code Slot} component), even though the hotbar selection doesn't change.
 */
@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayer {

    @Shadow public ItemStack[] mainInventory;
    @Shadow public EntityPlayer player;

    @Inject(method = "getCurrentItem", at = @At("HEAD"), cancellable = true)
    private void rise$currentItem(final CallbackInfoReturnable<ItemStack> cir) {
        if (Events.isLocalPlayer(this.player)) {
            final int slot = rise$heldSlot((InventoryPlayer) (Object) this);
            cir.setReturnValue(slot >= 0 && slot < 9 ? this.mainInventory[slot] : null);
        }
    }

    @Redirect(method = {"getStrVsBlock", "canHeldItemHarvest"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.GETFIELD))
    private int rise$heldSlotField(final InventoryPlayer inventory) {
        return rise$heldSlot(inventory);
    }

    private int rise$heldSlot(final InventoryPlayer inventory) {
        return EntityData.heldSlot(inventory);
    }
}
