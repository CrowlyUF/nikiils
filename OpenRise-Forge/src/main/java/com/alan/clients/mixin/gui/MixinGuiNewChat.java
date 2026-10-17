package com.alan.clients.mixin.gui;

import com.alan.clients.module.impl.render.UnlimitedChat;
import com.alan.clients.util.mc.Modules;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    @ModifyConstant(method = "setChatLine", constant = @Constant(intValue = 100))
    private int rise$chatHistory(final int original) {
        return Modules.isEnabled(UnlimitedChat.class) ? 10000 : 200;
    }
}
