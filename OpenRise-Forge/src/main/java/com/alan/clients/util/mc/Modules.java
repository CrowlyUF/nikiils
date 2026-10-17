package com.alan.clients.util.mc;

import com.alan.clients.Client;
import com.alan.clients.module.Module;

/**
 * Null-safe module lookups for mixins, which can run before the client has initialised.
 */
public final class Modules {

    private Modules() {
    }

    public static <T extends Module> T get(final Class<T> clazz) {
        return Client.INSTANCE.getModuleManager() == null ? null : Client.INSTANCE.getModuleManager().get(clazz);
    }

    public static boolean isEnabled(final Class<? extends Module> clazz) {
        final Module module = get(clazz);
        return module != null && module.isEnabled();
    }
}
