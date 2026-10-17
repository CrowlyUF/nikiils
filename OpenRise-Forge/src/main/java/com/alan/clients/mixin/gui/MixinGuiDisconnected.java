package com.alan.clients.mixin.gui;

import com.alan.clients.component.impl.player.LastConnectionComponent;
import com.alan.clients.event.impl.other.ServerKickEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Fires ServerKickEvent and adds Rise's "Reconnect" button next to "Back to server list".
 */
@Mixin(GuiDisconnected.class)
public abstract class MixinGuiDisconnected extends GuiScreen {

    private static final int RISE_RECONNECT_ID = 0x5253;

    @Shadow private List<String> multilineMessage;
    @Shadow @Final private GuiScreen parentScreen;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void rise$initGui(final CallbackInfo ci) {
        if (LastConnectionComponent.ip != null && !this.buttonList.isEmpty()) {
            final GuiButton back = this.buttonList.get(0);
            back.width = 98;
            this.buttonList.add(new GuiButton(RISE_RECONNECT_ID, back.xPosition + 102, back.yPosition, 98, 20, "Reconnect"));
        }

        Events.post(new ServerKickEvent(this.multilineMessage));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void rise$reconnect(final GuiButton button, final CallbackInfo ci) {
        if (button.id == RISE_RECONNECT_ID) {
            ci.cancel();
            this.mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(this.parentScreen), this.mc, new ServerData("", LastConnectionComponent.ip, false)));
        }
    }
}
