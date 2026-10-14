package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.Tracker;
import com.nikiils.mushinfo.api.MushApi;
import com.nikiils.mushinfo.api.PlayerData;
import com.nikiils.mushinfo.check.PlayerChecks;
import com.nikiils.mushinfo.check.PlayerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Painel arredondado com fundo borrado ao lado de cada jogador monitorado. A posição do jogador é
 * projetada na tela durante o render do mundo, e o painel é desenhado em 2D no HUD (assim o blur
 * pega exatamente o que está atrás dele).
 */
public class InfoRenderer {

    private static final int PAD = 6;
    private static final int RADIUS = 6;
    private static final int ROW_GAP = 3;
    private static final int BG = 0xD2000000;
    private static final int OUTLINE = 0x22FFFFFF;
    private static final int OUTLINE_ALERT = 0xAAFF4444;
    private static final int SEPARATOR = 0x30FFFFFF;
    private static final int LABEL = 0xFF9A9A9A;
    private static final int ALERT = 0xFFFF5555;

    private final FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screen = BufferUtils.createFloatBuffer(3);

    /** Jogadores projetados no último frame do mundo (em pixels reais, origem embaixo à esquerda). */
    private final List<Target> targets = new ArrayList<Target>();

    private static final class Target {
        final EntityPlayer player;
        final float x;
        final float y;
        final double dist;

        Target(EntityPlayer player, float x, float y, double dist) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.dist = dist;
        }
    }

    private static final class Row {
        final String label;
        final String value;

        Row(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        targets.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        modelview.clear();
        projection.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        RenderManager rm = mc.getRenderManager();
        double yaw = Math.toRadians(rm.playerViewY);
        // Vetor "direita" da câmera no plano horizontal.
        // (playerViewY já vem girado 180° na câmera frontal do F5.)
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        float pt = event.partialTicks;

        for (EntityPlayer player : new ArrayList<EntityPlayer>(mc.theWorld.playerEntities)) {
            if (!Tracker.isTracked(player.getName())) {
                continue;
            }
            if (player instanceof EntityPlayerSP && mc.gameSettings.thirdPersonView == 0) {
                continue;
            }
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt - rm.viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt - rm.viewerPosY;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt - rm.viewerPosZ;
            // Ponto 0.6 bloco à direita do jogador, na altura da cabeça.
            double px = x + rightX * 0.6, py = y + player.height, pz = z + rightZ * 0.6;
            screen.clear();
            if (GLU.gluProject((float) px, (float) py, (float) pz, modelview, projection, viewport, screen)
                    && screen.get(2) > 0 && screen.get(2) < 1) {
                targets.add(new Target(player, screen.get(0), screen.get(1), Math.sqrt(x * x + y * y + z * z)));
            }
        }
        // Os mais distantes primeiro, para os mais próximos ficarem por cima.
        targets.sort((a, b) -> Double.compare(b.dist, a.dist));
    }

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || targets.isEmpty()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        long now = System.currentTimeMillis();

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        // Os polígonos arredondados são desenhados em sentido horário; com o culling ligado eles somem.
        GlStateManager.disableCull();

        for (Target t : targets) {
            float gx = t.x / sf;
            float gy = (mc.displayHeight - t.y) / sf;
            drawPanel(mc, t.player.getName(), t.player, gx, gy, sr, sf, now, scaleFor(t.dist));
        }

        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /** Desenha um painel; retorna a altura. (x, y) é o canto superior esquerdo, ou a âncora do jogador. */
    private float drawPanel(Minecraft mc, String name, EntityPlayer player, float x, float y,
                            ScaledResolution sr, int sf, long now, float scale) {
        FontRenderer fr = mc.fontRendererObj;
        PlayerData data = MushApi.get(name);
        List<Row> rows = rows(name, player, data, now);
        List<String> alerts = player != null
                ? PlayerChecks.alerts(PlayerChecks.INSTANCE.get(player), now) : new ArrayList<String>();

        String title = "§l" + (data.username != null ? data.username : name);
        String rank = data.rank != null ? data.rank : null;
        String subtitle = null;

        int labelW = 0, valueW = 0;
        for (Row r : rows) {
            labelW = Math.max(labelW, fr.getStringWidth(r.label));
            valueW = Math.max(valueW, fr.getStringWidth(r.value));
        }
        int contentW = labelW + 14 + valueW;
        int titleW = fr.getStringWidth(title) + (rank != null ? fr.getStringWidth(rank) + 10 : 0);
        contentW = Math.max(contentW, titleW);
        if (subtitle != null) {
            contentW = Math.max(contentW, fr.getStringWidth(subtitle));
        }
        for (String a : alerts) {
            contentW = Math.max(contentW, fr.getStringWidth(a) + 8);
        }
        int lineH = fr.FONT_HEIGHT + ROW_GAP;
        float w = contentW + PAD * 2;
        float h = PAD + fr.FONT_HEIGHT + (subtitle != null ? lineH : 0) + 5 + rows.size() * lineH
                + (alerts.isEmpty() ? 0 : 3 + alerts.size() * lineH) + PAD - ROW_GAP;

        // Painel começa à direita do jogador com o topo um pouco acima da cabeça.
        float ox = Math.min(Math.max(2, x), sr.getScaledWidth() - w * scale - 2);
        float oy = Math.min(Math.max(2, y - 10 * scale), sr.getScaledHeight() - h * scale - 2);
        GlStateManager.pushMatrix();
        GlStateManager.translate(ox, oy, 0);
        GlStateManager.scale(scale, scale, 1);
        x = 0;
        y = 0;

        RenderUtil.blur(w, h, RADIUS, ox, oy, scale, sf);
        RenderUtil.roundedRect(x, y, w, h, RADIUS, BG);
        RenderUtil.roundedOutline(x, y, w, h, RADIUS, alerts.isEmpty() ? OUTLINE : OUTLINE_ALERT);

        float cy = y + PAD;
        fr.drawStringWithShadow(title, x + PAD, cy, 0xFFFFFFFF);
        if (rank != null) {
            // "Pílula" com a cor do rank.
            int rw = fr.getStringWidth(rank) + 6;
            float rx = x + w - PAD - rw;
            RenderUtil.roundedRect(rx, cy - 2, rw, fr.FONT_HEIGHT + 3, 3, 0x55000000 | (data.rankColor & 0xFFFFFF));
            fr.drawStringWithShadow(rank, rx + 3, cy, 0xFF000000 | brighten(data.rankColor));
        }
        cy += fr.FONT_HEIGHT;
        if (subtitle != null) {
            fr.drawStringWithShadow(subtitle, x + PAD, cy + ROW_GAP, 0xFF707070);
            cy += lineH;
        }
        cy += 2;
        RenderUtil.rect(x + PAD, cy, w - PAD * 2, 1, SEPARATOR);
        cy += 3 + ROW_GAP;

        for (Row r : rows) {
            fr.drawStringWithShadow(r.label, x + PAD, cy, LABEL);
            fr.drawStringWithShadow(r.value, x + w - PAD - fr.getStringWidth(r.value), cy, 0xFFFFFFFF);
            cy += lineH;
        }
        if (!alerts.isEmpty()) {
            RenderUtil.rect(x + PAD, cy, w - PAD * 2, 1, 0x40FF5555);
            cy += 3 + ROW_GAP - 1;
            for (String a : alerts) {
                RenderUtil.circle(x + PAD + 2, cy + 3.5F, 2, ALERT);
                fr.drawStringWithShadow(a, x + PAD + 8, cy, ALERT);
                cy += lineH;
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        return h * scale;
    }

    private static String cpsText(int cps) {
        return cps >= PlayerStats.SWING_CAP ? "7+" : String.valueOf(cps);
    }

    /** Tamanho do painel: normal até 6 blocos, diminuindo até a metade a partir de ~40 blocos. */
    private static float scaleFor(double dist) {
        return (float) Math.max(0.5, Math.min(1.0, 1.0 - (dist - 6.0) / 34.0 * 0.5));
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 255) + 60);
        int g = Math.min(255, (rgb >> 8 & 255) + 60);
        int b = Math.min(255, (rgb & 255) + 60);
        return r << 16 | g << 8 | b;
    }

    private static List<Row> rows(String name, EntityPlayer player, PlayerData data, long now) {
        List<Row> rows = new ArrayList<Row>();
        rows.add(new Row("Ping", ping(name)));

        if (player != null) {
            PlayerStats s = PlayerChecks.INSTANCE.get(player);
            int cps = s.cps(now);
            if (player instanceof EntityPlayerSP) {
                String c = cps >= 16 ? "§c" : cps >= 11 ? "§e" : "§a";
                rows.add(new Row("CPS", c + cps + " §8/ " + s.maxCps()));
            } else {
                // Acima de ~7 o servidor não repassa mais swings: o valor real é desconhecido.
                String c = cps >= PlayerStats.SWING_CAP ? "§e" : "§a";
                rows.add(new Row("CPS", c + cpsText(cps) + " §8/ " + cpsText(s.maxCps())));
            }
            double[] reach = s.reachStats();
            if (reach != null) {
                String rc = reach[0] >= 3.3 ? "§c" : reach[0] >= 3.0 ? "§e" : "§a";
                rows.add(new Row("Reach", rc + String.format("%.2f", reach[0]) + " §8méd / "
                        + String.format("%.2f", reach[1]) + " máx"));
            }
            float aim = s.aimError();
            if (aim >= 0) {
                String ac = aim < 3 ? "§c" : aim < 6 ? "§e" : "§a";
                rows.add(new Row("Mira", ac + String.format("%.1f°", aim) + " §8erro médio"));
            }
        }

        if (data.isLoading()) {
            rows.add(new Row("Bans", "§8..."));
            rows.add(new Row("Mute", "§8..."));
        } else if (data.error != null) {
            rows.add(new Row("Bans", "§8" + data.error));
            rows.add(new Row("Mute", "§8" + data.error));
        } else {
            String bans;
            if (data.banned) {
                bans = "§cBanido" + (data.banBlacklistCount > 0 ? " §7(blacklist " + data.banBlacklistCount + ")" : "");
            } else if (data.banCount > 0) {
                bans = "§6" + data.banCount;
            } else {
                bans = "§aNenhum";
            }
            rows.add(new Row("Bans", bans));
            rows.add(new Row("Mute", data.muted ? "§cMutado" : data.muteCount > 0 ? "§6" + data.muteCount : "§aNão"));
        }
        return rows;
    }

    private static String ping(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(name) : null;
        if (info == null) {
            return "§8?";
        }
        int ms = info.getResponseTime();
        String c = ms < 80 ? "§a" : ms < 150 ? "§e" : ms < 250 ? "§6" : "§c";
        return c + ms + "ms";
    }
}
