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
}
