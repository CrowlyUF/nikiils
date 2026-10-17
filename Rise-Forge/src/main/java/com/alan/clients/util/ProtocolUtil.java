package com.alan.clients.util;

import java.lang.reflect.Method;

/**
 * Replaces Rise's built-in ViaMCP. In the Forge build, multi-version support comes from ViaForge if it is installed;
 * its target protocol is read reflectively so the mod has no hard dependency on it.
 */
public final class ProtocolUtil {

    public static final int NATIVE_PROTOCOL = 47; // 1.8.x

    private static Method getManager, getTargetVersion, getVersion;
    private static boolean lookedUp;

    private ProtocolUtil() {
    }

    private static int cachedProtocol = NATIVE_PROTOCOL;
    private static long cachedAt;

    /**
     * Called from hot paths (entity hitbox size, ray traces), so the reflective lookup is cached for a second.
     */
    public static int getTargetProtocol() {
        final long now = System.currentTimeMillis();

        if (now - cachedAt > 1000L) {
            cachedAt = now;
            cachedProtocol = lookupProtocol();
        }

        return cachedProtocol;
    }

    private static int lookupProtocol() {
        try {
            if (!lookedUp) {
                lookedUp = true;
                final Class<?> common = Class.forName("de.florianmichael.viaforge.common.ViaForgeCommon");
                getManager = common.getMethod("getManager");
            }

            if (getManager == null) return NATIVE_PROTOCOL;

            final Object manager = getManager.invoke(null);
            if (manager == null) return NATIVE_PROTOCOL;

            if (getTargetVersion == null) getTargetVersion = manager.getClass().getMethod("getTargetVersion");
            final Object version = getTargetVersion.invoke(manager);

            if (getVersion == null) getVersion = version.getClass().getMethod("getVersion");
            return (int) getVersion.invoke(version);
        } catch (final Throwable ignored) {
            getManager = null;
            return NATIVE_PROTOCOL;
        }
    }

    public static final int V1_11 = 315, V1_17 = 755;

    public static boolean atLeast(final int protocol) {
        return getTargetProtocol() >= protocol;
    }

    public static boolean isNewerThan1_8() {
        return getTargetProtocol() > NATIVE_PROTOCOL;
    }
}
