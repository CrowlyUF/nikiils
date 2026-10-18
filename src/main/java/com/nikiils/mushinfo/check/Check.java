package com.nikiils.mushinfo.check;

/** Todos os checks. {@code experimental} = sinal mais fraco, mais sujeito a falso positivo. */
public enum Check {
    AUTOCLICK("AutoClick", true, "Clica no limite 30 s seguidos sem pausa"),
    AUTOBLOCK("AutoBlock", true, "Bate e defende ao mesmo tempo"),
    AIMASSIST("AimAssist", true, "Mira grudada ou giro brusco no alvo"),
    REACH("Reach", false, "Hits acima de 3.25 blocos, repetidos"),
    BACKTRACK("Backtrack", true, "Bate onde o alvo estava antes"),
    BLINK("Blink/FakeLag", false, "Congela e depois teleporta"),
    VELOCITY("Velocity", false, "Toma hit e n\u00e3o leva knockback"),
    SPEED("Speed", false, "R\u00e1pido demais sem po\u00e7\u00e3o"),
    NOFALL("NoFall", false, "Cai de alto e n\u00e3o toma dano"),
    NOSLOW("NoSlow", false, "Anda r\u00e1pido usando item"),
    SAFEWALK("SafeWalk", false, "Para seco na quina sem agachar"),
    SCAFFOLD("Scaffold", false, "Ponte ou tower imposs\u00edvel"),
    EAGLE("Eagle/LegitScaffold", true, "Agacha exatamente no clique"),
    FASTMINE("FastMine", true, "Quebra o bloco cedo demais"),
    NUKER("Nuker", false, "Quebra bloco coberto"),
    BEDNUKER("BedNuker", false, "Quebra cama coberta"),
    PROTOCOL("Protocol", false, "Rota\u00e7\u00e3o imposs\u00edvel");

    public final String displayName;
    public final boolean experimental;
    public final String description;

    Check(String displayName, boolean experimental, String description) {
        this.displayName = displayName;
        this.experimental = experimental;
        this.description = description;
    }
}
