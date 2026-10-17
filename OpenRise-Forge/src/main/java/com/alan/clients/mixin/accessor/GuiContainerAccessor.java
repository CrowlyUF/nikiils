package com.alan.clients.mixin.accessor;

import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiContainer.class)
public interface GuiContainerAccessor {
    @Invoker("mouseClicked")
    void rise$mouseClicked(int mouseX, int mouseY, int mouseButton);
}
