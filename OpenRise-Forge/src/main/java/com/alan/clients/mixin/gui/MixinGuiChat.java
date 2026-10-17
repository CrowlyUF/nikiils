package com.alan.clients.mixin.gui;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Chat module draws its own input box and handles typing (via GuiKeyBoardEvent), so the vanilla chat screen is
 * reduced to an empty screen that only closes on Escape - the same as Rise's patched GuiChat.
 */
@Mixin(GuiChat.class)
public abstract class MixinGuiChat extends GuiScreen {

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void rise$keyTyped(final char typedChar, final int keyCode, final CallbackInfo ci) {
        ci.cancel();

        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);

            if (this.mc.currentScreen == null) {
                this.mc.setIngameFocus();
            }
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void rise$drawScreen(final int mouseX, final int mouseY, final float partialTicks, final CallbackInfo ci) {
        ci.cancel();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
