package com.alan.clients.util.render;

import java.awt.*;

/**
 * Shared background renderer for the simple HUD counters (CPS / FPS / BPS) so they all expose the
 * same "Style" options and look consistent. Draws into the current layer; the caller is responsible
 * for the BLUR/BLOOM passes.
 */
public final class HudBackground {

    public static final String ROUNDED = "Rounded";
    public static final String OUTLINE = "Outline";
    public static final String GRADIENT = "Gradient";
    public static final String MINIMAL = "Minimal";

    public static final String[] STYLES = {ROUNDED, OUTLINE, GRADIENT, MINIMAL};

    private HudBackground() {
    }

    /**
     * Draws the styled background and optional left accent bar.
     *
     * @return the extra left padding (px) that the text should be shifted by to clear the accent bar
     */
    public static double draw(final String style, final double x, final double y, final double w, final double h,
                              final double round, final Color bgShade, final Color accent, final boolean accentBar) {
        switch (style) {
            case OUTLINE:
                RenderUtil.roundedRectangle(x, y, w, h, round, bgShade);
                RenderUtil.roundedOutlineGradientRectangle(x, y, w, h, round, 1.0, accent,
                        ColorUtil.mixColors(accent, bgShade, 0.5));
                break;

            case GRADIENT:
                RenderUtil.drawRoundedGradientRect(x, y, w, h, round,
                        ColorUtil.withAlpha(accent, 110),
                        ColorUtil.withAlpha(ColorUtil.darker(accent, 0.4f), 150), true);
                break;

            case MINIMAL:
                // text only, with a thin accent underline — no filled body
                RenderUtil.roundedRectangle(x, y + h - 1.5, w, 1.5, 0.75, accent);
                return 0;

            case ROUNDED:
            default:
                RenderUtil.roundedRectangle(x, y, w, h, round, bgShade);
                break;
        }

        if (accentBar) {
            RenderUtil.roundedRectangle(x + 2, y + 3, 1.5, h - 6, 0.75, accent);
            return 5;
        }

        return 0;
    }
}
