package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.Tracker;
import com.nikiils.mushinfo.api.MushApi;
import com.nikiils.mushinfo.api.PlayerData;
import com.nikiils.mushinfo.check.PlayerChecks;
import com.nikiils.mushinfo.check.PlayerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Desenha o painel ao lado dos jogadores monitorados (e um resumo no HUD para quem está longe). */
public class InfoRenderer {

    private static final int BG = 0x90000000;
    private static final int BORDER = 0xFF55FFFF;
    private static final int ALERT_BORDER = 0xFFFF5555;

    private final Set<String> visible = new HashSet<String>();

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        visible.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        long now = System.currentTimeMillis();
        for (EntityPlayer player : new ArrayList<EntityPlayer>(mc.theWorld.playerEntities)) {
            if (!Tracker.isTracked(player.getName())) {
                continue;
            }
            visible.add(player.getName().toLowerCase());
            if (player instanceof EntityPlayerSP && mc.gameSettings.thirdPersonView == 0) {
                continue;
            }
            List<String> lines = buildLines(player.getName(), player, now);
            List<String> alerts = PlayerChecks.alerts(PlayerChecks.INSTANCE.get(player), now);
            double pt = event.partialTicks;
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt - rm.viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt - rm.viewerPosY;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt - rm.viewerPosZ;
            drawPanel(mc, rm, x, y + player.height + 0.1, z, lines, alerts);
        }
    }

    private void drawPanel(Minecraft mc, RenderManager rm, double x, double y, double z,
                           List<String> lines, List<String> alerts) {
        FontRenderer fr = mc.fontRendererObj;
        double dist = Math.sqrt(x * x + y * y + z * z);
        float scale = (float) (0.02 * Math.max(1.0, dist / 7.0));

        List<String> all = new ArrayList<String>(lines);
        for (String a : alerts) {
            all.add("§c! " + a);
        }
        int width = 0;
        for (String s : all) {
            width = Math.max(width, fr.getStringWidth(s));
        }
        int lineH = fr.FONT_HEIGHT + 1;
        int height = all.size() * lineH + 4;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.gameSettings.thirdPersonView == 2 ? -rm.playerViewX : rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        // Ao lado direito do jogador (do ponto de vista de quem olha).
        GlStateManager.translate(40.0F, 0.0F, 0.0F);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        int border = alerts.isEmpty() ? BORDER : ALERT_BORDER;
        Gui.drawRect(-1, -1, width + 5, height + 1, border);
        Gui.drawRect(0, 0, width + 4, height, BG);
        int ty = 2;
        for (String s : all) {
            fr.drawStringWithShadow(s, 2, ty, 0xFFFFFFFF);
            ty += lineH;
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /** Resumo no canto da tela para jogadores monitorados que não estão no alcance de renderização. */
    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        int y = 4;
        long now = System.currentTimeMillis();
        for (String name : Tracker.names()) {
            if (visible.contains(name.toLowerCase())) {
                continue;
            }
            List<String> lines = buildLines(name, null, now);
            int width = 0;
            for (String s : lines) {
                width = Math.max(width, mc.fontRendererObj.getStringWidth(s));
            }
            int h = lines.size() * (mc.fontRendererObj.FONT_HEIGHT + 1) + 3;
            Gui.drawRect(3, y - 1, 3 + width + 5, y + h, BG);
            int ty = y + 1;
            for (String s : lines) {
                mc.fontRendererObj.drawStringWithShadow(s, 5, ty, 0xFFFFFFFF);
                ty += mc.fontRendererObj.FONT_HEIGHT + 1;
            }
            y += h + 4;
        }
    }

    private static List<String> buildLines(String name, EntityPlayer player, long now) {
        List<String> lines = new ArrayList<String>();
        PlayerData data = MushApi.get(name);
        String rank = data.rank != null ? " §7[" + data.rank + "]" : "";
        String display = data.username != null ? data.username : name;
        lines.add("§b§l" + display + rank + (player == null ? " §8(longe)" : ""));

        lines.add("§7PING: " + ping(name));

        if (player != null) {
            PlayerStats s = PlayerChecks.INSTANCE.get(player);
            int cps = s.cps(now);
            String c = cps >= 16 ? "§c" : cps >= 11 ? "§e" : "§a";
            lines.add("§7CPS: " + c + cps + " §8(máx " + s.maxCps() + ")");
        } else {
            lines.add("§7CPS: §8-");
        }

        if (data.isLoading()) {
            lines.add("§7Bans: §8carregando...");
            lines.add("§7Mute: §8carregando...");
        } else if (data.error != null) {
            lines.add("§7Bans: §8" + data.error);
            lines.add("§7Mute: §8" + data.error);
        } else {
            String bans;
            if (data.banned) {
                bans = "§cBanido";
                if (data.banBlacklistCount > 0) {
                    bans += " §7(blacklist x" + data.banBlacklistCount + ")";
                }
            } else if (data.banCount > 0) {
                bans = "§6" + data.banCount + " §7(não banido agora)";
            } else {
                bans = "§aNenhum";
            }
            lines.add("§7Bans: " + bans);
            String mute = data.muted ? "§cMutado" : data.muteCount > 0 ? "§6" + data.muteCount : "§aNão";
            lines.add("§7Mute: " + mute);
        }
        return lines;
    }

    private static String ping(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return "§8?";
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(name);
        if (info == null) {
            return "§8? (fora do tab)";
        }
        int ms = info.getResponseTime();
        String c = ms < 80 ? "§a" : ms < 150 ? "§e" : ms < 250 ? "§6" : "§c";
        return c + ms + "ms";
    }
}
