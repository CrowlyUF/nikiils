package com.alan.clients.util.shader;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Decides whether Rise's framebuffer effects (blur, bloom) may run on this GPU.
 * <p>
 * They read and redraw the main framebuffer, so when the driver can't run them they don't just disappear: on GL4ES
 * (PojavLauncher) and on drivers that reject a shader they end up drawing over the world, leaving it black. They are
 * turned off automatically there; {@code -Drise.effects=on|off} forces either way.
 */
public final class ShaderSupport {

    private static boolean checked;
    private static boolean effects = true;
    private static String reason;

    private ShaderSupport() {
    }

    public static boolean effectsEnabled() {
        if (!checked) {
            checked = true;
            detect();
        }

        return effects;
    }

    /** Called when a Rise shader fails to compile or link. */
    public static void disable(final String why) {
        if (effects || !checked) {
            System.err.println("[Rise] Disabling blur/bloom effects: " + why);
        }

        checked = true;
        effects = false;
        reason = why;
    }

    public static String getReason() {
        return reason;
    }

    private static void detect() {
        final String forced = System.getProperty("rise.effects", "");

        if (forced.equalsIgnoreCase("on")) return;
        if (forced.equalsIgnoreCase("off")) {
            disable("disabled with -Drise.effects=off");
            return;
        }

        if (!OpenGlHelper.isFramebufferEnabled() || !OpenGlHelper.shadersSupported) {
            disable("framebuffers or shaders unavailable");
            return;
        }

        final String renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase();
        final String version = String.valueOf(GL11.glGetString(GL11.GL_VERSION)).toLowerCase();

        if (renderer.contains("gl4es") || version.contains("gl4es") || System.getenv("POJAV_RENDERER") != null
                || System.getProperty("pojav.path.minecraft") != null) {
            disable("PojavLauncher / GL4ES renderer (" + renderer + ")");
            return;
        }

        if (GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) < 4) {
            disable("not enough texture units");
        }
    }
}
