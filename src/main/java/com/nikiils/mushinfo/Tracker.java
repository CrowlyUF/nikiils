package com.nikiils.mushinfo;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Jogadores marcados com ".info <nick>" (chave em minúsculas, valor com a grafia digitada). */
public final class Tracker {

    private static final Map<String, String> TRACKED = Collections.synchronizedMap(new LinkedHashMap<String, String>());

    private Tracker() {
    }

    /** Alterna o jogador; retorna true se passou a ser monitorado. */
    public static boolean toggle(String name) {
        String key = name.toLowerCase();
        synchronized (TRACKED) {
            if (TRACKED.remove(key) != null) {
                return false;
            }
            TRACKED.put(key, name);
            return true;
        }
    }

    public static void remove(String name) {
        TRACKED.remove(name.toLowerCase());
    }

    public static boolean isTracked(String name) {
        return TRACKED.containsKey(name.toLowerCase());
    }

    public static Collection<String> names() {
        synchronized (TRACKED) {
            return new java.util.ArrayList<String>(TRACKED.values());
        }
    }

    public static void clear() {
        TRACKED.clear();
    }
}
