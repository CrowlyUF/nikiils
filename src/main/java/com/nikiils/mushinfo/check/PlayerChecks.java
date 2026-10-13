package com.nikiils.mushinfo.check;

import com.nikiils.mushinfo.Tracker;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
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

/**
 * Coleta dados dos jogadores no client e roda heurísticas simples (AutoClick, SafeWalk, NoSlow).
 * Tudo aqui é estimativa feita só com o que o servidor envia ao client: serve como indício para
 * moderação, não como prova.
 */
public final class PlayerChecks {

    public static final PlayerChecks INSTANCE = new PlayerChecks();

    private final Map<Integer, PlayerStats> stats = new ConcurrentHashMap<Integer, PlayerStats>();

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
        for (EntityPlayer player : new ArrayList<EntityPlayer>(mc.theWorld.playerEntities)) {
            if (!Tracker.isTracked(player.getName())) {
                continue;
            }
            PlayerStats s = get(player);
            s.lastSeen = now;
            s.sampleCps(now);
            checkSafeWalk(mc.theWorld, player, s, now);
            checkNoSlow(player, s, now);
        }
        // Remove dados antigos (entidades que saíram / ids reutilizados).
        Iterator<Map.Entry<Integer, PlayerStats>> it = stats.entrySet().iterator();
        while (it.hasNext()) {
            PlayerStats s = it.next().getValue();
            if (now - Math.max(s.lastSeen, s.lastSwing()) > 30_000L) {
                it.remove();
            }
        }
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
    private static double posX(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posX : p.serverPosX / 32.0;
    }

    private static double posY(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posY : p.serverPosY / 32.0;
    }

    private static double posZ(EntityPlayer p) {
        return p instanceof EntityPlayerSP ? p.posZ : p.serverPosZ / 32.0;
    }

    /** Lista de alertas ativos para exibir na GUI. */
    public static List<String> alerts(PlayerStats s, long now) {
        List<String> out = new ArrayList<String>();
        s.autoClick.describe("AutoClick", now, out);
        s.safeWalk.describe("SafeWalk", now, out);
        s.noSlow.describe("NoSlow", now, out);
        return out;
    }
}
