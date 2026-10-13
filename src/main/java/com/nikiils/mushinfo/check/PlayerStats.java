package com.nikiils.mushinfo.check;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Dados coletados de um jogador (por id de entidade). */
public final class PlayerStats {

    private static final int CPS_SAMPLES = 10;
    private static final int SPEED_SAMPLES = 5;

    private final ArrayDeque<Long> swings = new ArrayDeque<Long>();
    private final ArrayDeque<Integer> cpsSamples = new ArrayDeque<Integer>();
    private final ArrayDeque<Double> speeds = new ArrayDeque<Double>();
    private long lastSample;
    private int maxCps;

    final Flag autoClick = new Flag();
    final Flag safeWalk = new Flag();
    final Flag noSlow = new Flag();
    final Flag scaffold = new Flag();
    final Flag aimAssist = new Flag();
    final Flag fakeLag = new Flag();
    final Flag backtrack = new Flag();

    // FakeLag
    boolean lagHasPos;
    double lagX;
    double lagY;
    double lagZ;
    int frozenTicks;
    int freezeTraffic;
    double preFreezeSpeed;
    /** Congelamentos suspeitos: {tempo, em combate (1/0)}. */
    private final ArrayDeque<long[]> lagEvents = new ArrayDeque<long[]>();

    // Backtrack / Reach
    private final ArrayDeque<Double> reaches = new ArrayDeque<Double>();
    private final ArrayDeque<Long> backtrackHits = new ArrayDeque<Long>();
    private final ArrayDeque<Long> farHits = new ArrayDeque<Long>();

    /** Blocos colocados: {tempo, pitch, embaixo (1/0)}. */
    private final ArrayDeque<double[]> placed = new ArrayDeque<double[]>();
    private final ArrayDeque<Double> yHistory = new ArrayDeque<Double>();
    private final ArrayDeque<Float> aimErrors = new ArrayDeque<Float>();
    private final ArrayDeque<Long> snaps = new ArrayDeque<Long>();
    boolean newPlacement;
    float lastYaw;
    float lastDesired;
    Object lastTarget;

    long lastSeen;
    int edgeTicks;
    boolean edgeLookingDown;
    int usingTicks;
    int noSlowTicks;
    boolean hasLastPos;
    double lastX;
    double lastZ;

    synchronized void addSwing(long time) {
        swings.addLast(time);
        while (swings.size() > 200) {
            swings.removeFirst();
        }
    }

    synchronized long lastSwing() {
        return swings.isEmpty() ? 0 : swings.peekLast();
    }

    /** Cliques no último segundo. */
    public synchronized int cps(long now) {
        int n = 0;
        Iterator<Long> it = swings.descendingIterator();
        while (it.hasNext() && now - it.next() <= 1000L) {
            n++;
        }
        return n;
    }

    /** Maior CPS medido nas últimas amostras (~10 s). */
    public synchronized int maxCps() {
        return maxCps;
    }

    /**
     * A cada segundo guarda o CPS e verifica se os cliques estão constantes demais. O servidor agrupa
     * os swings por tick (50 ms), então intervalos entre cliques não são confiáveis; a variação do CPS
     * segundo a segundo é. Humanos variam bastante; autoclickers ficam quase fixos.
     */
    synchronized void sampleCps(long now) {
        while (!swings.isEmpty() && now - swings.peekFirst() > 10_000L) {
            swings.removeFirst();
        }
        if (now - lastSample < 1000L) {
            return;
        }
        lastSample = now;
        int cps = cps(now);
        if (cps < 5) {
            cpsSamples.clear();
        } else {
            cpsSamples.addLast(cps);
            while (cpsSamples.size() > CPS_SAMPLES) {
                cpsSamples.removeFirst();
            }
        }
        maxCps = 0;
        for (int c : cpsSamples) {
            maxCps = Math.max(maxCps, c);
        }
        maxCps = Math.max(maxCps, cps);

        if (cps >= 20) {
            autoClick.flag(now, cps + " CPS");
        } else if (cpsSamples.size() >= 8) {
            double mean = 0;
            for (int c : cpsSamples) {
                mean += c;
            }
            mean /= cpsSamples.size();
            double var = 0;
            for (int c : cpsSamples) {
                var += (c - mean) * (c - mean);
            }
            double sd = Math.sqrt(var / cpsSamples.size());
            if (mean >= 10 && sd < 0.75) {
                autoClick.flag(now, String.format("CPS constante %.1f±%.2f", mean, sd));
                cpsSamples.clear();
            }
        }
    }

    void pushSpeed(double speed) {
        speeds.addLast(speed);
        while (speeds.size() > SPEED_SAMPLES) {
            speeds.removeFirst();
        }
    }

    double averageSpeed() {
        double sum = 0;
        for (double s : speeds) {
            sum += s;
        }
        return speeds.isEmpty() ? 0 : sum / speeds.size();
    }

    void addPlacement(long time, float pitch, boolean under) {
        placed.addLast(new double[]{time, pitch, under ? 1 : 0});
        while (placed.size() > 40) {
            placed.removeFirst();
        }
        newPlacement = true;
    }

    void clearPlacements() {
        placed.clear();
    }

    int placementsSince(long since) {
        int n = 0;
        for (double[] p : placed) {
            if (p[0] >= since) {
                n++;
            }
        }
        return n;
    }

    int forwardPlacementsSince(long since, float maxPitch) {
        int n = 0;
        for (double[] p : placed) {
            if (p[0] >= since && p[1] < maxPitch) {
                n++;
            }
        }
        return n;
    }

    int underPlacementsSince(long since) {
        int n = 0;
        for (double[] p : placed) {
            if (p[0] >= since && p[2] == 1) {
                n++;
            }
        }
        return n;
    }

    void pushY(double y) {
        yHistory.addLast(y);
        while (yHistory.size() > 20) {
            yHistory.removeFirst();
        }
    }

    /** Quanto o jogador subiu nos últimos {@code ticks} ticks (20 ticks = 1 s). */
    double riseOver(int ticks) {
        if (yHistory.size() < ticks) {
            return 0;
        }
        return yHistory.peekLast() - yHistory.peekFirst();
    }

    void addAimSample(float err) {
        aimErrors.addLast(err);
        while (aimErrors.size() > 60) {
            aimErrors.removeFirst();
        }
    }

    int aimSamples() {
        return aimErrors.size();
    }

    void clearAim() {
        aimErrors.clear();
    }

    /** Erro médio da mira em graus, ou -1 se ainda não há amostras suficientes. */
    public float aimError() {
        if (aimErrors.size() < 10) {
            return -1;
        }
        float sum = 0;
        for (float e : aimErrors) {
            sum += e;
        }
        return sum / aimErrors.size();
    }

    /** Registra um giro brusco e retorna quantos houve nos últimos 10 s. */
    int snap(long now) {
        snaps.addLast(now);
        while (!snaps.isEmpty() && now - snaps.peekFirst() > 10_000L) {
            snaps.removeFirst();
        }
        return snaps.size();
    }

    /** Registra um congelamento suspeito e retorna quantos houve nos últimos 15 s. */
    int lagEvent(long now, boolean combat) {
        lagEvents.addLast(new long[]{now, combat ? 1 : 0});
        while (!lagEvents.isEmpty() && now - lagEvents.peekFirst()[0] > 15_000L) {
            lagEvents.removeFirst();
        }
        return lagEvents.size();
    }

    int combatLagEvents(long now) {
        int n = 0;
        for (long[] e : lagEvents) {
            if (e[1] == 1 && now - e[0] <= 15_000L) {
                n++;
            }
        }
        return n;
    }

    void clearLagEvents() {
        lagEvents.clear();
    }

    void addReach(double reach) {
        reaches.addLast(reach);
        while (reaches.size() > 20) {
            reaches.removeFirst();
        }
    }

    /** {média, máximo} do reach nos últimos hits, ou null se houver menos de 3. */
    public double[] reachStats() {
        if (reaches.size() < 3) {
            return null;
        }
        double sum = 0, max = 0;
        for (double r : reaches) {
            sum += r;
            max = Math.max(max, r);
        }
        return new double[]{sum / reaches.size(), max};
    }

    int backtrackHit(long now) {
        return count(backtrackHits, now, 20_000L);
    }

    int farHit(long now) {
        return count(farHits, now, 20_000L);
    }

    private static int count(ArrayDeque<Long> q, long now, long window) {
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > window) {
            q.removeFirst();
        }
        return q.size();
    }
}
