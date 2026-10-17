package com.alan.clients.mixin.gui;

import com.alan.clients.event.impl.other.ServerJoinEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConnecting.class)
public abstract class MixinGuiConnecting {

    @Inject(method = "connect", at = @At("HEAD"))
    private void rise$serverJoin(final String ip, final int port, final CallbackInfo ci) {
        Events.post(new ServerJoinEvent(ip, port));
    }
}
