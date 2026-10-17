package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
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
    /** Deslocamentos (em pixels reais) usados para o blur: centro + 2 anéis de 6 amostras. */
    private static final float[][] BLUR_OFFSETS = buildOffsets();
    /** Margem copiada em volta do painel para o blur ter o que espalhar nas bordas. */
    private static final int MARGIN = 8;

    private static int tex = -1;
    private static int texW;
    private static int texH;

    private RenderUtil() {
    }

    private static float[][] buildOffsets() {
        float[][] out = new float[1 + 2 * 6][];
        out[0] = new float[]{0, 0};
        int i = 1;
        for (int ring = 1; ring <= 2; ring++) {
            float r = ring * 2.5F;
            for (int k = 0; k < 6; k++) {
                double a = Math.PI * 2 * k / 6 + ring * 0.5;
                out[i++] = new float[]{(float) (Math.cos(a) * r), (float) (Math.sin(a) * r)};
            }
        }
        return out;
    }

    private static int pow2(int v) {
        int p = 64;
        while (p < v) {
            p <<= 1;
        }
        return p;
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

    /**
     * Desenha o fundo borrado do painel. Copia só a área atrás do painel (não a tela inteira) para uma
     * textura RGBA (mesmo formato do framebuffer, sem conversão lenta) e desenha a cópia deslocada 13
     * vezes com média progressiva. Os vértices são locais ao painel; (ox, oy, scale) é a posição e a
     * escala do painel na GUI, usadas para achar os pixels reais.
     */
    static void blur(float w, float h, float r, float ox, float oy, float scale, int sf) {
        if (!Settings.blur) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Região em pixels reais, com origem embaixo à esquerda (como o OpenGL).
        int rx = Math.max(0, (int) Math.floor(ox * sf) - MARGIN);
        int ryTop = Math.max(0, (int) Math.floor(oy * sf) - MARGIN);
        int rw = Math.min(mc.displayWidth - rx, (int) Math.ceil(w * scale * sf) + MARGIN * 2);
        int rh = Math.min(mc.displayHeight - ryTop, (int) Math.ceil(h * scale * sf) + MARGIN * 2);
        if (rw <= 0 || rh <= 0) {
            return;
        }
        int ry = mc.displayHeight - ryTop - rh;

        if (tex < 0) {
            tex = GL11.glGenTextures();
        }
        GlStateManager.bindTexture(tex);
        if (rw > texW || rh > texH) {
            texW = Math.max(texW, pow2(rw));
            texH = Math.max(texH, pow2(rh));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texW, texH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, rx, ry, rw, rh);

        float[][] pts = outline(0, 0, w, h, r);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableAlpha();
        for (int i = 0; i < BLUR_OFFSETS.length; i++) {
            // Média progressiva: a amostra i entra com peso 1/(i+1).
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F / (i + 1));
            float dx = BLUR_OFFSETS[i][0], dy = BLUR_OFFSETS[i][1];
            wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION_TEX);
            for (float[] p : pts) {
                float px = (ox + p[0] * scale) * sf + dx - rx;
                float py = mc.displayHeight - (oy + p[1] * scale) * sf + dy - ry;
                wr.pos(p[0], p[1], 0).tex(px / texW, py / texH).endVertex();
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

    /** Recorta o desenho a um retângulo da GUI (para listas com rolagem). */
    static void scissor(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getMinecraft();
        int sf = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * sf), (int) (mc.displayHeight - (y + h) * sf), Math.max(0, (int) (w * sf)), Math.max(0, (int) (h * sf)));
    }

    static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Rosto da skin (com a camada do chapéu). */
    static void drawHead(ResourceLocation skin, int x, int y, int size) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
    }

    static boolean inside(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
