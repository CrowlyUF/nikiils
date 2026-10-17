package com.alan.clients.mixin.client;

import com.alan.clients.event.impl.inventory.SyncCurrentItemEvent;
import com.alan.clients.event.impl.other.BlockBreakEvent;
import com.alan.clients.event.impl.other.BlockDamageEvent;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.ItemStackData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Shadow @Final private Minecraft mc;

    @Inject(method = "onPlayerDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void rise$blockBreak(final BlockPos pos, final EnumFacing side, final CallbackInfoReturnable<Boolean> cir) {
        if (Events.post(new BlockBreakEvent(pos)).isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;", ordinal = 0, shift = At.Shift.AFTER))
    private void rise$blockDamage(final BlockPos pos, final EnumFacing face, final CallbackInfoReturnable<Boolean> cir) {
        final MovingObjectPosition over = this.mc.objectMouseOver;

        if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(over.getBlockPos())) {
            Events.post(new BlockDamageEvent(this.mc.thePlayer, this.mc.thePlayer.worldObj, pos));
        }
    }

    /** The slot the server is told about comes from SyncCurrentItemEvent (silent slot switching). */
    @Redirect(method = "syncCurrentPlayItem", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.GETFIELD))
    private int rise$syncSlot(final InventoryPlayer inventory) {
        return Events.post(new SyncCurrentItemEvent(inventory.currentItem)).getSlot();
    }

    /** Count client-side placements against the predicted stack size. */
    @Redirect(method = "onPlayerRightClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemBlock;canPlaceBlockOnSide(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)Z"))
    private boolean rise$countPlacement(final ItemBlock itemBlock, final World world, final BlockPos pos, final EnumFacing side, final EntityPlayer player, final ItemStack stack) {
        final boolean canPlace = itemBlock.canPlaceBlockOnSide(world, pos, side, player, stack);

        if (canPlace) {
            final ItemStackData.Holder data = (ItemStackData.Holder) (Object) stack;
            data.rise$markModified();
            data.rise$setRealStackSize(data.rise$getRealStackSize() - 1);
        }

        return canPlace;
    }
}
