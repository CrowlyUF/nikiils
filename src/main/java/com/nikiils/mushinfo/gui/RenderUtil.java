package com.nikiils.mushinfo.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/** Retângulos arredondados e blur de fundo, sem shaders: copia a tela e desenha a cópia deslocada várias vezes. */
final class RenderUtil {

    private static final int CORNER_SEGMENTS = 8;
    /** Deslocamentos (em pixels reais) usados para o blur: centro + 3 anéis de 8 amostras. */
    private static final float[][] BLUR_OFFSETS = buildOffsets();

    private static int screenTex = -1;
    private static int texW;
    private static int texH;

    private RenderUtil() {
    }

    private static float[][] buildOffsets() {
        float[][] out = new float[1 + 3 * 8][];
        out[0] = new float[]{0, 0};
        int i = 1;
        for (int ring = 1; ring <= 3; ring++) {
            float r = ring * 2.2F;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8 + ring * 0.4;
                out[i++] = new float[]{(float) (Math.cos(a) * r), (float) (Math.sin(a) * r)};
            }
        }
        return out;
    }

    /** Copia o que já foi desenhado na tela para uma textura (uma vez por frame, antes dos painéis). */
    static void captureScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth, h = mc.displayHeight;
        if (screenTex < 0) {
            screenTex = GL11.glGenTextures();
        }
        GlStateManager.bindTexture(screenTex);
        if (w != texW || h != texH) {
            texW = w;
            texH = h;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
    }

    /** Pontos do contorno de um retângulo arredondado (coordenadas da GUI). */
    private static float[][] outline(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2);
        float[][] pts = new float[4 * (CORNER_SEGMENTS + 1)][];
        float[][] centers = {{x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}, {x + r, y + r}};
        int i = 0;
        for (int c = 0; c < 4; c++) {
            double start = -Math.PI / 2 + c * Math.PI / 2;
            for (int s = 0; s <= CORNER_SEGMENTS; s++) {
                double a = start + (Math.PI / 2) * s / CORNER_SEGMENTS;
                pts[i++] = new float[]{centers[c][0] + (float) Math.cos(a) * r, centers[c][1] + (float) Math.sin(a) * r};
            }
        }
        return pts;
    }

    /** Desenha a cópia borrada da tela recortada no formato arredondado. */
    static void blur(float x, float y, float w, float h, float r, int scaleFactor) {
        if (screenTex < 0) {
            return;
        }
        float[][] pts = outline(x, y, w, h, r);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableAlpha();
        GlStateManager.bindTexture(screenTex);
        for (int i = 0; i < BLUR_OFFSETS.length; i++) {
            // Média progressiva: a amostra i entra com peso 1/(i+1).
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F / (i + 1));
            float ox = BLUR_OFFSETS[i][0], oy = BLUR_OFFSETS[i][1];
            wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION_TEX);
            for (float[] p : pts) {
                float px = p[0] * scaleFactor + ox;
                float py = p[1] * scaleFactor + oy;
                wr.pos(p[0], p[1], 0).tex(px / texW, 1.0F - py / texH).endVertex();
            }
            tess.draw();
        }
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void roundedRect(float x, float y, float w, float h, float r, int argb) {
        draw(outline(x, y, w, h, r), GL11.GL_POLYGON, argb);
    }

    static void roundedOutline(float x, float y, float w, float h, float r, int argb) {
        GL11.glLineWidth(1.0F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        draw(outline(x, y, w, h, r), GL11.GL_LINE_LOOP, argb);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    static void rect(float x, float y, float w, float h, int argb) {
        draw(new float[][]{{x, y}, {x, y + h}, {x + w, y + h}, {x + w, y}}, GL11.GL_QUADS, argb);
    }

    static void circle(float cx, float cy, float r, int argb) {
        float[][] pts = new float[16][];
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            pts[i] = new float[]{cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r};
        }
        draw(pts, GL11.GL_POLYGON, argb);
    }

    private static void draw(float[][] pts, int mode, int argb) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F, (argb >>> 24) / 255.0F);
        wr.begin(mode, DefaultVertexFormats.POSITION);
        for (float[] p : pts) {
            wr.pos(p[0], p[1], 0).endVertex();
        }
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
