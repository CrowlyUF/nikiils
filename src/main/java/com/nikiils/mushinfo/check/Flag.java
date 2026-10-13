package com.nikiils.mushinfo.check;

import java.util.List;

/** Um alerta de heurística: quantas vezes disparou e o último motivo. */
public final class Flag {

    /** Tempo que o alerta continua visível depois do último disparo. */
    private static final long SHOW_MS = 30_000L;

    private int count;
    private long last;
    private String reason = "";

    public synchronized void flag(long now, String reason) {
        count++;
        last = now;
        this.reason = reason;
    }

    synchronized void describe(String name, long now, List<String> out) {
        if (count > 0 && now - last <= SHOW_MS) {
            out.add(name + " x" + count + " (" + reason + ")");
        }
    }
}
