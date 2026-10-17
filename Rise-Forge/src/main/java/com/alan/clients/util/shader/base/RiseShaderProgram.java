package com.alan.clients.util.shader.base;

import net.minecraft.client.renderer.GlStateManager;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.util.Accessor;
import com.alan.clients.util.shader.ShaderUtil;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class RiseShaderProgram implements Accessor {

    private final int programId;

    public RiseShaderProgram(final String fragmentPath, final String vertexPath) {
        this.programId = ShaderUtil.createShader(fragmentPath, vertexPath);
    }

    public static void drawQuad(final double x, final double y, final double width, final double height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2d(x, y + height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2d(x + width, y + height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2d(x + width, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2d(x, y);
        GL11.glEnd();
    }

    public static void drawQuad() {
        final ScaledResolution scaledResolution = MCHooks.scaledResolution();
        drawQuad(0.0, 0.0, scaledResolution.getScaledWidth_double(), scaledResolution.getScaledHeight_double());
    }

    public boolean isValid() {
        return programId > 0;
    }

    public void start() {
        if (programId > 0) GL20.glUseProgram(programId);
    }

    /** Plain rectangle used when a shape shader can't run on this GPU. */
    public static void fallbackQuad(final double x, final double y, final double width, final double height, final java.awt.Color color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
        drawQuad(x, y, width, height);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void stop() {
        GL20.glUseProgram(0);
    }

    public int getProgramId() {
        return programId;
    }
}
