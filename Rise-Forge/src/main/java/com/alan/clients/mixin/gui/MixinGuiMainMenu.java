package com.alan.clients.mixin.gui;

import com.alan.clients.ui.menu.impl.account.AccountManagerScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Forge's main menu (and its Mods button) and adds Rise's account manager to it, instead of replacing the
 * whole menu like the standalone client did.
 */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    private static final int RISE_ALTS_ID = 0x5241;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void rise$addAltManager(final CallbackInfo ci) {
        this.buttonList.add(new GuiButton(RISE_ALTS_ID, 5, 5, 90, 20, "Alt Manager"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void rise$openAltManager(final GuiButton button, final CallbackInfo ci) {
        if (button.id == RISE_ALTS_ID) {
            ci.cancel();
            this.mc.displayGuiScreen(new AccountManagerScreen(this));
        }
    }
}
