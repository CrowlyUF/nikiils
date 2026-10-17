package com.alan.clients.util.render;

import com.alan.clients.ui.theme.Themes;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.SubMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import java.awt.*;

/**
 * Shared colour resolver used across the render modules so that every module exposes the same
 * "Color Mode" options (Theme / Custom / Health / Rainbow / Distance) and resolves them identically.
 *
 * <p>Reuses the existing primitives in {@link ColorUtil} and {@link Themes} rather than introducing
 * new colour maths.</p>
 */
public final class EntityColors {

    /** Canonical ordering of the colour modes. Used both for the switch below and the GUI dropdown. */
    public static final String THEME = "Theme";
    public static final String CUSTOM = "Custom";
    public static final String HEALTH = "Health";
    public static final String RAINBOW = "Rainbow";
    public static final String DISTANCE = "Distance";

    public static final String[] MODES = {THEME, CUSTOM, HEALTH, RAINBOW, DISTANCE};

    private EntityColors() {
    }

    /**
     * Populates the given {@link ModeValue} with the standard colour-mode submodes and defaults it to
     * {@link #THEME}. Call with a freshly constructed {@code ModeValue} owning the desired parent, e.g.
     * {@code EntityColors.colorMode(new ModeValue("Color Mode", this))}.
     */
    public static ModeValue colorMode(final ModeValue value) {
        for (final String mode : MODES) {
            value.add(new SubMode(mode));
        }
        return value.setDefault(THEME);
    }

    /**
     * Resolves a colour for the given mode.
     *
     * @param mode         one of {@link #MODES}
     * @param custom       the user-picked colour, used by {@link #CUSTOM}
     * @param theme        the active client theme
     * @param entity       the entity being coloured (may be {@code null} for HUD elements)
     * @param screenY      a screen-space Y used to drive the {@link #THEME} gradient
     * @param rainbowDelay per-element phase offset (ms) so multiple elements don't pulse in lockstep
     * @param maxDistance  the range (blocks) over which {@link #DISTANCE} fades from near to far
     */
    public static Color resolve(final String mode, final Color custom, final Themes theme,
                                final EntityLivingBase entity, final double screenY,
                                final int rainbowDelay, final double maxDistance) {
        if (mode == null) {
            return theme.getAccentColor(new Vector2d(0, screenY));
        }

        switch (mode) {
            case CUSTOM:
                return custom;

            case HEALTH: {
                if (entity == null || entity.getMaxHealth() <= 0) {
                    return theme.getFirstColor();
                }
                final float pct = Math.max(0f, Math.min(1f, entity.getHealth() / entity.getMaxHealth()));
                // green (full) -> red (empty), matching the existing 2D ESP health colour pattern
                return Color.getHSBColor(pct / 3f, 1f, 1f);
            }

            case RAINBOW:
                return ColorUtil.rainbow(rainbowDelay);

            case DISTANCE: {
                final Minecraft mc = Minecraft.getMinecraft();
                double t = 0;
                if (entity != null && mc.thePlayer != null && maxDistance > 0) {
                    t = Math.min(1.0, mc.thePlayer.getDistanceToEntity(entity) / maxDistance);
                }
                // near -> theme first colour, far -> theme second colour
                return ColorUtil.mixColors(theme.getSecondColor(), theme.getFirstColor(), t);
            }

            case THEME:
            default:
                return theme.getAccentColor(new Vector2d(0, screenY));
        }
    }

    /**
     * Convenience overload for HUD elements that have no entity (drives the Theme gradient by {@code screenY}).
     */
    public static Color resolve(final String mode, final Color custom, final Themes theme,
                                final double screenY, final int rainbowDelay) {
        return resolve(mode, custom, theme, null, screenY, rainbowDelay, 1);
    }
}
