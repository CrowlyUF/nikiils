package com.nikiils.mushinfo.check;

/** Um disparo de check guardado no histórico do jogador. */
public final class Flag {

    public final Check check;
    public final long time;
    public final String reason;

    Flag(Check check, long time, String reason) {
        this.check = check;
        this.time = time;
        this.reason = reason;
    }
}
