package com.alan.clients.script.api.wrapper.impl;

import com.alan.clients.script.api.RenderAPI;
import com.alan.clients.util.font.impl.minecraft.MinecraftFont;

public class ScriptMCFontRenderer extends ScriptFontRenderer<MinecraftFont> {
    public ScriptMCFontRenderer(MinecraftFont wrapped) {
        super(wrapped);
    }

    @Override
    public double width(String string) {
        return this.wrapped.width(string);
    }

    @Override
    public double height() {
        return MinecraftFont.FONT_HEIGHT;
    }

    @Override
    public void draw(String string, double x, double y, int[] color) {
        this.wrapped.draw(string, x, y, RenderAPI.intArrayToColor(color).getRGB());
    }

    @Override
    public void drawCentered(String string, double x, double y, int[] color) {
        this.wrapped.draw(string, (float) x - ((int) width(string) >> 1), (float) y, RenderAPI.intArrayToColor(color).getRGB(), false);
    }

    @Override
    public void drawWithShadow(String string, double x, double y, int[] color) {
        this.wrapped.draw(string, x, y, RenderAPI.intArrayToColor(color).getRGB(), true);
    }

    @Override
    public void drawCenteredWithShadow(String string, float x, float y, int[] color) {
        this.wrapped.draw(string, x - ((int) width(string) >> 1), y, RenderAPI.intArrayToColor(color).getRGB(), true);
    }
}
