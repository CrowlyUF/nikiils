package com.nikiils.mushinfo.check;

import com.nikiils.mushinfo.Tracker;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
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
 * Roda todos os checks em todos os jogadores do mundo, a cada tick. Tudo é estimativa feita só com
 * o que o servidor envia ao client (posições, rotações, animações, blocos): serve como indício para
 * moderação, não como prova.
 */
public final class PlayerChecks {

    public static final PlayerChecks INSTANCE = new PlayerChecks();

    /** Por nick (minúsculo): dados sobrevivem a respawn/troca de mundo. */
    private final Map<String, PlayerStats> byName = new ConcurrentHashMap<String, PlayerStats>();
    /** Id de entidade atual -> dados, para os pacotes recebidos na thread do netty. */
    private final Map<Integer, PlayerStats> byId = new ConcurrentHashMap<Integer, PlayerStats>();
    /** Blocos colocados: {x, y, z, tempo}. */
    private final ConcurrentLinkedQueue<long[]> placements = new ConcurrentLinkedQueue<long[]>();
    private final Map<String, Long> lastOnline = new ConcurrentHashMap<String, Long>();
    private final CombatChecks combat = new CombatChecks();
    final BlockChecks blocks = new BlockChecks();
    private WorldClient lastWorld;
    private long worldChangedAt;
    /** Já vimos alguém tomar dano de queda neste mundo (minigames/lobbies sem dano de queda não contam). */
    private boolean worldHasFallDamage;
    /** Jogadores andando acima de 0.36 bloco/tick agora (para não acusar Speed em velocidade do servidor). */
    private int fastPlayers;

    private PlayerChecks() {
    }

    public BlockChecks blocks() {
        return blocks;
    }

    public PlayerStats get(EntityPlayer player) {
        PlayerStats s = get(player.getName());
        s.entityId = player.getEntityId();
        return s;
    }

    public PlayerStats get(String name) {
        String key = name.toLowerCase();
        PlayerStats s = byName.get(key);
        if (s == null) {
            s = new PlayerStats(name);
            byName.put(key, s);
        }
        return s;
    }

    /** Dados já existentes (não cria). */
    public PlayerStats peek(String name) {
        return byName.get(name.toLowerCase());
    }

    PlayerStats byEntityId(int id) {
        return byId.get(id);
    }

    public void reset() {
        byId.clear();
        combat.reset();
        blocks.reset();
    }

    // ================================================================ eventos de rede (thread do netty)

    public void onSwing(int entityId, long time) {
        PlayerStats s = byId.get(entityId);
        if (s != null) {
            s.addSwing(time);
        }
    }

    public static void onMovePacket() {
        CombatChecks.MOVE_PACKETS.incrementAndGet();
    }

    /** Chamado na thread principal quando um bloco vazio vira sólido (colocado). */
    void onBlockPlaced(BlockPos pos, long time) {
        if (placements.size() < 512) {
            placements.add(new long[]{pos.getX(), pos.getY(), pos.getZ(), time});
        }
    }

    /** O servidor não reenvia o swing do próprio jogador, então contamos os cliques locais. */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
        if (self != null && event.button == 0 && event.buttonstate) {
            get(self).addSwing(System.currentTimeMillis());
        }
    }

    // ================================================================ tick

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
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            worldChangedAt = now;
            worldHasFallDamage = false;
            reset();
        }
        checkPresence(mc, now);

        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        for (EntityPlayer p : players) {
            byId.put(p.getEntityId(), get(p));
            ids.add(p.getEntityId());
        }
        byId.keySet().retainAll(ids);
        attributePlacements(players, now);
        fastPlayers = 0;
        for (EntityPlayer p : players) {
            PlayerStats ps = get(p);
            if (ps.movesCount() >= 20 && ps.averageSpeed(20) > 0.36) {
                fastPlayers++;
            }
        }

        for (EntityPlayer p : players) {
            if (p.isDead || p.deathTime > 0) {
                continue;
            }
            PlayerStats s = get(p);
            s.lastSeen = now;
            boolean hurt = p.hurtTime > s.lastHurtTime;
            s.lastHurtTime = p.hurtTime;
            s.ticksSinceHurt = hurt ? 0 : s.ticksSinceHurt + 1;

            boolean self = p instanceof EntityPlayerSP;
            ItemStack held = p.getHeldItem();
            boolean exempt = now - s.lastBreakAnim < 1000L || held != null
                    && (held.getItem() == Items.fishing_rod || held.getItem() instanceof ItemBlock)
                    || s.placementsSince(now - 2000L) > 0;
            s.sampleCps(now, self, exempt);
            s.pushY(posY(p));

            boolean moved = updateMovement(p, s);
            checkSafeWalk(mc.theWorld, p, s, now);
            checkNoSlow(p, s, now);
            if (moved) {
                checkSpeed(mc.theWorld, p, s, now);
            }
            checkNoFall(mc.theWorld, p, s, hurt, now);
            checkScaffold(mc.theWorld, p, s, now);
            checkAim(p, players, s, now);
            checkAutoBlock(p, s, now);
            if (!self && Math.abs(p.rotationPitch) > 90.5F) {
                s.flag(Check.PROTOCOL, now, String.format("pitch %.1f° (impossível)", p.rotationPitch));
            }
            s.wasSneaking = p.isSneaking();
            s.wasUsing = p.isUsingItem();
            if (hurt) {
                combat.onHurt(p, players, this, now);
            }
        }
        combat.tick(players, this, now);
        blocks.tick(now);

        // Quem não aparece há 10 min sai da memória (o histórico de quem está online fica).
        Iterator<Map.Entry<String, PlayerStats>> it = byName.entrySet().iterator();
        while (it.hasNext()) {
            PlayerStats s = it.next().getValue();
            if (now - Math.max(s.lastSeen, s.lastSwing()) > 600_000L && s.totalFlags() == 0) {
                it.remove();
            }
        }
    }

    // ================================================================ saída do jogador

    /** Quem sai do servidor (some do TAB e do mundo por 3 s) perde o card. */
    private void checkPresence(Minecraft mc, long now) {
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
                        "§8[§eMush§bInfo§8] §f" + name + " §7saiu, card removido."));
            }
        }
    }

    // ================================================================ movimento

    /** Atualiza o deslocamento do tick. Retorna false em teleporte (dados descartados). */
    private boolean updateMovement(EntityPlayer p, PlayerStats s) {
        double x = posX(p), y = posY(p), z = posZ(p);
        if (!s.hasPos) {
            s.hasPos = true;
            s.lastX = x;
            s.lastY = y;
            s.lastZ = z;
            return false;
        }
        double dx = x - s.lastX, dy = y - s.lastY, dz = z - s.lastZ;
        s.lastX = x;
        s.lastY = y;
        s.lastZ = z;
        if (dx * dx + dy * dy + dz * dz > 25) {
            s.clearMoves();
            s.fallStartY = Double.NaN;
            return false;
        }
        s.pushMove(dx, dy, dz, p.onGround);
        double maxH = Math.max(Math.abs(dx), Math.abs(dz));
        s.horizontalTicks = maxH >= 0.07 ? s.horizontalTicks + 1 : 0;
        s.ticksSinceDeltaY = Math.abs(dy) >= 0.1 ? 0 : s.ticksSinceDeltaY + 1;
        s.ticksSinceSneak = p.isSneaking() ? 0 : s.ticksSinceSneak + 1;
        return true;
    }

    /**
     * Speed: correndo no chão a velocidade fica em ~0.28 bloco/tick (até ~0.33 pulando). Média acima de
     * 0.40 por 1 s sem efeito de poção visível (partículas), sem gelo/slime e sem ter tomado hit
     * (knockback) é velocidade impossível.
     */
    private void checkSpeed(World world, EntityPlayer p, PlayerStats s, long now) {
        BlockPos below = new BlockPos(posX(p), posY(p) - 0.5, posZ(p));
        Block b = world.getBlockState(below).getBlock();
        if (b == Blocks.ice || b == Blocks.packed_ice || b == Blocks.slime_block) {
            s.ticksSinceIce = 0;
        } else {
            s.ticksSinceIce++;
        }
        boolean potion = p.getDataWatcher().getWatchableObjectInt(7) != 0;
        if (p.isRiding() || potion || s.ticksSinceHurt < 40 || s.ticksSinceIce < 40 || s.movesCount() < 20) {
            return;
        }
        double avg = s.averageSpeed(20);
        // Se 3+ jogadores estão rápidos, é o servidor dando velocidade (lobby, kit, evento).
        if (avg > 0.42 && s.maxStep(20) < 1.5 && s.groundTicks(20) >= 5 && fastPlayers < 3) {
            s.flag(Check.SPEED, now, String.format("%.2f b/t por 1s (normal até ~0.30)", avg));
            s.clearMoves();
        }
    }

    /**
     * NoFall: caiu 5+ blocos e não tomou dano. Só vale num mundo onde já vimos alguém tomar dano de
     * queda (vários minigames/lobbies desligam), e ignora água, teia, escada, slime, feno e bota com
     * Peso-Pena.
     */
    private void checkNoFall(World world, EntityPlayer p, PlayerStats s, boolean hurt, long now) {
        double y = posY(p);
        if (p.isRiding() || p.isInWater() || p.isInLava() || p.isOnLadder()) {
            s.fallStartY = Double.NaN;
            s.landedTicksAgo = -1;
            return;
        }
        if (!p.onGround) {
            if (Double.isNaN(s.fallStartY) || y > s.fallStartY) {
                s.fallStartY = y;
            }
        } else if (!Double.isNaN(s.fallStartY)) {
            double fall = s.fallStartY - y;
            s.fallStartY = Double.NaN;
            BlockPos feet = new BlockPos(posX(p), y, posZ(p));
            Block at = world.getBlockState(feet).getBlock();
            Block under = world.getBlockState(feet.down()).getBlock();
            boolean soft = at == Blocks.web || at == Blocks.vine || at == Blocks.water || at == Blocks.flowing_water
                    || under == Blocks.slime_block || under == Blocks.hay_block || under == Blocks.water;
            if (fall >= 4.0 && !soft && s.ticksSinceHurt > 10) {
                s.landedTicksAgo = 0;
                s.landedFall = fall;
            }
        }
        if (s.landedTicksAgo < 0) {
            return;
        }
        if (hurt) {
            worldHasFallDamage = true;
            s.landedTicksAgo = -1;
            return;
        }
        if (++s.landedTicksAgo <= 5) {
            return;
        }
        s.landedTicksAgo = -1;
        ItemStack boots = p.getCurrentArmor(0);
        boolean feather = boots != null && EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, boots) > 0;
        if (worldHasFallDamage && s.landedFall >= 6.0 && !feather) {
            if (now - s.lastNoFallCandidate > 120_000L) {
                s.noFallCandidates = 0;
            }
            s.lastNoFallCandidate = now;
            if (++s.noFallCandidates >= 3) {
                s.flag(Check.NOFALL, now, String.format("caiu %.0f blocos sem dano", s.landedFall));
            }
        }
    }

    // ================================================================ SafeWalk

    /**
     * SafeWalk segura o jogador na quina sem agachar. Ficar PARADO na quina é normal (dá para parar ali
     * andando devagar); o que denuncia é COMO ele para: soltando as teclas o jogo desacelera aos poucos
     * (~0.55x por tick), mas o SafeWalk zera o movimento na hora, exatamente na borda. Então conta
     * quando ele vinha andando (0.1+ bloco/tick), parou seco por 2 ticks com o corpo para fora da
     * quina, sem agachar nos últimos 6 ticks, sem parede na frente, sobre um vão de 2+ blocos.
     * 3 paradas assim em 20 s.
     */
    private void checkSafeWalk(World world, EntityPlayer p, PlayerStats s, long now) {
        double[] cur = s.move(0), mid = s.move(1), before = s.move(2);
        if (cur == null || mid == null || before == null || p.isSneaking() || s.ticksSinceSneak < 6
                || p.isRiding() || !p.onGround || cur[3] == 0 || mid[3] == 0) {
            return;
        }
        double speedBefore = Math.hypot(before[0], before[1]);
        if (speedBefore < 0.1 || Math.hypot(mid[0], mid[1]) > 0.01 || Math.hypot(cur[0], cur[1]) > 0.01) {
            return;
        }
        double x = posX(p), y = posY(p), z = posZ(p);
        if (edgeDepth(world, x, y, z) >= 0.08) {
            return;
        }
        // Bater numa parede também para seco: só vale se à frente é vazio.
        double dx = before[0] / speedBefore, dz = before[1] / speedBefore;
        int fx = MathHelper.floor_double(x + dx * 0.6), fz = MathHelper.floor_double(z + dz * 0.6);
        int fy = MathHelper.floor_double(y + 0.01);
        if (solid(world, fx, fy, fz) || solid(world, fx, fy + 1, fz) || solid(world, fx, fy - 1, fz)) {
            return;
        }
        int stops = s.safeWalkStop(now);
        if (stops >= 3) {
            s.flag(Check.SAFEWALK, now, "parou seco na quina " + stops + "x sem agachar"
                    + (p.rotationPitch > 60.0F ? " (fazendo ponte)" : ""));
        }
    }

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
        return best < 0 ? 1.0 : best;
    }

    private static boolean solidTop(World world, int x, int y, int z, double feetY) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        AxisAlignedBB box = state.getBlock().getCollisionBoundingBox(world, pos, state);
        return box != null && box.maxY >= feetY - 0.05 && box.maxY <= feetY + 0.05;
    }

    static boolean solid(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(world, pos, state) != null;
    }

    // ================================================================ NoSlow

    /**
     * Dois sinais: (1) usando item (defender, comer, arco) a velocidade cai para ~0.04 bloco/tick, e ele
     * anda acima de 0.17 por 8+ ticks; (2) o client vanilla para de correr ao usar item, e ele continua
     * com sprint ligado usando item por 11+ ticks, sem ter soltado o item nos últimos 35 ticks.
     */
    private void checkNoSlow(EntityPlayer p, PlayerStats s, long now) {
        boolean using = p.isUsingItem();
        s.ticksSinceStopUsing = s.wasUsing && !using ? 0 : s.ticksSinceStopUsing + 1;
        if (!using || !p.onGround || p.isRiding() || p.isInWater() || s.ticksSinceHurt < 20) {
            s.usingTicks = 0;
            s.noSlowTicks = 0;
            s.sprintUseTicks = 0;
            return;
        }
        s.usingTicks++;
        double speed = s.averageSpeed(5);
        if (s.usingTicks > 6 && speed > 0.17) {
            if (++s.noSlowTicks == 8) {
                s.flag(Check.NOSLOW, now, String.format("%.2f b/t usando item", speed));
            }
        } else {
            s.noSlowTicks = 0;
        }
        s.sprintUseTicks = p.isSprinting() ? s.sprintUseTicks + 1 : 0;
        if (s.sprintUseTicks == 11 && s.averageSpeed(3) >= 0.08 && s.ticksSinceStopUsing >= 35) {
            s.flag(Check.NOSLOW, now, "sprint ligado usando item");
        }
    }

    // ================================================================ AutoBlock

    /**
     * No 1.8 o client vanilla ignora o ataque enquanto o item está em uso, então não existe swing com a
     * espada defendendo. Swing + defesa por 15 ticks seguidos é AutoBlock/KillAura.
     */
    private void checkAutoBlock(EntityPlayer p, PlayerStats s, long now) {
        s.swingBlockTicks = p.isSwingInProgress && p.isBlocking() ? s.swingBlockTicks + 1 : 0;
        if (s.swingBlockTicks == 60) {
            s.flag(Check.AUTOBLOCK, now, "batendo e defendendo ao mesmo tempo");
        }
    }

    // ================================================================ Scaffold / Eagle

    /** Liga cada bloco colocado ao jogador que está em cima/ao lado dele (pés). */
    private void attributePlacements(List<EntityPlayer> players, long now) {
        long[] pl;
        while ((pl = placements.poll()) != null) {
            if (now - pl[3] > 1000L) {
                continue;
            }
            double bx = pl[0] + 0.5, bz = pl[2] + 0.5;
            EntityPlayer best = null;
            double bestDist = 1.7;
            for (EntityPlayer p : players) {
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
                get(best).addPlacement(pl[3], best.rotationPitch,
                        bestDist < 0.9 && pl[1] == MathHelper.floor_double(posY(best) + 0.01) - 1);
            }
        }
    }

    /**
     * Scaffold:
     * - colocar blocos embaixo/atrás de si olhando para frente (pitch &lt; 40°, 3x em 3 s);
     * - 11+ blocos/s, ou 8+ blocos/s correndo rápido sem agachar;
     * - Tower: subir 3+ blocos/s colocando bloco embaixo;
     * - ponte de sprint no plano olhando para baixo sobre o vazio, sem agachar (sprint só existe
     *   andando para frente, e não dá para colocar bloco atrás de si andando para frente sem hack).
     * Eagle/LegitScaffold: agacha exatamente no tick do clique, 4 vezes seguidas, fazendo ponte.
     */
    private void checkScaffold(World world, EntityPlayer p, PlayerStats s, long now) {
        ItemStack held = p.getHeldItem();
        boolean block = held != null && held.getItem() instanceof ItemBlock;
        boolean bridging = p.isSwingInProgress && p.rotationPitch >= 65.0F && block;

        if (bridging) {
            if (p.swingProgressInt == 1) {
                s.perfectSneaks = !s.wasSneaking && p.isSneaking() ? s.perfectSneaks + 1 : 0;
                if (s.perfectSneaks == 4) {
                    s.flag(Check.EAGLE, now, "agacha exatamente no clique 4x seguidas");
                }
            }
            s.scaffoldTicks = s.horizontalTicks >= 15 && p.isSprinting() ? s.scaffoldTicks + 1 : 0;
        } else {
            s.perfectSneaks = 0;
            s.scaffoldTicks = 0;
        }
        if (s.scaffoldTicks == 10 && s.ticksSinceSneak >= 25 && s.ticksSinceDeltaY >= 20 && voidBelow(world, p)) {
            s.flag(Check.SCAFFOLD, now, "ponte correndo (sprint) sem agachar");
        }

        if (!s.newPlacement) {
            return;
        }
        s.newPlacement = false;
        int perSecond = s.placementsSince(now - 1000L);
        double speed = s.averageSpeed(5);
        double rise = s.riseOver(20);
        if (s.forwardPlacementsSince(now - 3000L, 30.0F) >= 3) {
            s.flag(Check.SCAFFOLD, now, "bloco embaixo olhando pra frente");
            s.clearPlacements();
        } else if (perSecond >= 13) {
            s.flag(Check.SCAFFOLD, now, perSecond + " blocos/s");
            s.clearPlacements();
        } else if (perSecond >= 8 && speed >= 0.26 && !p.isSneaking()) {
            s.flag(Check.SCAFFOLD, now, String.format("ponte rápida %.2f b/t", speed));
            s.clearPlacements();
        } else if (s.underPlacementsSince(now - 1500L) >= 4 && rise >= 3.0
                && p.getDataWatcher().getWatchableObjectInt(7) == 0) {
            s.flag(Check.SCAFFOLD, now, String.format("Tower %.1f blocos/s", rise));
            s.clearPlacements();
        }
    }

    private static boolean voidBelow(World world, EntityPlayer p) {
        int x = MathHelper.floor_double(posX(p)), z = MathHelper.floor_double(posZ(p));
        int y = MathHelper.floor_double(posY(p)) - 2;
        for (int i = 0; i < 4; i++) {
            if (!world.isAirBlock(new BlockPos(x, y - i, z))) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ AimAssist

    /**
     * Três sinais, todos durante combate:
     * - erro médio da mira (yaw até o centro do oponente) &lt; 3° em 40 amostras com o alvo se movendo
     *   de lado: humanos ficam alguns graus atrás;
     * - giros de 60°+ em 1 tick que já param em cima do alvo, 3x em 10 s;
     * - só o yaw se mexe (1°+ por tick) por 25+ ticks com o pitch parado: muitos AimAssist só puxam o
     *   yaw.
     */
    private void checkAim(EntityPlayer p, List<EntityPlayer> players, PlayerStats s, long now) {
        float yaw = p.rotationYawHead;
        float pitch = p.rotationPitch;
        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(yaw - s.lastYaw));
        s.ticksSincePitch = pitch != s.lastPitch ? 0 : s.ticksSincePitch + 1;
        s.yawOnlyTicks = yawDelta >= 2.0F ? s.yawOnlyTicks + 1 : 0;
        s.lastYaw = yaw;
        s.lastPitch = pitch;

        boolean combat = now - s.lastSwing() < 1000L;
        if (combat && pitch > -80 && pitch < 80 && s.yawOnlyTicks == 40 && s.ticksSincePitch >= 40) {
            s.flag(Check.AIMASSIST, now, "só o yaw mexe, pitch travado");
        }

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
            if (targetMove > 2.5F) {
                s.addAimSample(targetErr);
                float mean = s.aimError();
                if (s.aimSamples() >= 40 && mean >= 0 && mean < 3.0F) {
                    s.flag(Check.AIMASSIST, now, String.format("mira grudada %.1f°", mean));
                    s.clearAim();
                }
            }
            if (yawDelta > 60.0F && targetErr < 6.0F && s.snap(now) >= 3) {
                s.flag(Check.AIMASSIST, now, "giro brusco no alvo");
            }
        }
        s.lastTarget = target;
        s.lastDesired = desired;
    }

    // ================================================================ posição

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

    /** Alertas ativos (últimos 30 s) para o card. */
    public static List<String> alerts(PlayerStats s, long now) {
        return s.activeAlerts(now);
    }
}
