package com.nikiils.mushinfo.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fonte da GUI: Comic Sans MS do sistema (vem com o Windows) ou, se não existir, a Comic Neue que vai
 * dentro do mod (licença OFL). As letras são desenhadas numa textura na resolução real da tela (refeita
 * se a escala da GUI mudar), então ficam nítidas. Aceita os códigos de cor do Minecraft (§a, §l...).
 * Mesmos nomes de métodos do FontRenderer, para trocar um pelo outro sem mudar o resto.
 */
final class UiFont {

    /** Altura de uma linha em unidades da GUI (a mesma do FontRenderer vanilla). */
    final int FONT_HEIGHT = 9;

    private static final int FIRST = 32;
    private static final int LAST = 255;
    private static final int[] COLORS = new int[16];

    static {
        for (int i = 0; i < 16; i++) {
            int j = (i >> 3 & 1) * 85;
            int r = (i >> 2 & 1) * 170 + j;
            int g = (i >> 1 & 1) * 170 + j;
            int b = (i & 1) * 170 + j;
            if (i == 6) {
                r += 85;
            }
            COLORS[i] = r << 16 | g << 8 | b;
        }
    }

    private static UiFont instance;

    private final Font regularBase;
    private final Font boldBase;
    private Atlas regular;
    private Atlas bold;
    private int builtFor = -1;

    private static final class Atlas {
        int tex = -1;
        int size;
        float scale; // pixels -> unidades da GUI
        final float[] u = new float[LAST + 1], v = new float[LAST + 1];
        final float[] w = new float[LAST + 1], h = new float[LAST + 1];
        final float[] advance = new float[LAST + 1];
        float cellH;
        float ascentOffset;
    }

    static UiFont get() {
        if (instance == null) {
            instance = new UiFont();
        }
        return instance;
    }

    private UiFont() {
        Font reg = load(new String[]{
                System.getenv("WINDIR") + "\\Fonts\\comic.ttf", "C:\\Windows\\Fonts\\comic.ttf",
                "/Library/Fonts/Comic Sans MS.ttf", "/System/Library/Fonts/Supplemental/Comic Sans MS.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Comic_Sans_MS.ttf",
                System.getProperty("user.home") + "/.fonts/comic.ttf"}, "/assets/mushinfo/fonts/ComicNeue-Regular.ttf");
        Font bld = load(new String[]{
                System.getenv("WINDIR") + "\\Fonts\\comicbd.ttf", "C:\\Windows\\Fonts\\comicbd.ttf",
                "/Library/Fonts/Comic Sans MS Bold.ttf", "/System/Library/Fonts/Supplemental/Comic Sans MS Bold.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Comic_Sans_MS_Bold.ttf",
                System.getProperty("user.home") + "/.fonts/comicbd.ttf"}, "/assets/mushinfo/fonts/ComicNeue-Bold.ttf");
        regularBase = reg;
        boldBase = bld != null ? bld : reg != null ? reg.deriveFont(Font.BOLD) : null;
    }

    private static Font load(String[] files, String resource) {
        for (String path : files) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    return Font.createFont(Font.TRUETYPE_FONT, f);
                }
            } catch (Exception ignored) {
                // tenta o próximo
            }
        }
        try (InputStream in = UiFont.class.getResourceAsStream(resource)) {
            return in != null ? Font.createFont(Font.TRUETYPE_FONT, in) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================ atlas

    /** (Re)gera as texturas quando a escala da GUI muda. Retorna false se a fonte não carregou. */
    private boolean ready() {
        if (regularBase == null) {
            return false;
        }
        int sf = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        if (sf != builtFor) {
            free(regular);
            free(bold);
            regular = build(regularBase, sf);
            bold = build(boldBase, sf);
            builtFor = sf;
        }
        return regular != null;
    }

    private static void free(Atlas a) {
        if (a != null && a.tex >= 0) {
            TextureUtil.deleteTexture(a.tex);
        }
    }

    private static Atlas build(Font base, int sf) {
        try {
            // Linha de ~9 unidades da GUI = 9*sf pixels reais.
            float target = FONT_HEIGHT_PX(sf);
            Font font = base.deriveFont(target);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(font);
            FontMetrics pm = pg.getFontMetrics();
            float factor = target / pm.getHeight();
            font = base.deriveFont(target * factor);
            pg.setFont(font);
            FontMetrics fm = pg.getFontMetrics();
            pg.dispose();

            Atlas a = new Atlas();
            int pad = 2;
            int cellH = fm.getHeight() + pad * 2;
            int size = 256;
            while (true) {
                int x = 0, y = 0;
                for (int c = FIRST; c <= LAST; c++) {
                    int cw = fm.charWidth((char) c) + pad * 2;
                    if (x + cw > size) {
                        x = 0;
                        y += cellH;
                    }
                    x += cw;
                }
                if (y + cellH <= size) {
                    break;
                }
                size *= 2;
            }
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setFont(font);
            g.setColor(java.awt.Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            int x = 0, y = 0;
            for (int c = FIRST; c <= LAST; c++) {
                int cw = fm.charWidth((char) c) + pad * 2;
                if (x + cw > size) {
                    x = 0;
                    y += cellH;
                }
                g.drawString(String.valueOf((char) c), x + pad, y + pad + fm.getAscent());
                a.u[c] = x / (float) size;
                a.v[c] = y / (float) size;
                a.w[c] = cw;
                a.h[c] = cellH;
                a.advance[c] = fm.charWidth((char) c);
                x += cw;
            }
            g.dispose();
            a.size = size;
            a.scale = 1.0F / sf;
            a.cellH = cellH;
            // Centraliza a linha na mesma altura que o texto vanilla ocuparia.
            a.ascentOffset = (cellH - FONT_HEIGHT_PX(sf)) / 2.0F + pad * 0.5F;
            a.tex = TextureUtil.glGenTextures();
            TextureUtil.uploadTextureImageAllocate(a.tex, img, true, false);
            return a;
        } catch (Throwable t) {
            return null;
        }
    }

    private static float FONT_HEIGHT_PX(int sf) {
        return 9 * sf;
    }

    // ================================================================ medidas

    int getStringWidth(String text) {
        if (!ready()) {
            return vanilla().getStringWidth(text);
        }
        return (int) Math.ceil(width(text));
    }

    private float width(String text) {
        if (text == null) {
            return 0;
        }
        float wsum = 0;
        boolean isBold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char k = Character.toLowerCase(text.charAt(++i));
                if (k == 'l') {
                    isBold = true;
                } else if (k == 'r' || "0123456789abcdef".indexOf(k) >= 0) {
                    isBold = false;
                }
                continue;
            }
            Atlas a = isBold ? bold : regular;
            wsum += a.advance[glyph(c)] * a.scale + (isBold ? a.scale : 0);
        }
        return wsum;
    }

    String trimStringToWidth(String text, int maxWidth) {
        if (!ready()) {
            return vanilla().trimStringToWidth(text, maxWidth);
        }
        if (width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (width(sb.toString() + "...") > maxWidth) {
                sb.setLength(sb.length() - 1);
                return sb + "...";
            }
        }
        return sb.toString();
    }

    /** Quebra em linhas pelo espaço, mantendo a cor ativa no início de cada linha. */
    List<String> listFormattedStringToWidth(String text, int maxWidth) {
        if (!ready()) {
            return vanilla().listFormattedStringToWidth(text, maxWidth);
        }
        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        String carry = "";
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? carry + word : line + " " + word;
            if (width(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                carry = activeCodes(line.toString());
                line = new StringBuilder(carry + word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String activeCodes(String s) {
        String color = "", bold = "";
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '\u00a7') {
                char k = Character.toLowerCase(s.charAt(i + 1));
                if ("0123456789abcdefr".indexOf(k) >= 0) {
                    color = k == 'r' ? "" : "\u00a7" + k;
                    bold = "";
                } else if (k == 'l') {
                    bold = "\u00a7l";
                }
            }
        }
        return color + bold;
    }

    // ================================================================ desenho

    int drawStringWithShadow(String text, float x, float y, int color) {
        if (!ready()) {
            return vanilla().drawStringWithShadow(text, x, y, color);
        }
        if ((color & 0xFC000000) == 0) {
            color |= 0xFF000000;
        }
        int shadow = (color & 0xFCFCFC) >> 2 | (color & 0xFF000000);
        draw(text, x + 0.7F, y + 0.7F, shadow, true);
        return (int) draw(text, x, y, color, false);
    }

    private float draw(String text, float x, float y, int color, boolean isShadow) {
        if (text == null) {
            return x;
        }
        float alpha = (color >>> 24) / 255.0F;
        int rgb = color & 0xFFFFFF;
        boolean isBold = false;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableAlpha();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        Atlas current = null;
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char k = Character.toLowerCase(text.charAt(++i));
                int idx = "0123456789abcdef".indexOf(k);
                if (idx >= 0) {
                    rgb = isShadow ? (COLORS[idx] & 0xFCFCFC) >> 2 : COLORS[idx];
                    isBold = false;
                } else if (k == 'r') {
                    rgb = color & 0xFFFFFF;
                    isBold = false;
                } else if (k == 'l') {
                    isBold = true;
                }
                continue;
            }
            Atlas a = isBold ? bold : regular;
            if (a != current) {
                if (current != null) {
                    tess.draw();
                }
                current = a;
                GlStateManager.bindTexture(a.tex);
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            }
            int g = glyph(c);
            float gw = a.w[g] * a.scale, gh = a.h[g] * a.scale;
            float gx = cx - 2 * a.scale, gy = y - a.ascentOffset * a.scale;
            float u0 = a.u[g], v0 = a.v[g], u1 = u0 + a.w[g] / a.size, v1 = v0 + a.h[g] / a.size;
            int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, b = rgb & 255, al = (int) (alpha * 255 * RenderUtil.globalAlpha);
            wr.pos(gx, gy, 0).tex(u0, v0).color(r, gr, b, al).endVertex();
            wr.pos(gx, gy + gh, 0).tex(u0, v1).color(r, gr, b, al).endVertex();
            wr.pos(gx + gw, gy + gh, 0).tex(u1, v1).color(r, gr, b, al).endVertex();
            wr.pos(gx + gw, gy, 0).tex(u1, v0).color(r, gr, b, al).endVertex();
            if (isBold) {
                // Reforço de 1 pixel: o negrito da Comic fica fino demais em tamanho pequeno.
                float o = a.scale;
                wr.pos(gx + o, gy, 0).tex(u0, v0).color(r, gr, b, al).endVertex();
                wr.pos(gx + o, gy + gh, 0).tex(u0, v1).color(r, gr, b, al).endVertex();
                wr.pos(gx + gw + o, gy + gh, 0).tex(u1, v1).color(r, gr, b, al).endVertex();
                wr.pos(gx + gw + o, gy, 0).tex(u1, v0).color(r, gr, b, al).endVertex();
            }
            cx += a.advance[g] * a.scale + (isBold ? a.scale : 0);
        }
        if (current != null) {
            tess.draw();
        }
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return cx;
    }

    private static int glyph(char c) {
        return c >= FIRST && c <= LAST ? c : '?';
    }

    private static FontRenderer vanilla() {
        return Minecraft.getMinecraft().fontRendererObj;
    }
}
