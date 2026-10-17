package com.alan.clients.mixin.network;

import com.alan.clients.Client;
import com.alan.clients.event.impl.other.TeleportEvent;
import com.alan.clients.module.impl.exploit.Disabler;
import com.alan.clients.module.impl.exploit.disabler.VulcanDisabler;
import com.alan.clients.util.mc.EntityData;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.ItemStackData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    @Shadow @Final private NetworkManager netManager;
    @Shadow private Minecraft gameController;
    @Shadow private boolean doneLoadingTerrain;

    /** Vulcan disabler (miscellaneous): don't announce the client brand after joining. */
    @Inject(method = "handleJoinGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"), cancellable = true)
    private void rise$skipBrand(final S01PacketJoinGame packet, final CallbackInfo ci) {
        if (Client.INSTANCE.getModuleManager() == null) return;

        final Disabler disabler = Client.INSTANCE.getModuleManager().get(Disabler.class);

        if (disabler != null && disabler.isEnabled() && disabler.vulcan.getValue()
                && ((VulcanDisabler) disabler.vulcan.getMode()).miscellaneous.getValue()) {
            ci.cancel();
        }
    }

    /**
     * Server teleports go through TeleportEvent (modules can cancel or alter them) and are confirmed with the exact
     * teleport position, as Rise did.
     */
    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V", shift = At.Shift.AFTER), cancellable = true)
    private void rise$teleport(final S08PacketPlayerPosLook packet, final CallbackInfo ci) {
        ci.cancel();

        final EntityPlayerSP player = this.gameController.thePlayer;
        final TeleportEvent event = Events.post(new TeleportEvent(
                new C03PacketPlayer.C06PacketPlayerPosLook(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, false),
                packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch()));

        if (event.isCancelled()) {
            return;
        }

        double x = event.getPosX(), y = event.getPosY(), z = event.getPosZ();
        float yaw = event.getYaw(), pitch = event.getPitch();
        final Set<S08PacketPlayerPosLook.EnumFlags> flags = packet.func_179834_f();

        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X)) x += player.posX;
        else player.motionX = 0.0D;

        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y)) y += player.posY;
        else player.motionY = 0.0D;

        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Z)) z += player.posZ;
        else player.motionZ = 0.0D;

        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X_ROT)) pitch += player.rotationPitch;
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT)) yaw += player.rotationYaw;

        player.setPositionAndRotation(x, y, z, yaw, pitch);
        this.netManager.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(x, y, z, yaw, pitch, false));

        if (!this.doneLoadingTerrain) {
            player.prevPosX = player.posX;
            player.prevPosY = player.posY;
            player.prevPosZ = player.posZ;
            this.doneLoadingTerrain = true;
            this.gameController.displayGuiScreen(null);
        }
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void rise$explosionVelocity(final S27PacketExplosion packet, final CallbackInfo ci) {
        if (packet.func_149149_c() != 0 && packet.func_149144_d() != 0 && packet.func_149147_e() != 0) {
            EntityData.of(this.gameController.thePlayer).ticksSinceVelocity = 0;
        }
    }

    /** Keep the scaffold's predicted block count when the server resyncs a hotbar slot. */
    @Inject(method = "handleSetSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;putStackInSlot(ILnet/minecraft/item/ItemStack;)V", ordinal = 0))
    private void rise$keepPredictedStack(final S2FPacketSetSlot packet, final CallbackInfo ci) {
        final ItemStack incoming = packet.func_149174_e();
        final int index = packet.func_149173_d() - 36;
        final ItemStack[] hotbar = this.gameController.thePlayer.inventory.mainInventory;

        if (incoming != null && index >= 0 && index < hotbar.length && hotbar[index] != null) {
            ((ItemStackData.Holder) (Object) incoming).rise$setRealStackSize(ItemStackData.realStackSize(hotbar[index]));
        }
    }
}
