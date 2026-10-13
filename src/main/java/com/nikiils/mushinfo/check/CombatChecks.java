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
 * Backtrack: o cheater atrasa os pacotes que recebe e bate onde o alvo estava há pouco. Em cada hit
 * medimos o reach até a posição atual da vítima e até as posições dos últimos ticks; se o hit só
 * é possível numa posição bem mais antiga do que o ping dele explica, é Backtrack.
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

    private void onHurt(EntityPlayer victim, Victim v, List<EntityPlayer> tracked, PlayerChecks checks, long now) {
        EntityPlayer attacker = null;
        float bestErr = 35.0F;
        for (EntityPlayer a : tracked) {
            if (a == victim || now - checks.get(a).lastSwing() > 250L) {
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
        PlayerStats s = checks.get(attacker);
        double ex = PlayerChecks.posX(attacker);
        double ey = PlayerChecks.posY(attacker) + (attacker.isSneaking() ? 1.54 : 1.62);
        double ez = PlayerChecks.posZ(attacker);

        double[][] hist = v.positions.toArray(new double[0][]);
        if (hist.length == 0) {
            return;
        }
        double current = reach(ex, ey, ez, hist[0]);
        s.addReach(current);

        if (current <= 3.3) {
            return;
        }
        // Atraso que um jogador legit teria: metade do ping + ~2 ticks de interpolação.
        int ping = ping(attacker);
        int expectedTicks = (ping < 0 ? 200 : ping) / 50 + 2;
        int matchTick = -1;
        for (int k = 1; k < hist.length; k++) {
            if (reach(ex, ey, ez, hist[k]) <= LEGIT_REACH) {
                matchTick = k;
                break;
            }
        }
        if (matchTick > expectedTicks + 2) {
            if (s.backtrackHit(now) >= 3) {
                s.backtrack.flag(now, String.format("bate onde o alvo estava há ~%dms", matchTick * 50));
            }
        } else if (matchTick < 0) {
            // Longe até das posições antigas: reach puro.
            if (s.farHit(now) >= 3) {
                s.backtrack.flag(now, String.format("hit a %.2f blocos", current));
            }
        }
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
