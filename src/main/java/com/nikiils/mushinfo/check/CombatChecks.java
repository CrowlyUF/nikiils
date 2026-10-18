package com.nikiils.mushinfo.check;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blink/FakeLag, Reach/Backtrack e Velocity (anti-knockback).
 *
 * Blink: o cheater segura os próprios pacotes de movimento e solta de uma vez. Para quem vê, ele
 * congela (o servidor para de mandar a posição dele) e depois "teleporta" o caminho todo. Descartamos
 * congelamentos em que ninguém mais se mexeu (o lag é nosso ou do servidor) e ping alto (lag real).
 *
 * Reach/Backtrack: veja {@link #onHurt}. Velocity: depois de um hit corpo a corpo a vítima tem que ser
 * empurrada; se quase não sai do lugar (sem parede atrás) em vários hits, é anti-knockback.
 */
final class CombatChecks {

    private static final int HISTORY = 20;
    private static final double LEGIT_REACH = 3.05;
    private static final int KB_TICKS = 6;

    /** Pacotes de movimento de qualquer entidade (contados na thread do netty). */
    static final AtomicInteger MOVE_PACKETS = new AtomicInteger();

    /** Histórico de posições (servidor) de cada jogador, mais recente primeiro. */
    private final Map<Integer, ArrayDeque<double[]>> positions = new HashMap<Integer, ArrayDeque<double[]>>();
    /** Verificações de knockback em andamento, por id da vítima. */
    private final Map<Integer, double[]> pendingKb = new HashMap<Integer, double[]>(); // {ticks, x0, z0, dirX, dirZ}
    private int lastMovePackets;

    void tick(List<EntityPlayer> players, PlayerChecks checks, long now) {
        int packets = MOVE_PACKETS.get();
        boolean traffic = packets != lastMovePackets;
        lastMovePackets = packets;
        World world = Minecraft.getMinecraft().theWorld;

        Set<Integer> present = new HashSet<Integer>();
        for (EntityPlayer p : players) {
            present.add(p.getEntityId());
            ArrayDeque<double[]> hist = positions.get(p.getEntityId());
            if (hist == null) {
                hist = new ArrayDeque<double[]>();
                positions.put(p.getEntityId(), hist);
            }
            hist.addFirst(new double[]{PlayerChecks.posX(p), PlayerChecks.posY(p), PlayerChecks.posZ(p)});
            while (hist.size() > HISTORY) {
                hist.removeLast();
            }
            if (!(p instanceof EntityPlayerSP)) {
                checkBlink(p, checks.get(p), traffic, now);
                checkKnockback(world, p, checks.get(p), now);
            }
        }
        positions.keySet().retainAll(present);
        pendingKb.keySet().retainAll(present);
    }

    void reset() {
        positions.clear();
        pendingKb.clear();
    }

    // ================================================================ Blink / FakeLag

    private void checkBlink(EntityPlayer p, PlayerStats s, boolean traffic, long now) {
        double x = PlayerChecks.posX(p), y = PlayerChecks.posY(p), z = PlayerChecks.posZ(p);
        if (!s.lagHasPos) {
            s.lagHasPos = true;
            s.lagX = x;
            s.lagY = y;
            s.lagZ = z;
            return;
        }
        double d = Math.hypot(x - s.lagX, z - s.lagZ);
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
            if (s.preFreezeSpeed > 0.12 && d >= 1.0 && d >= expected * 0.5 && trafficRatio >= 0.6
                    && s.frozenTicks <= 60 && ping >= 0 && ping < 200) {
                boolean combat = now - s.lastSwing() < 3000L;
                int events = s.lagEvent(now, combat);
                if (events >= 3 || (combat && s.combatLagEvents(now) >= 2)) {
                    s.flag(Check.BLINK, now, String.format("congela %dms e pula %.1f blocos, ping %dms",
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

    // ================================================================ hits

    /**
     * Chamado quando um jogador toma dano. Acha quem bateu (swing até 200 ms antes, mirando nele, a até
     * 7 blocos) e mede:
     *
     * Reach: um jogador legit enxerga a vítima um pouco no passado (metade do ping + interpolação), e o
     * servidor aceita o hit nessa posição. O reach "compensado" é a MENOR distância entre o atacante
     * (últimos 3 ticks) e a vítima dentro dessa janela de atraso: o caso mais favorável ao atacante.
     *
     * Backtrack: dentro da janela o hit não alcança, mas alcança uma posição bem mais antiga.
     */
    void onHurt(EntityPlayer victim, List<EntityPlayer> players, PlayerChecks checks, long now) {
        EntityPlayer attacker = null;
        float bestErr = 35.0F;
        for (EntityPlayer a : players) {
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
        ArrayDeque<double[]> vq = positions.get(victim.getEntityId());
        ArrayDeque<double[]> aq = positions.get(attacker.getEntityId());
        if (vq == null || aq == null || vq.size() < 3 || aq.size() < 3) {
            return;
        }
        double[][] vh = vq.toArray(new double[0][]);
        double[][] ah = aq.toArray(new double[0][]);

        // Knockback: começa a medir o empurrão na direção atacante -> vítima. Só hit corpo a corpo claro
        // (perto e mirando), e sem fogo, que dá dano sem empurrar.
        double hd = Math.hypot(vh[0][0] - ah[0][0], vh[0][2] - ah[0][2]);
        if (!(victim instanceof EntityPlayerSP) && hd <= 4.5 && bestErr < 25.0F && !victim.isBurning()) {
            double dx = vh[0][0] - ah[0][0], dz = vh[0][2] - ah[0][2];
            double len = Math.hypot(dx, dz);
            if (len > 0.1 && len < 6) {
                pendingKb.put(victim.getEntityId(), new double[]{0, vh[0][0], vh[0][2], dx / len, dz / len});
            }
        }
        measureReach(attacker, ah, vh, checks.get(attacker), now);
    }

    private void measureReach(EntityPlayer attacker, double[][] ah, double[][] vh, PlayerStats s, long now) {
        double eye = attacker.isSneaking() ? 1.54 : 1.62;
        int ping = ping(attacker);
        int window = Math.max(3, Math.min(10, (ping < 0 ? 150 : ping) / 50 + 3));
        window = Math.min(window, vh.length - 1);
        for (int k = 0; k < Math.min(vh.length - 1, window + 6); k++) {
            if (dist(vh[k], vh[k + 1]) > 1.5) {
                return;
            }
        }
        double compensated = Double.MAX_VALUE;
        for (int k = 0; k <= window; k++) {
            compensated = Math.min(compensated, bestReach(ah, eye, vh[k]));
        }
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
                    s.flag(Check.BACKTRACK, now, String.format("bate onde o alvo estava há ~%dms (ping %dms)",
                            matchTick * 50, Math.max(ping, 0)));
                }
                return;
            }
        }
        if (compensated > 3.25 && s.farHit(now) >= 3 && s.hits() >= 8
                && s.reachAbove(3.2) >= Math.max(3, s.hits() * 3 / 10)) {
            s.flag(Check.REACH, now, String.format("%.2f blocos, %d de %d hits acima de 3.2",
                    compensated, s.reachAbove(3.2), s.hits()));
        }
    }

    /**
     * Velocity: o knockback de um hit empurra ~0.4 bloco/tick no 1º tick e uns 0.8+ blocos no total em
     * 6 ticks. Quem anda para frente reduz um pouco, mas quase zero (&lt; 0.12) sem parede atrás, em 4+
     * dos últimos 5-10 hits, é anti-knockback.
     */
    private void checkKnockback(World world, EntityPlayer p, PlayerStats s, long now) {
        double[] kb = pendingKb.get(p.getEntityId());
        if (kb == null) {
            return;
        }
        if (++kb[0] < KB_TICKS) {
            return;
        }
        pendingKb.remove(p.getEntityId());
        double x = PlayerChecks.posX(p), y = PlayerChecks.posY(p), z = PlayerChecks.posZ(p);
        if (p.isRiding() || p.isInWater() || p.isInLava() || p.isOnLadder() || ping(p) > 250) {
            return;
        }
        // Parede (ou teia) logo atrás na direção do empurrão segura o jogador legitimamente.
        for (double step = 0.4; step <= 1.0; step += 0.3) {
            int bx = MathHelper.floor_double(kb[1] + kb[3] * step);
            int bz = MathHelper.floor_double(kb[2] + kb[4] * step);
            int by = MathHelper.floor_double(y);
            Block feet = world.getBlockState(new BlockPos(bx, by, bz)).getBlock();
            if (PlayerChecks.solid(world, bx, by, bz) || PlayerChecks.solid(world, bx, by + 1, bz) || feet == Blocks.web) {
                return;
            }
        }
        double moved = Math.hypot(x - kb[1], z - kb[2]);
        int[] r = s.addKnockback(moved < 0.12);
        if (moved < 0.12 && r[1] >= 5 && r[0] >= 4) {
            s.flag(Check.VELOCITY, now, String.format("sem knockback em %d de %d hits", r[0], r[1]));
        }
    }

    // ================================================================ utilidades

    private static double bestReach(double[][] attacker, double eye, double[] victim) {
        double best = Double.MAX_VALUE;
        for (int j = 0; j < Math.min(3, attacker.length); j++) {
            best = Math.min(best, reach(attacker[j][0], attacker[j][1] + eye, attacker[j][2], victim));
        }
        return best;
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

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static int ping(EntityPlayer p) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(p.getUniqueID()) : null;
        return info == null ? -1 : info.getResponseTime();
    }
}
