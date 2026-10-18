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

    /** Opacidade global (0-1) aplicada a tudo que é desenhado aqui e no UiFont: usada no fade da GUI. */
    static float globalAlpha = 1.0F;

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
        if (!Settings.blur || globalAlpha < 0.999F) {
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
        GlStateManager.color((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F,
                (argb >>> 24) / 255.0F * globalAlpha);
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
        GlStateManager.color(1.0F, 1.0F, 1.0F, globalAlpha);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
    }

    /** Linha com espessura em unidades da GUI (desenhada como um quad, nítida em qualquer escala). */
    static void line(float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) {
            return;
        }
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        draw(new float[][]{{x1 + nx, y1 + ny}, {x1 - nx, y1 - ny}, {x2 - nx, y2 - ny}, {x2 + nx, y2 + ny}}, GL11.GL_QUADS, argb);
    }

    /** Sombra suave: camadas arredondadas cada vez maiores e mais transparentes. */
    static void shadow(float x, float y, float w, float h, float r, int size, int maxAlpha) {
        for (int i = size; i >= 1; i--) {
            float f = 1.0F - (float) i / (size + 1);
            int a = (int) (maxAlpha * f * f / size * 2.2F);
            roundedRect(x - i, y - i + 2, w + i * 2, h + i * 2, r + i, Math.min(255, a) << 24);
        }
    }

    /** Degradê vertical (top -> bottom). */
    static void gradient(float x, float y, float w, float h, int top, int bottom) {
        gradientQuad(x, y, w, h, top, top, bottom, bottom);
    }

    /** Degradê horizontal (left -> right). */
    static void gradientH(float x, float y, float w, float h, int left, int right) {
        gradientQuad(x, y, w, h, left, right, right, left);
    }

    /** Quad com uma cor por canto: topo-esq, topo-dir, baixo-dir, baixo-esq. */
    private static void gradientQuad(float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, x, y, tl);
        vertex(wr, x, y + h, bl);
        vertex(wr, x + w, y + h, br);
        vertex(wr, x + w, y, tr);
        tess.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
    }

    private static void vertex(WorldRenderer wr, float x, float y, int argb) {
        wr.pos(x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, (int) ((argb >>> 24) * globalAlpha)).endVertex();
    }

    /** Mistura duas cores ARGB (t = 0 -> a, t = 1 -> b). */
    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    static boolean inside(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
