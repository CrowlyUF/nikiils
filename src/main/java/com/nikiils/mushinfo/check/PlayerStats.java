package com.nikiils.mushinfo.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Tudo que o mod sabe de um jogador (por nick, sobrevive a respawn e troca de mundo). */
public final class PlayerStats {

    /**
     * O servidor 1.8 só repassa o swing de outro jogador quando a animação anterior passou da metade
     * (3 de 6 ticks), então de fora nunca se vê mais que ~6-7 swings/s, por mais rápido que ele clique.
     * A partir disso o CPS real é desconhecido ("7+").
     */
    public static final int SWING_CAP = 6;
    /** Tempo que um alerta continua "ativo" (card e destaque na GUI) depois do último disparo. */
    public static final long ACTIVE_MS = 30_000L;
    private static final int HISTORY = 60;

    public final String name;
    volatile int entityId = -1;
    long lastSeen;

    // ---------------------------------------------------------------- flags
    private final Map<Check, Integer> counts = new EnumMap<Check, Integer>(Check.class);
    private final Map<Check, Flag> lastFlags = new EnumMap<Check, Flag>(Check.class);
    private final ArrayDeque<Flag> history = new ArrayDeque<Flag>();

    // ---------------------------------------------------------------- CPS
    private final ArrayDeque<Long> swings = new ArrayDeque<Long>();
    private final ArrayDeque<Integer> cpsSamples = new ArrayDeque<Integer>();
    private long lastSample;
    private int maxCps;
    private int saturatedSeconds;

    // ---------------------------------------------------------------- movimento
    boolean hasPos;
    double lastX, lastY, lastZ;
    private final ArrayDeque<double[]> moves = new ArrayDeque<double[]>(); // {dx, dz, dy, onGround}
    int edgeTicks;
    boolean edgeLookingDown;
    int usingTicks, noSlowTicks, sprintUseTicks, ticksSinceStopUsing = 100;
    boolean wasUsing;
    int speedTicks;
    int ticksSinceHurt = 100;
    int lastHurtTime;
    int ticksSinceIce = 100;
    double fallStartY = Double.NaN;
    int landedTicksAgo = -1;
    double landedFall;
    int noFallCandidates;
    long lastNoFallCandidate;

    // ---------------------------------------------------------------- rotação / swing
    float lastYaw, lastPitch, lastDesired;
    Object lastTarget;
    int yawOnlyTicks, ticksSincePitch;
    int swingBlockTicks;
    boolean wasSneaking;
    int perfectSneaks, ticksSinceSneak = 100, ticksSinceDeltaY = 100, horizontalTicks, scaffoldTicks;
    long lastBreakAnim;
    private final ArrayDeque<Float> aimErrors = new ArrayDeque<Float>();
    private final ArrayDeque<Long> snaps = new ArrayDeque<Long>();

    // ---------------------------------------------------------------- blocos
    private final ArrayDeque<double[]> placed = new ArrayDeque<double[]>(); // {tempo, pitch, embaixo}
    private final ArrayDeque<Double> yHistory = new ArrayDeque<Double>();
    boolean newPlacement;
    private final ArrayDeque<Long> fastMines = new ArrayDeque<Long>();

    // ---------------------------------------------------------------- combate
    boolean lagHasPos;
    double lagX, lagY, lagZ, preFreezeSpeed;
    int frozenTicks, freezeTraffic;
    private final ArrayDeque<long[]> lagEvents = new ArrayDeque<long[]>();
    private final ArrayDeque<Double> reaches = new ArrayDeque<Double>();
    private final ArrayDeque<Long> backtrackHits = new ArrayDeque<Long>();
    private final ArrayDeque<Long> farHits = new ArrayDeque<Long>();
    /** Resultado dos últimos hits recebidos: true = sem knockback. */
    private final ArrayDeque<Boolean> kbResults = new ArrayDeque<Boolean>();

    PlayerStats(String name) {
        this.name = name;
    }

    // ================================================================ flags

    /** Registra um disparo. Disparos do mesmo check em menos de 1 s contam como um só. */
    synchronized boolean flag(Check check, long now, String reason) {
        Flag last = lastFlags.get(check);
        Flag f = new Flag(check, now, reason);
        lastFlags.put(check, f);
        if (last != null && now - last.time < 1000L) {
            return false;
        }
        Integer c = counts.get(check);
        counts.put(check, c == null ? 1 : c + 1);
        history.addFirst(f);
        while (history.size() > HISTORY) {
            history.removeLast();
        }
        Alerts.onFlag(this, f, counts.get(check));
        return true;
    }

    public synchronized int count(Check check) {
        Integer c = counts.get(check);
        return c == null ? 0 : c;
    }

    public synchronized int totalFlags() {
        int n = 0;
        for (int c : counts.values()) {
            n += c;
        }
        return n;
    }

    public synchronized Flag lastFlag(Check check) {
        return lastFlags.get(check);
    }

    public synchronized List<Flag> history() {
        return new ArrayList<Flag>(history);
    }

    /** Checks que dispararam nos últimos 30 s, com contagem e último motivo. */
    public synchronized List<String> activeAlerts(long now) {
        List<String> out = new ArrayList<String>();
        for (Check c : Check.values()) {
            Flag f = lastFlags.get(c);
            if (f != null && now - f.time <= ACTIVE_MS) {
                out.add(c.displayName + " x" + count(c) + " (" + f.reason + ")");
            }
        }
        return out;
    }

    public synchronized boolean hasActiveAlert(long now) {
        for (Flag f : lastFlags.values()) {
            if (now - f.time <= ACTIVE_MS) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ CPS

    synchronized void addSwing(long time) {
        swings.addLast(time);
        while (swings.size() > 200) {
            swings.removeFirst();
        }
    }

    public synchronized long lastSwing() {
        return swings.isEmpty() ? 0 : swings.peekLast();
    }

    /** Swings no último segundo. */
    public synchronized int cps(long now) {
        int n = 0;
        Iterator<Long> it = swings.descendingIterator();
        while (it.hasNext() && now - it.next() <= 1000L) {
            n++;
        }
        return n;
    }

    public synchronized int maxCps() {
        return maxCps;
    }

    /**
     * A cada segundo guarda o CPS. Para o próprio jogador (cliques locais, sem limite) verifica CPS alto
     * ou constante demais. Para os outros, como o servidor corta em ~7, só dá para ver que ele está no
     * limite: ficar no limite 30 s seguidos sem nenhuma pausa é o padrão de quem segura um autoclick.
     * {@code exempt}: minerando ou com vara de pesca (swing contínuo legítimo).
     */
    synchronized void sampleCps(long now, boolean self, boolean exempt) {
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
            while (cpsSamples.size() > 10) {
                cpsSamples.removeFirst();
            }
        }
        maxCps = cps;
        for (int c : cpsSamples) {
            maxCps = Math.max(maxCps, c);
        }

        if (!self) {
            saturatedSeconds = cps >= SWING_CAP && !exempt ? saturatedSeconds + 1 : 0;
            if (saturatedSeconds > 0 && saturatedSeconds % 30 == 0) {
                flag(Check.AUTOCLICK, now, "clique no limite " + saturatedSeconds + "s sem parar");
            }
            return;
        }
        if (cps >= 20) {
            flag(Check.AUTOCLICK, now, cps + " CPS");
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
                flag(Check.AUTOCLICK, now, String.format("CPS constante %.1f±%.2f", mean, sd));
                cpsSamples.clear();
            }
        }
    }

    // ================================================================ movimento

    /** Guarda o deslocamento do tick (posição do servidor). */
    void pushMove(double dx, double dy, double dz, boolean onGround) {
        moves.addFirst(new double[]{dx, dz, dy, onGround ? 1 : 0});
        while (moves.size() > 20) {
            moves.removeLast();
        }
    }

    /** Velocidade horizontal média dos últimos {@code ticks} ticks. */
    double averageSpeed(int ticks) {
        double sum = 0;
        int n = 0;
        for (double[] m : moves) {
            if (n == ticks) {
                break;
            }
            sum += Math.hypot(m[0], m[1]);
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    int movesCount() {
        return moves.size();
    }

    double maxStep(int ticks) {
        double max = 0;
        int n = 0;
        for (double[] m : moves) {
            if (n++ == ticks) {
                break;
            }
            max = Math.max(max, Math.hypot(m[0], m[1]));
        }
        return max;
    }

    int groundTicks(int ticks) {
        int g = 0, n = 0;
        for (double[] m : moves) {
            if (n++ == ticks) {
                break;
            }
            g += (int) m[3];
        }
        return g;
    }

    void clearMoves() {
        moves.clear();
    }

    // ================================================================ rotação

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

    int snap(long now) {
        return count(snaps, now, 10_000L);
    }

    // ================================================================ blocos

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

    double riseOver(int ticks) {
        if (yHistory.size() < ticks) {
            return 0;
        }
        return yHistory.peekLast() - yHistory.peekFirst();
    }

    int fastMine(long now) {
        return count(fastMines, now, 30_000L);
    }

    // ================================================================ combate

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

    synchronized void addReach(double reach) {
        reaches.addLast(reach);
        while (reaches.size() > 20) {
            reaches.removeFirst();
        }
    }

    /** {média, máximo} do reach nos últimos hits, ou null se houver menos de 3. */
    public synchronized double[] reachStats() {
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

    synchronized int hits() {
        return reaches.size();
    }

    synchronized int reachAbove(double threshold) {
        int n = 0;
        for (double r : reaches) {
            if (r > threshold) {
                n++;
            }
        }
        return n;
    }

    int backtrackHit(long now) {
        return count(backtrackHits, now, 20_000L);
    }

    int farHit(long now) {
        return count(farHits, now, 20_000L);
    }

    /** Registra se o hit recebido teve knockback; retorna {sem KB, total} dos últimos 10. */
    int[] addKnockback(boolean none) {
        kbResults.addLast(none);
        while (kbResults.size() > 10) {
            kbResults.removeFirst();
        }
        int n = 0;
        for (boolean b : kbResults) {
            if (b) {
                n++;
            }
        }
        return new int[]{n, kbResults.size()};
    }

    private static int count(ArrayDeque<Long> q, long now, long window) {
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > window) {
            q.removeFirst();
        }
        return q.size();
    }
}
