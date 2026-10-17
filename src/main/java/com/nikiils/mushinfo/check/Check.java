package com.nikiils.mushinfo.check;

/** Todos os checks. {@code experimental} = sinal mais fraco, mais sujeito a falso positivo. */
public enum Check {
    AUTOCLICK("AutoClick", true),
    AUTOBLOCK("AutoBlock", false),
    AIMASSIST("AimAssist", true),
    REACH("Reach", false),
    BACKTRACK("Backtrack", true),
    BLINK("Blink/FakeLag", false),
    VELOCITY("Velocity", false),
    SPEED("Speed", false),
    NOFALL("NoFall", false),
    NOSLOW("NoSlow", false),
    SAFEWALK("SafeWalk", false),
    SCAFFOLD("Scaffold", false),
    EAGLE("Eagle/LegitScaffold", true),
    FASTMINE("FastMine", true),
    NUKER("Nuker", false),
    BEDNUKER("BedNuker", false),
    PROTOCOL("Protocol", false);

    public final String displayName;
    public final boolean experimental;

    Check(String displayName, boolean experimental) {
        this.displayName = displayName;
        this.experimental = experimental;
    }
}
