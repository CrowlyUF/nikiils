package com.nikiils.mushinfo.check;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * FastMine, Nuker e BedNuker. Os métodos {@code on*} rodam na thread principal ANTES de o jogo aplicar
 * o pacote (agendados pelo {@code PacketListener}), então o mundo ainda tem o bloco antigo.
 *
 * FastMine: o servidor manda aos outros jogadores o estágio da rachadura (0-9) de quem está minerando,
 * calculado pelo próprio servidor. Quebrando no tempo certo, o último estágio visto é 8-9; o servidor
 * aceita quebrar a partir de 70%, que é onde o FastMine quebra (estágio 7 ou menos).
 *
 * Nuker/BedNuker: quebrar um bloco sem nenhuma face exposta (coberto por blocos inteiros, inclusive
 * vidro) é impossível sem hack, porque não dá para mirar nele.
 */
public final class BlockChecks {

    /** Rachaduras em andamento: pos -> {id de quem quebra, último estágio 0-9, atualizações, tempo}. */
    private final Map<BlockPos, long[]> anims = new HashMap<BlockPos, long[]>();

    void reset() {
        anims.clear();
    }

    void tick(long now) {
        Iterator<long[]> it = anims.values().iterator();
        while (it.hasNext()) {
            if (now - it.next()[3] > 3000L) {
                it.remove();
            }
        }
    }

    public void onBreakAnim(int breakerId, BlockPos pos, int progress) {
        PlayerStats s = PlayerChecks.INSTANCE.byEntityId(breakerId);
        if (s == null) {
            return;
        }
        long now = System.currentTimeMillis();
        s.lastBreakAnim = now;
        long[] a = anims.get(pos);
        if (a == null || a[0] != breakerId) {
            a = new long[]{breakerId, -1, 0, now};
            anims.put(pos, a);
        }
        a[3] = now;
        // -1/255 = parou ou terminou: mantém o último estágio válido para comparar com a quebra.
        if (progress >= 0 && progress <= 9) {
            a[1] = progress;
            a[2]++;
        }
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null || newState == null) {
            return;
        }
        IBlockState old = world.getBlockState(pos);
        long now = System.currentTimeMillis();
        boolean oldEmpty = empty(old.getBlock());
        boolean newEmpty = empty(newState.getBlock());
        if (oldEmpty && !newEmpty) {
            PlayerChecks.INSTANCE.onBlockPlaced(pos, now);
        } else if (!oldEmpty && newState.getBlock().getMaterial() == Material.air) {
            onBreak(world, pos, old.getBlock(), now);
        }
    }

    private void onBreak(World world, BlockPos pos, Block block, long now) {
        long[] anim = anims.remove(pos);
        EntityPlayer breaker = null;
        if (anim != null) {
            net.minecraft.entity.Entity e = world.getEntityByID((int) anim[0]);
            if (e instanceof EntityPlayer) {
                breaker = (EntityPlayer) e;
            }
        }
        if (breaker != null && !(breaker instanceof EntityPlayerSP) && anim[2] >= 6 && anim[1] >= 0 && anim[1] <= 7) {
            PlayerStats s = PlayerChecks.INSTANCE.get(breaker);
            if (s.fastMine(now) >= 3) {
                s.flag(Check.FASTMINE, now, String.format("quebra com ~%d%% (estágio %d de 9)",
                        (anim[1] + 1) * 10, anim[1]));
            }
        }

        boolean bed = block instanceof BlockBed;
        if (!bed && !block.isFullCube()) {
            return;
        }
        if (exposed(world, pos)) {
            return;
        }
        if (breaker == null) {
            breaker = nearestBreaker(world, pos, now);
        }
        if (breaker == null || breaker instanceof EntityPlayerSP) {
            return;
        }
        PlayerStats s = PlayerChecks.INSTANCE.get(breaker);
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (bed) {
            s.flag(Check.BEDNUKER, now, "quebrou cama coberta em " + where);
        } else {
            s.flag(Check.NUKER, now, "quebrou bloco coberto (" + block.getLocalizedName() + ") em " + where);
        }
    }

    /** Alguma face do bloco dá para algo que não é bloco inteiro (ar, água, laje...)? */
    private static boolean exposed(World world, BlockPos pos) {
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = pos.offset(f);
            Block nb = world.getBlockState(n).getBlock();
            if (nb instanceof BlockBed) {
                continue; // a outra metade da cama não expõe nada
            }
            if (!nb.isFullCube()) {
                return true;
            }
        }
        return false;
    }

    /** Sem rachadura conhecida: o jogador mais perto (até 5.5 blocos) que deu swing no último segundo. */
    private static EntityPlayer nearestBreaker(World world, BlockPos pos, long now) {
        EntityPlayer best = null;
        double bestD = 5.5 * 5.5;
        for (EntityPlayer p : world.playerEntities) {
            PlayerStats s = PlayerChecks.INSTANCE.peek(p.getName());
            if (s == null || now - s.lastSwing() > 1000L) {
                continue;
            }
            double d = p.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean empty(Block b) {
        Material m = b.getMaterial();
        return m == Material.air || m.isLiquid();
    }
}
