package com.nikiils.mushinfo.check;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FakeLag/Blink e Backtrack/Reach.
 *
 * FakeLag: o cheater segura os próprios pacotes de movimento e solta de uma vez. Para quem vê, ele
 * congela (o servidor para de mandar a posição dele) e depois "teleporta" o caminho todo de uma vez.
 * Descartamos congelamentos em que ninguém mais se mexeu (o lag é nosso ou do servidor) e jogadores
 * com ping alto (lag de verdade).
 *
 * Backtrack/Reach: o cheater atrasa os pacotes que recebe e bate onde o alvo estava há pouco (ou
 * simplesmente bate de longe). Veja {@link #onHurt}.
 */
final class CombatChecks {

    private static final int HISTORY = 20;
    private static final double LEGIT_REACH = 3.05;

    /** Pacotes de movimento de qualquer entidade (contados na thread do netty). */
    static final AtomicInteger MOVE_PACKETS = new AtomicInteger();

    private final Map<Integer, Victim> victims = new HashMap<Integer, Victim>();
    private int lastMovePackets;

    /** Histórico de posições (servidor) de um jogador qualquer, mais recente primeiro. */
    private static final class Victim {
        final ArrayDeque<double[]> positions = new ArrayDeque<double[]>();
        int lastHurtTime;
    }

    void tick(List<EntityPlayer> players, List<EntityPlayer> tracked, PlayerChecks checks, long now) {
        int packets = MOVE_PACKETS.get();
        boolean traffic = packets != lastMovePackets;
        lastMovePackets = packets;

        for (EntityPlayer p : tracked) {
            if (!(p instanceof EntityPlayerSP)) {
                checkFakeLag(p, checks.get(p), traffic, now);
            }
        }

        Set<Integer> present = new HashSet<Integer>();
        for (EntityPlayer victim : players) {
            present.add(victim.getEntityId());
            Victim v = victims.get(victim.getEntityId());
            if (v == null) {
                v = new Victim();
                v.lastHurtTime = victim.hurtTime;
                victims.put(victim.getEntityId(), v);
            }
            v.positions.addFirst(new double[]{PlayerChecks.posX(victim), PlayerChecks.posY(victim), PlayerChecks.posZ(victim)});
            while (v.positions.size() > HISTORY) {
                v.positions.removeLast();
            }
            // hurtTime sobe para 10 quando o jogador toma dano (pacote de status 2 do servidor).
            if (victim.hurtTime > v.lastHurtTime) {
                onHurt(victim, v, tracked, checks, now);
            }
            v.lastHurtTime = victim.hurtTime;
        }
        Iterator<Integer> it = victims.keySet().iterator();
        while (it.hasNext()) {
            if (!present.contains(it.next())) {
                it.remove();
            }
        }
    }

    void reset() {
        victims.clear();
    }

    // ---------------------------------------------------------------- FakeLag

    private void checkFakeLag(EntityPlayer p, PlayerStats s, boolean traffic, long now) {
        double x = PlayerChecks.posX(p), y = PlayerChecks.posY(p), z = PlayerChecks.posZ(p);
        if (!s.lagHasPos) {
            s.lagHasPos = true;
            s.lagX = x;
            s.lagY = y;
            s.lagZ = z;
            return;
        }
        double d = Math.sqrt((x - s.lagX) * (x - s.lagX) + (z - s.lagZ) * (z - s.lagZ));
        boolean moved = d > 1.0E-4 || Math.abs(y - s.lagY) > 1.0E-4;
        if (!moved) {
            s.frozenTicks++;
            if (traffic) {
                s.freezeTraffic++;
            }
            return;
        }
        if (s.frozenTicks >= 4) {
            double expected = s.preFreezeSpeed * s.frozenTicks;
            double trafficRatio = s.freezeTraffic / (double) s.frozenTicks;
            int ping = ping(p);
            // Estava andando, congelou e voltou "teleportando" o caminho que teria andado.
            if (s.preFreezeSpeed > 0.12 && d >= 1.0 && d >= expected * 0.5 && trafficRatio >= 0.6
                    && s.frozenTicks <= 60 && ping >= 0 && ping < 200) {
                boolean combat = now - s.lastSwing() < 3000L;
                int events = s.lagEvent(now, combat);
                if (events >= 3 || (combat && s.combatLagEvents(now) >= 2)) {
                    s.fakeLag.flag(now, String.format("congela %dms e pula %.1f blocos, ping %dms",
                            s.frozenTicks * 50, d, ping));
                    s.clearLagEvents();
                }
            }
        } else {
            s.preFreezeSpeed = s.preFreezeSpeed * 0.7 + d * 0.3;
        }
        s.frozenTicks = 0;
        s.freezeTraffic = 0;
        s.lagX = x;
        s.lagY = y;
        s.lagZ = z;
    }

    // ---------------------------------------------------------------- Backtrack / Reach

    /**
     * Mede cada hit de um jogador monitorado.
     *
     * Um jogador legit enxerga a vítima um pouco no passado (metade do ping dele + interpolação), e o
     * servidor aceita o hit nessa posição. Por isso o reach "compensado" é a MENOR distância entre o
     * atacante (últimos 3 ticks) e a vítima dentro dessa janela de atraso — é o caso mais favorável
     * ao atacante, então só sobra reach alto quando ele realmente bateu de longe.
     *
     * Backtrack: dentro da janela o hit não alcança, mas alcança uma posição bem mais antiga.
     */
    private void onHurt(EntityPlayer victim, Victim v, List<EntityPlayer> tracked, PlayerChecks checks, long now) {
        EntityPlayer attacker = null;
        float bestErr = 35.0F;
        for (EntityPlayer a : tracked) {
            long sinceSwing = now - checks.get(a).lastSwing();
            if (a == victim || sinceSwing > 200L || sinceSwing < 0) {
                continue;
            }
            double dx = PlayerChecks.posX(victim) - PlayerChecks.posX(a);
            double dz = PlayerChecks.posZ(victim) - PlayerChecks.posZ(a);
            if (dx * dx + dz * dz > 49) {
                continue;
            }
            float desired = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float err = Math.abs(MathHelper.wrapAngleTo180_float(desired - a.rotationYawHead));
            if (err < bestErr) {
                bestErr = err;
                attacker = a;
            }
        }
        if (attacker == null) {
            return;
        }
        Victim av = victims.get(attacker.getEntityId());
        double[][] vh = v.positions.toArray(new double[0][]);
        double[][] ah = av != null ? av.positions.toArray(new double[0][]) : new double[0][];
        if (vh.length < 3 || ah.length < 3) {
            return;
        }
        double eye = attacker.isSneaking() ? 1.54 : 1.62;

        int ping = ping(attacker);
        int window = Math.max(3, Math.min(10, (ping < 0 ? 150 : ping) / 50 + 3));
        window = Math.min(window, vh.length - 1);
        // Teleporte/lag no meio da janela deixa a medida sem sentido.
        for (int k = 0; k < Math.min(vh.length - 1, window + 6); k++) {
            if (dist(vh[k], vh[k + 1]) > 1.5) {
                return;
            }
        }

        double compensated = Double.MAX_VALUE;
        for (int k = 0; k <= window; k++) {
            compensated = Math.min(compensated, bestReach(ah, eye, vh[k]));
        }
        PlayerStats s = checks.get(attacker);
        s.addReach(compensated);

        if (compensated > 3.15) {
            int matchTick = -1;
            for (int k = window + 1; k < vh.length; k++) {
                if (bestReach(ah, eye, vh[k]) <= LEGIT_REACH) {
                    matchTick = k;
                    break;
                }
            }
            if (matchTick > 0) {
                if (s.backtrackHit(now) >= 3 && s.hits() >= 6) {
                    s.backtrack.flag(now, String.format("bate onde o alvo estava h\u00e1 ~%dms (ping %dms)",
                            matchTick * 50, Math.max(ping, 0)));
                }
                return;
            }
        }
        if (compensated > 3.25) {
            // Precisa repetir e ser uma fatia relevante dos hits (um hit estranho é normal com lag).
            if (s.farHit(now) >= 3 && s.hits() >= 8 && s.reachAbove(3.2) >= Math.max(3, s.hits() * 3 / 10)) {
                s.reach.flag(now, String.format("%.2f blocos, %d de %d hits acima de 3.2",
                        compensated, s.reachAbove(3.2), s.hits()));
            }
        }
    }

    /** Menor reach entre as últimas 3 posições do atacante e uma posição da vítima. */
    private static double bestReach(double[][] attacker, double eye, double[] victim) {
        double best = Double.MAX_VALUE;
        for (int j = 0; j < Math.min(3, attacker.length); j++) {
            best = Math.min(best, reach(attacker[j][0], attacker[j][1] + eye, attacker[j][2], victim));
        }
        return best;
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distância do olho do atacante até a hitbox da vítima (com a margem de 0.1 do vanilla). */
    private static double reach(double ex, double ey, double ez, double[] pos) {
        double cx = clamp(ex, pos[0] - 0.4, pos[0] + 0.4);
        double cy = clamp(ey, pos[1] - 0.1, pos[1] + 1.9);
        double cz = clamp(ez, pos[2] - 0.4, pos[2] + 0.4);
        return Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }

    private static int ping(EntityPlayer p) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(p.getUniqueID()) : null;
        return info == null ? -1 : info.getResponseTime();
    }
}
