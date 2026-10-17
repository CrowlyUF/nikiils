package com.alan.clients.util.font.impl.minecraft;

import com.alan.clients.util.font.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

/**
 * Rise shipped a patched copy of the vanilla font renderer that extended {@link Font}. A Forge mod can't replace that
 * class, so this adapts the live vanilla renderer to Rise's font API instead.
 */
public final class MinecraftFont extends Font {

    public static final MinecraftFont INSTANCE = new MinecraftFont();
    public static final int FONT_HEIGHT = 9;

    private MinecraftFont() {
    }

    public static FontRenderer vanilla() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    @Override
    public int draw(final String text, final double x, final double y, final int color, final boolean dropShadow) {
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        // Rise's renderer drew one pixel higher than vanilla
        return vanilla().drawString(text, (float) x, (float) (y - 1), color, dropShadow);
    }

    @Override
    public int draw(final String text, final double x, final double y, final int color) {
        return draw(text, x, y, color, false);
    }

    @Override
    public int drawWithShadow(final String text, final double x, final double y, final int color) {
        return draw(text, x, y, color, true);
    }

    @Override
    public int width(final String text) {
        return vanilla().getStringWidth(text);
    }

    @Override
    public int drawCentered(final String text, final double x, final double y, final int color) {
        return draw(text, x - (width(text) >> 1), y, color, false);
    }

    @Override
    public int drawRight(final String text, final double x, final double y, final int color) {
        return draw(text, x - width(text), y, color, false);
    }

    @Override
    public float height() {
        return vanilla().FONT_HEIGHT;
    }

    @Override
    public void drawCharacter(final char character, final int x, final int y, final Color color) {
        drawWithShadow(String.valueOf(character), x, y, color.getRGB());
    }
}
