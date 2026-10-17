package com.alan.clients.mixin.gui;

import com.alan.clients.event.impl.input.GuiClickEvent;
import com.alan.clients.event.impl.input.GuiKeyBoardEvent;
import com.alan.clients.event.impl.input.GuiMouseReleaseEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void rise$guiClick(final int mouseX, final int mouseY, final int mouseButton, final CallbackInfo ci) {
        Events.post(new GuiClickEvent(mouseX, mouseY, mouseButton));
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void rise$guiRelease(final int mouseX, final int mouseY, final int state, final CallbackInfo ci) {
        Events.post(new GuiMouseReleaseEvent());
    }

    @Inject(method = "handleKeyboardInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;keyTyped(CI)V", shift = At.Shift.AFTER))
    private void rise$guiKey(final CallbackInfo ci) {
        Events.post(new GuiKeyBoardEvent(Keyboard.getEventKey(), Keyboard.getEventCharacter(), (GuiScreen) (Object) this));
    }
}
