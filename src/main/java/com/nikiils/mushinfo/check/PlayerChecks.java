package com.nikiils.mushinfo.check;

import com.nikiils.mushinfo.Tracker;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Coleta dados dos jogadores no client e roda heurísticas simples (AutoClick, SafeWalk, NoSlow,
 * Scaffold, AimAssist, FakeLag, Backtrack).
 * Tudo aqui é estimativa feita só com o que o servidor envia ao client: serve como indício para
 * moderação, não como prova.
 */
public final class PlayerChecks {

    public static final PlayerChecks INSTANCE = new PlayerChecks();

    private final Map<Integer, PlayerStats> stats = new ConcurrentHashMap<Integer, PlayerStats>();
    /** Blocos colocados recebidos pela rede: {x, y, z, tempo}. */
    private final ConcurrentLinkedQueue<long[]> placements = new ConcurrentLinkedQueue<long[]>();
    /** Última vez que cada monitorado (minúsculo) foi visto no TAB ou no mundo. */
    private final Map<String, Long> lastOnline = new ConcurrentHashMap<String, Long>();
    private final CombatChecks combat = new CombatChecks();
    private WorldClient lastWorld;
    private long worldChangedAt;

    private PlayerChecks() {
    }

    public PlayerStats get(EntityPlayer player) {
        PlayerStats s = stats.get(player.getEntityId());
        if (s == null) {
            s = new PlayerStats();
            stats.put(player.getEntityId(), s);
        }
        return s;
    }

    public void reset() {
        stats.clear();
        combat.reset();
    }

    /** Chamado na thread do netty a cada S0BPacketAnimation de swing. */
    public void onSwing(int entityId, long time) {
        PlayerStats s = stats.get(entityId);
        if (s == null) {
            s = new PlayerStats();
            stats.put(entityId, s);
        }
        s.addSwing(time);
    }

    /** Chamado na thread do netty para cada pacote de movimento de entidade (usado pelo FakeLag). */
    public static void onMovePacket() {
        CombatChecks.MOVE_PACKETS.incrementAndGet();
    }

    /** Chamado na thread do netty para cada bloco que virou sólido. */
    public void onBlockPlaced(BlockPos pos, long time) {
        if (placements.size() < 512) {
            placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), time});
        }
    }

    /** O servidor não reenvia o swing do próprio jogador, então contamos os cliques locais. */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
        if (self != null && event.button == 0 && event.buttonstate) {
            onSwing(self.getEntityId(), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        checkPresence(mc, now);
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        List<EntityPlayer> tracked = new ArrayList<EntityPlayer>();
        for (EntityPlayer player : players) {
            if (Tracker.isTracked(player.getName())) {
                tracked.add(player);
            }
        }
        attributePlacements(tracked, now);
        for (EntityPlayer player : tracked) {
            PlayerStats s = get(player);
            s.lastSeen = now;
            s.sampleCps(now);
            s.pushY(posY(player));
            checkSafeWalk(mc.theWorld, player, s, now);
            checkNoSlow(player, s, now);
            checkScaffold(player, s, now);
            checkAim(player, players, s, now);
        }
        combat.tick(players, tracked, this, now);
        // Remove dados antigos (entidades que saíram / ids reutilizados).
        Iterator<Map.Entry<Integer, PlayerStats>> it = stats.entrySet().iterator();
        while (it.hasNext()) {
            PlayerStats s = it.next().getValue();
            if (now - Math.max(s.lastSeen, s.lastSwing()) > 30_000L) {
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- saída do jogador

    /**
     * Quem sai do servidor (some do TAB e do mundo por 3 s) deixa de ser monitorado. Logo depois de
     * trocar de servidor/mundo o TAB ainda está sendo preenchido, então esperamos um pouco mais.
     */
    private void checkPresence(Minecraft mc, long now) {
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            worldChangedAt = now;
        }
        for (String name : Tracker.names()) {
            String key = name.toLowerCase();
            boolean online = mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(name) != null;
            if (!online) {
                for (EntityPlayer p : mc.theWorld.playerEntities) {
                    if (p.getName().equalsIgnoreCase(name)) {
                        online = true;
                        break;
                    }
                }
            }
            Long seen = lastOnline.get(key);
            if (online || seen == null) {
                lastOnline.put(key, now);
            } else if (now - seen > 3000L && now - worldChangedAt > 6000L) {
                Tracker.remove(name);
                lastOnline.remove(key);
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "\u00a78[\u00a7bMushInfo\u00a78] \u00a7f" + name + " \u00a77saiu, parou de mostrar."));
            }
        }
    }

    // ---------------------------------------------------------------- Scaffold

    /** Liga cada bloco colocado ao jogador monitorado que está em cima/ao lado dele (pés). */
    private void attributePlacements(List<EntityPlayer> tracked, long now) {
        long[] pl;
        while ((pl = placements.poll()) != null) {
            if (now - pl[3] > 1000L || tracked.isEmpty()) {
                continue;
            }
            double bx = pl[0] + 0.5, bz = pl[2] + 0.5;
            EntityPlayer best = null;
            double bestDist = 1.7;
            for (EntityPlayer p : tracked) {
                int feet = MathHelper.floor_double(posY(p) + 0.01);
                if (pl[1] > feet - 1 || pl[1] < feet - 2) {
                    continue;
                }
                double d = Math.hypot(posX(p) - bx, posZ(p) - bz);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            if (best != null) {
                PlayerStats s = get(best);
                s.addPlacement(pl[3], best.rotationPitch, bestDist < 0.9 && pl[1] == MathHelper.floor_double(posY(best) + 0.01) - 1);
            }
        }
    }

    /**
     * Sinais de Scaffold vistos de fora:
     * - colocar blocos embaixo/atrás de si olhando para frente (pitch baixo) — impossível sem o hack
     *   girar a cabeça, pois é preciso mirar na face do bloco;
     * - muitos blocos por segundo, ou ponte muito rápida correndo sem agachar;
     * - Tower: subir mais rápido do que pulando e colocando bloco embaixo.
     */
    private void checkScaffold(EntityPlayer p, PlayerStats s, long now) {
        if (!s.newPlacement) {
            return;
        }
        s.newPlacement = false;
        int perSecond = s.placementsSince(now - 1000L);
        int lookingForward = s.forwardPlacementsSince(now - 3000L, 40.0F);
        double speed = s.averageSpeed();
        double rise = s.riseOver(20);
        if (lookingForward >= 3) {
            s.scaffold.flag(now, "bloco embaixo olhando pra frente");
            s.clearPlacements();
        } else if (perSecond >= 11) {
            s.scaffold.flag(now, perSecond + " blocos/s");
            s.clearPlacements();
        } else if (perSecond >= 8 && speed >= 0.26 && !p.isSneaking()) {
            s.scaffold.flag(now, String.format("ponte r\u00e1pida %.2f b/t", speed));
            s.clearPlacements();
        } else if (s.underPlacementsSince(now - 1500L) >= 4 && rise >= 3.0) {
            s.scaffold.flag(now, String.format("Tower %.1f blocos/s", rise));
            s.clearPlacements();
        }
    }

    // ---------------------------------------------------------------- AimAssist

    /**
     * Mede o erro da mira (diferença de yaw entre para onde o jogador olha e o centro do oponente)
     * enquanto ele está batendo em alguém que se move de lado. Humanos ficam atrás do alvo uns graus;
     * AimAssist/aimbot mantém o erro muito baixo e estável. Giros bruscos (>60° em 1 tick) que já
     * terminam em cima do alvo também contam.
     */
    private void checkAim(EntityPlayer p, List<EntityPlayer> players, PlayerStats s, long now) {
        float yaw = p.rotationYawHead;
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(yaw - s.lastYaw));
        s.lastYaw = yaw;

        if (now - s.lastSwing() > 300L) {
            s.lastTarget = null;
            return;
        }
        double px = posX(p), pz = posZ(p);
        EntityPlayer target = null;
        float targetErr = 0;
        double bestScore = Double.MAX_VALUE;
        for (EntityPlayer o : players) {
            if (o == p || o.isInvisible() || o.isDead) {
                continue;
            }
            double dx = posX(o) - px, dz = posZ(o) - pz;
            double dist = Math.hypot(dx, dz);
            if (dist > 4.5 || dist < 0.5) {
                continue;
            }
            float desired = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float err = Math.abs(MathHelper.wrapAngleTo180_float(desired - yaw));
            if (err < 45 && err + dist < bestScore) {
                bestScore = err + dist;
                target = o;
                targetErr = err;
            }
        }
        if (target == null) {
            s.lastTarget = null;
            return;
        }
        double dx = posX(target) - px, dz = posZ(target) - pz;
        float desired = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        if (s.lastTarget == target) {
            float targetMove = Math.abs(MathHelper.wrapAngleTo180_float(desired - s.lastDesired));
            // Só conta quando o alvo está se movendo de lado (a mira precisa acompanhar).
            if (targetMove > 2.5F) {
                s.addAimSample(targetErr);
                float mean = s.aimError();
                if (s.aimSamples() >= 40 && mean >= 0 && mean < 3.0F) {
                    s.aimAssist.flag(now, String.format("mira grudada %.1f\u00b0", mean));
                    s.clearAim();
                }
            }
            if (yawDelta > 60.0F && targetErr < 6.0F) {
                if (s.snap(now) >= 3) {
                    s.aimAssist.flag(now, "giro brusco no alvo");
                }
            }
        }
        s.lastTarget = target;
        s.lastDesired = desired;
    }

    // ---------------------------------------------------------------- SafeWalk

    /**
     * SafeWalk impede o jogador de cair de quinas sem agachar. Detectamos jogadores parados (ou se
     * movendo, como ao fazer ponte de costas) com o centro do corpo fora do bloco, sustentados só pela
     * borda da hitbox, sem estar agachados, por vários ticks seguidos, sobre um vão de pelo menos 2 blocos.
     */
    private void checkSafeWalk(World world, EntityPlayer p, PlayerStats s, long now) {
        double x = posX(p), y = posY(p), z = posZ(p);
        boolean hanging = !p.isSneaking() && !p.capabilities.isFlying && !p.isRiding()
                && p.onGround && edgeDepth(world, x, y, z) < 0.10;

        if (hanging) {
            s.edgeTicks++;
            if (p.rotationPitch > 60.0F) {
                s.edgeLookingDown = true;
            }
            if (s.edgeTicks == 5) {
                s.safeWalk.flag(now, s.edgeLookingDown ? "ponte sem agachar" : "parado na quina");
            }
        } else {
            s.edgeTicks = 0;
            s.edgeLookingDown = false;
        }
    }

    /**
     * Quanto a hitbox (0.6 de largura) do jogador ainda está "dentro" do bloco que o sustenta.
     * Retorna 1 se o centro estiver sobre um bloco sólido ou se não houver queda real abaixo.
     */
    private static double edgeDepth(World world, double x, double y, double z) {
        int by = MathHelper.floor_double(y - 0.01);
        int cx = MathHelper.floor_double(x);
        int cz = MathHelper.floor_double(z);
        if (solidTop(world, cx, by, cz, y) || solid(world, cx, by - 1, cz) || solid(world, cx, by - 2, cz)) {
            return 1.0;
        }
        double minX = x - 0.3, maxX = x + 0.3, minZ = z - 0.3, maxZ = z + 0.3;
        double best = -1.0;
        for (int bx = MathHelper.floor_double(minX); bx <= MathHelper.floor_double(maxX - 1.0E-4); bx++) {
            for (int bz = MathHelper.floor_double(minZ); bz <= MathHelper.floor_double(maxZ - 1.0E-4); bz++) {
                if (!solidTop(world, bx, by, bz, y)) {
                    continue;
                }
                double ox = Math.min(maxX, bx + 1) - Math.max(minX, bx);
                double oz = Math.min(maxZ, bz + 1) - Math.max(minZ, bz);
                best = Math.max(best, Math.min(ox, oz));
            }
        }
        // Sem bloco sustentando: está caindo/pulando, não conta.
        return best < 0 ? 1.0 : best;
    }

    private static boolean solidTop(World world, int x, int y, int z, double feetY) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        AxisAlignedBB box = state.getBlock().getCollisionBoundingBox(world, pos, state);
        return box != null && box.maxY >= feetY - 0.05 && box.maxY <= feetY + 0.05;
    }

    private static boolean solid(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(world, pos, state) != null;
    }

    // ---------------------------------------------------------------- NoSlow

    /**
     * Usando item (defender com espada, comer, puxar arco) a velocidade cai para ~0.04 bloco/tick.
     * Andar acima de ~0.17 bloco/tick por vários ticks seguidos usando item indica NoSlow.
     */
    private void checkNoSlow(EntityPlayer p, PlayerStats s, long now) {
        double x = posX(p), z = posZ(p);
        double speed = s.hasLastPos ? Math.hypot(x - s.lastX, z - s.lastZ) : 0.0;
        s.lastX = x;
        s.lastZ = z;
        s.hasLastPos = true;
        s.pushSpeed(speed);

        if (p.isUsingItem() && p.onGround && !p.isRiding()) {
            s.usingTicks++;
        } else {
            s.usingTicks = 0;
            s.noSlowTicks = 0;
            return;
        }
        if (s.usingTicks > 6 && s.averageSpeed() > 0.17) {
            if (++s.noSlowTicks == 8) {
                s.noSlow.flag(now, String.format("%.2f b/t usando item", s.averageSpeed()));
            }
        } else {
            s.noSlowTicks = 0;
        }
    }

    // ---------------------------------------------------------------- posição

    /** Para outros jogadores usa a posição exata enviada pelo servidor (sem interpolação). */
    static double posX(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posX : p.serverPosX / 32.0;
    }

    static double posY(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posY : p.serverPosY / 32.0;
    }

    static double posZ(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posZ : p.serverPosZ / 32.0;
    }

    /** Lista de alertas ativos para exibir na GUI. */
    public static List<String> alerts(PlayerStats s, long now) {
        List<String> out = new ArrayList<String>();
        s.autoClick.describe("AutoClick", now, out);
        s.safeWalk.describe("SafeWalk", now, out);
        s.noSlow.describe("NoSlow", now, out);
        s.scaffold.describe("Scaffold", now, out);
        s.aimAssist.describe("AimAssist", now, out);
        s.fakeLag.describe("FakeLag", now, out);
        s.backtrack.describe("Backtrack/Reach", now, out);
        return out;
    }
}
