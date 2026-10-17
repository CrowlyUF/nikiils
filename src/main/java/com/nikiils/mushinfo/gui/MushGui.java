package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.ChatLog;
import com.nikiils.mushinfo.Settings;
import com.nikiils.mushinfo.Tracker;
import com.nikiils.mushinfo.api.MushApi;
import com.nikiils.mushinfo.api.PlayerData;
import com.nikiils.mushinfo.check.Check;
import com.nikiils.mushinfo.check.Flag;
import com.nikiils.mushinfo.check.PlayerChecks;
import com.nikiils.mushinfo.check.PlayerStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClickGUI (RShift): lista de jogadores à esquerda (cabeça, original/pirata, alertas, busca) e os
 * detalhes do selecionado à direita (informações, alertas, histórico e chat). As duas colunas rolam
 * com a roda do mouse ou segurando e arrastando.
 */
public class MushGui extends GuiScreen {

    // ---------------------------------------------------------------- cores
    private static final int WINDOW = 0xD8101014;
    private static final int PANEL = 0x50000000;
    private static final int CELL = 0x1CFFFFFF;
    private static final int HOVER = 0x14FFFFFF;
    private static final int ACCENT = 0xFF4FC3F7;
    private static final int ACCENT_BG = 0x404FC3F7;
    private static final int MUTED = 0xFF8C8C96;
    private static final int RED = 0xFFFF5555;
    private static final int ORANGE = 0xFFFFAA33;
    private static final int GREEN = 0xFF55E07A;

    private static final int ROW_H = 24;
    private static final int HEADER_H = 26;
    private static final int DETAIL_HEADER_H = 50;
    private static final int TABS_H = 24;
    private static final String[] TABS = {"Infos", "Alertas", "Mensagens"};

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy");

    /** Jogador selecionado (continua selecionado ao reabrir). */
    private static String selected;
    /** Aba aberta nos detalhes (0 Infos, 1 Alertas, 2 Mensagens); continua ao trocar de jogador. */
    private static int tab;
    private float tabX = -1, tabW;
    private static String pendingOpen;
    private static boolean pending;

    private int x0, y0, w, h;
    private int listX, listY, listW, listH;
    private int detX, detY, detW, detH;
    private GuiTextField search;
    private final List<Entry> entries = new ArrayList<Entry>();

    private float listScroll, listTarget, detScroll, detTarget;
    private int listContentH, detContentH;
    /** 1 = arrastando a lista, 2 = os detalhes, 3/4 = a barra de rolagem de cada um. */
    private int drag;
    private int pressY;
    private float pressScroll;
    private boolean dragMoved;
    private long openedAt;

    private final List<Button> buttons = new ArrayList<Button>();

    private static final class Entry {
        final String name;
        NetworkPlayerInfo info;
        EntityPlayer entity;

        Entry(String name) {
            this.name = name;
        }
    }

    private static final class Button {
        final float x, y, w, h;
        final Runnable action;

        Button(float x, float y, float w, float h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    // ================================================================ abrir

    /** Abre no próximo tick (o chat fecha a si mesmo logo depois de mandar a mensagem). */
    public static void requestOpen(String name) {
        pendingOpen = name;
        pending = true;
    }

    /** Tecla e abertura agendada. */
    public static final class Opener {
        private final KeyBinding key = new KeyBinding("Abrir MushInfo", Keyboard.KEY_RSHIFT, "MushInfo");

        public Opener() {
            ClientRegistry.registerKeyBinding(key);
        }

        @SubscribeEvent
        public void onKey(InputEvent.KeyInputEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (key.isPressed() && mc.currentScreen == null && mc.thePlayer != null) {
                mc.displayGuiScreen(new MushGui());
            }
        }

        /** O chat vanilla aparece através da janela; esconde enquanto a GUI está aberta. */
        @SubscribeEvent
        public void onOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
            if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CHAT
                    && Minecraft.getMinecraft().currentScreen instanceof MushGui) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (event.phase == TickEvent.Phase.END && pending && mc.thePlayer != null
                    && (mc.currentScreen == null || mc.currentScreen instanceof GuiChat)) {
                pending = false;
                if (pendingOpen != null) {
                    selected = pendingOpen;
                }
                mc.displayGuiScreen(new MushGui());
            }
        }
    }

    // ================================================================ layout

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        w = Math.min(width - 24, 540);
        h = Math.min(height - 24, 330);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        listX = x0 + 8;
        listY = y0 + HEADER_H;
        listW = Math.min(170, w / 3);
        listH = h - HEADER_H - 8;
        detX = listX + listW + 8;
        detY = listY;
        detW = x0 + w - 8 - detX;
        detH = listH;
        String old = search != null ? search.getText() : "";
        search = new GuiTextField(0, fontRendererObj, listX + 8, listY + 7, listW - 16, 10);
        search.setEnableBackgroundDrawing(false);
        search.setMaxStringLength(16);
        search.setText(old);
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    // ================================================================ dados

    private void rebuildEntries() {
        entries.clear();
        Map<String, Entry> map = new LinkedHashMap<String, Entry>();
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : new ArrayList<NetworkPlayerInfo>(mc.getNetHandler().getPlayerInfoMap())) {
                String n = info.getGameProfile().getName();
                if (valid(n)) {
                    entry(map, n).info = info;
                }
            }
        }
        if (mc.theWorld != null) {
            for (EntityPlayer p : new ArrayList<EntityPlayer>(mc.theWorld.playerEntities)) {
                if (valid(p.getName())) {
                    entry(map, p.getName()).entity = p;
                }
            }
        }
        String q = search.getText().trim().toLowerCase();
        final long now = System.currentTimeMillis();
        for (Entry e : map.values()) {
            if (q.isEmpty() || e.name.toLowerCase().contains(q)) {
                entries.add(e);
            }
        }
        Collections.sort(entries, (a, b) -> {
            int c = Boolean.compare(Tracker.isTracked(b.name), Tracker.isTracked(a.name));
            if (c != 0) {
                return c;
            }
            PlayerStats sa = PlayerChecks.INSTANCE.peek(a.name), sb = PlayerChecks.INSTANCE.peek(b.name);
            boolean fa = sa != null && sa.hasActiveAlert(now), fb = sb != null && sb.hasActiveAlert(now);
            c = Boolean.compare(fb, fa);
            if (c != 0) {
                return c;
            }
            int ta = sa != null ? sa.totalFlags() : 0, tb = sb != null ? sb.totalFlags() : 0;
            if (ta != tb) {
                return tb - ta;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0).name;
        }
    }

    private static Entry entry(Map<String, Entry> map, String name) {
        Entry e = map.get(name.toLowerCase());
        if (e == null) {
            e = new Entry(name);
            map.put(name.toLowerCase(), e);
        }
        return e;
    }

    private Entry find(String name) {
        for (Entry e : entries) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        Entry e = new Entry(name);
        if (mc.getNetHandler() != null) {
            e.info = mc.getNetHandler().getPlayerInfo(name);
        }
        if (mc.theWorld != null) {
            e.entity = mc.theWorld.getPlayerEntityByName(name);
        }
        return e;
    }

    private static boolean valid(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{1,16}");
    }

    private static ResourceLocation skin(Entry e) {
        if (e.info != null) {
            return e.info.getLocationSkin();
        }
        if (e.entity instanceof AbstractClientPlayer) {
            return ((AbstractClientPlayer) e.entity).getLocationSkin();
        }
        return DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    /** 1 = original, 2 = pirata, 0 = desconhecido. API primeiro; sem ela, a versão do UUID (3 = offline). */
    private static int account(Entry e) {
        PlayerData d = MushApi.peek(e.name);
        if (d != null && d.accountType != null) {
            return d.accountType.equalsIgnoreCase("cracked") ? 2 : 1;
        }
        UUID id = e.info != null ? e.info.getGameProfile().getId() : e.entity != null ? e.entity.getUniqueID() : null;
        if (id == null) {
            return 0;
        }
        return id.version() == 3 ? 2 : id.version() == 4 ? 1 : 0;
    }

    // ================================================================ desenho

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        rebuildEntries();
        buttons.clear();
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        long now = System.currentTimeMillis();
        float t = Math.min(1.0F, (now - openedAt) / 160.0F);
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);

        listScroll += (listTarget - listScroll) * 0.35F;
        detScroll += (detTarget - detScroll) * 0.35F;

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        RenderUtil.rect(0, 0, width, height, ((int) (0x50 * ease)) << 24);

        GlStateManager.pushMatrix();
        float scale = 0.94F + 0.06F * ease;
        GlStateManager.translate(width / 2.0F, height / 2.0F, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(-width / 2.0F, -height / 2.0F, 0);

        if (t >= 1.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x0, y0, 0);
            RenderUtil.blur(w, h, 10, x0, y0, 1.0F, sf);
            GlStateManager.popMatrix();
        }
        RenderUtil.roundedRect(x0, y0, w, h, 10, WINDOW);
        RenderUtil.roundedOutline(x0, y0, w, h, 10, 0x20FFFFFF);

        drawHeader(mouseX, mouseY);
        drawList(mouseX, mouseY, now);
        drawDetail(mouseX, mouseY, now);

        GlStateManager.popMatrix();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private void drawHeader(int mx, int my) {
        UiFont fr = UiFont.get();
        fr.drawStringWithShadow("§e§lMush§b§lInfo", x0 + 12, y0 + 9, 0xFFFFFFFF);

        float bx = x0 + w - 10;
        bx -= 12;
        final float closeX = bx;
        boolean hoverClose = RenderUtil.inside(mx, my, closeX, y0 + 6, 12, 12);
        fr.drawStringWithShadow("\u00d7", closeX + 2, y0 + 8, hoverClose ? RED : MUTED);
        buttons.add(new Button(closeX, y0 + 6, 12, 12, () -> mc.displayGuiScreen(null)));

        // Janela estreita: rótulos curtos para não cobrir o título.
        boolean compact = w < fr.getStringWidth("MushInfo modera\u00e7\u00e3o  Blur  Alertas no chat  Experimentais  X") + 90;
        bx = toggle(mx, my, bx - 6, compact ? "Exp." : "Experimentais", Settings.experimentalAlerts, () -> {
            Settings.experimentalAlerts = !Settings.experimentalAlerts;
            Settings.save();
        });
        bx = toggle(mx, my, bx - 4, compact ? "Chat" : "Alertas no chat", Settings.chatAlerts, () -> {
            Settings.chatAlerts = !Settings.chatAlerts;
            Settings.save();
        });
        bx = toggle(mx, my, bx - 4, "Blur", Settings.blur, () -> {
            Settings.blur = !Settings.blur;
            Settings.save();
        });
        if (bx > x0 + 20 + fr.getStringWidth("\u00a7lMushInfo modera\u00e7\u00e3o")) {
            fr.drawStringWithShadow("modera\u00e7\u00e3o", x0 + 14 + fr.getStringWidth("\u00a7lMushInfo"), y0 + 9, MUTED);
        }
    }

    /** Botão liga/desliga terminando em {@code right}. Retorna a borda esquerda. */
    private float toggle(int mx, int my, float right, String label, boolean on, Runnable action) {
        UiFont fr = UiFont.get();
        float bw = fr.getStringWidth(label) + 18;
        float bx = right - bw, by = y0 + 6;
        boolean hover = RenderUtil.inside(mx, my, bx, by, bw, 14);
        RenderUtil.roundedRect(bx, by, bw, 14, 7, on ? ACCENT_BG : (hover ? 0x30FFFFFF : CELL));
        RenderUtil.circle(bx + 7, by + 7, 2.5F, on ? ACCENT : MUTED);
        fr.drawStringWithShadow(label, bx + 13, by + 3, on ? 0xFFFFFFFF : 0xFFBBBBBB);
        buttons.add(new Button(bx, by, bw, 14, action));
        return bx;
    }

    private void drawList(int mx, int my, long now) {
        UiFont fr = UiFont.get();
        RenderUtil.roundedRect(listX, listY, listW, listH, 7, PANEL);

        // Busca
        RenderUtil.roundedRect(listX + 4, listY + 4, listW - 8, 15, 5, search.isFocused() ? 0x30FFFFFF : CELL);
        if (search.getText().isEmpty() && !search.isFocused()) {
            fr.drawStringWithShadow("Buscar jogador... \u00a78(" + entries.size() + ")", listX + 8, listY + 7, 0xFF707078);
        }
        String typed = search.getText();
        boolean caret = search.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0;
        fr.drawStringWithShadow(typed + (caret ? "\u00a77|" : ""), listX + 8, listY + 7, 0xFFFFFFFF);

        int top = listY + 23, viewH = listH - 27;
        listContentH = entries.size() * ROW_H;
        clampList(viewH);

        RenderUtil.scissor(listX, top, listW, viewH);
        float y = top - listScroll;
        for (Entry e : entries) {
            if (y + ROW_H < top || y > top + viewH) {
                y += ROW_H;
                continue;
            }
            boolean sel = e.name.equalsIgnoreCase(selected);
            boolean hover = drag == 0 && RenderUtil.inside(mx, my, listX + 3, y, listW - 9, ROW_H - 2) && my >= top && my < top + viewH;
            if (sel) {
                RenderUtil.roundedRect(listX + 3, y + 1, listW - 9, ROW_H - 2, 5, ACCENT_BG);
            } else if (hover) {
                RenderUtil.roundedRect(listX + 3, y + 1, listW - 9, ROW_H - 2, 5, HOVER);
            }
            RenderUtil.drawHead(skin(e), listX + 7, (int) y + 4, 16);

            PlayerStats s = PlayerChecks.INSTANCE.peek(e.name);
            boolean active = s != null && s.hasActiveAlert(now);
            fr.drawStringWithShadow(e.name, listX + 28, y + 4, active ? 0xFFFF7777 : 0xFFFFFFFF);
            int acc = account(e);
            String accText = acc == 1 ? "§aOriginal" : acc == 2 ? "§7Pirata" : "§8?";
            small(accText + (e.entity == null ? " §8· longe" : ""), listX + 28, y + 14, 0xFFFFFFFF);

            float rx = listX + listW - 10;
            int flags = s != null ? s.totalFlags() : 0;
            if (flags > 0) {
                String n = String.valueOf(flags);
                float bw = Math.max(12, fr.getStringWidth(n) + 6);
                rx -= bw;
                RenderUtil.roundedRect(rx, y + 6, bw, 11, 5.5F, active ? 0xC0E03030 : 0x80A06020);
                fr.drawStringWithShadow(n, rx + (bw - fr.getStringWidth(n)) / 2.0F, y + 8, 0xFFFFFFFF);
                rx -= 3;
            }
            if (Tracker.isTracked(e.name)) {
                rx -= 9;
                RenderUtil.roundedRect(rx, y + 6, 9, 11, 3, ACCENT_BG);
                small("§bC", rx + 2.5F, y + 8.5F, 0xFFFFFFFF);
            }
            y += ROW_H;
        }
        if (entries.isEmpty()) {
            fr.drawStringWithShadow("Ninguém encontrado", listX + 8, top + 6, MUTED);
        }
        RenderUtil.endScissor();
        scrollbar(listX + listW - 5, top, viewH, listScroll, listContentH, drag == 3);
    }

    private void drawDetail(int mx, int my, long now) {
        UiFont fr = UiFont.get();
        RenderUtil.roundedRect(detX, detY, detW, detH, 7, PANEL);
        if (selected == null) {
            fr.drawStringWithShadow("Selecione um jogador", detX + 10, detY + 10, MUTED);
            return;
        }
        final Entry e = find(selected);
        PlayerData d = MushApi.get(e.name);
        PlayerStats s = PlayerChecks.INSTANCE.peek(e.name);

        // ---- cabeçalho fixo
        RenderUtil.drawHead(skin(e), detX + 8, detY + 8, 34);
        String title = d.username != null ? d.username : e.name;
        GlStateManager.pushMatrix();
        GlStateManager.translate(detX + 50, detY + 9, 0);
        GlStateManager.scale(1.35F, 1.35F, 1);
        fr.drawStringWithShadow("§l" + title, 0, 0, 0xFFFFFFFF);
        GlStateManager.popMatrix();

        float px = detX + 50, py = detY + 26;
        if (d.rank != null) {
            px = pill(px, py, d.rank, d.rankColor) + 4;
        }
        int acc = account(e);
        px = pill(px, py, acc == 1 ? "Original" : acc == 2 ? "Pirata" : "Conta ?", acc == 1 ? 0x55E07A : 0x9A9A9A) + 4;
        if (d.clanTag != null) {
            px = pill(px, py, "[" + d.clanTag + "]", d.clanColor) + 4;
        }
        String where;
        if (e.entity != null && mc.thePlayer != null) {
            where = e.entity instanceof EntityPlayerSP ? "você" : String.format("a %.0f blocos", e.entity.getDistanceToEntity(mc.thePlayer));
        } else {
            where = e.info != null ? "no servidor, fora do alcance" : "offline";
        }
        small(where, detX + 50, detY + 40, MUTED);

        // Botões: Card info e Copiar nick
        boolean tracked = Tracker.isTracked(e.name);
        String cardLabel = tracked ? "Card info  §aON" : "Card info  §7OFF";
        float cw = fr.getStringWidth(cardLabel) + 14, cx = detX + detW - cw - 8, cy = detY + 8;
        boolean hc = RenderUtil.inside(mx, my, cx, cy, cw, 16);
        RenderUtil.roundedRect(cx, cy, cw, 16, 6, tracked ? ACCENT_BG : (hc ? 0x30FFFFFF : CELL));
        if (tracked) {
            RenderUtil.roundedOutline(cx, cy, cw, 16, 6, ACCENT);
        }
        fr.drawStringWithShadow(cardLabel, cx + 7, cy + 4, 0xFFFFFFFF);
        final String name = e.name;
        buttons.add(new Button(cx, cy, cw, 16, () -> {
            if (Tracker.toggle(name)) {
                MushApi.get(name);
            }
        }));
        String copy = "Copiar nick";
        float kw = fr.getStringWidth(copy) + 12, kx = detX + detW - kw - 8, ky = cy + 20;
        boolean hk = RenderUtil.inside(mx, my, kx, ky, kw, 14);
        RenderUtil.roundedRect(kx, ky, kw, 14, 5, hk ? 0x30FFFFFF : CELL);
        fr.drawStringWithShadow(copy, kx + 6, ky + 3, 0xFFDDDDDD);
        buttons.add(new Button(kx, ky, kw, 14, () -> setClipboardString(name)));

        RenderUtil.rect(detX + 8, detY + DETAIL_HEADER_H - 1, detW - 16, 1, 0x20FFFFFF);

        drawTabs(mx, my, e.name, s, now);

        // ---- conteúdo da aba, com rolagem
        int top = contentTop(), viewH = contentViewH();
        clampDetail(viewH);
        RenderUtil.scissor(detX, top, detW, viewH);
        float y = top + 6 - detScroll;
        float start = y;
        if (tab == 0) {
            y = drawInfoGrid(e, d, s, y, now);
        } else if (tab == 1) {
            y = drawAlerts(s, y, now);
            y = drawHistory(s, y + 10, now);
        } else {
            y = drawChat(e.name, y, now);
        }
        detContentH = (int) (y - start) + 10;
        RenderUtil.endScissor();
        scrollbar(detX + detW - 5, top, viewH, detScroll, detContentH, drag == 4);
    }

    private int contentTop() {
        return detY + DETAIL_HEADER_H + TABS_H;
    }

    private int contentViewH() {
        return detH - DETAIL_HEADER_H - TABS_H - 2;
    }

    /** Abas lado a lado com um sublinhado que desliza até a aba ativa. */
    private void drawTabs(int mx, int my, String name, PlayerStats s, long now) {
        UiFont fr = UiFont.get();
        int alerts = s != null ? s.totalFlags() : 0;
        int messages = ChatLog.get(name).size();
        float x = detX + 10, y = detY + DETAIL_HEADER_H + 4;
        float activeX = x, activeW = 0;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            int count = i == 1 ? alerts : i == 2 ? messages : -1;
            float labelW = fr.getStringWidth(TABS[i]);
            String countText = count > 0 ? String.valueOf(count) : null;
            float badgeW = countText != null ? Math.max(11, fr.getStringWidth(countText) * 0.75F + 6) : 0;
            float w = labelW + (countText != null ? badgeW + 4 : 0) + 12;
            boolean active = tab == i;
            boolean hover = RenderUtil.inside(mx, my, x, y, w, 16);
            if (active) {
                RenderUtil.roundedRect(x, y, w, 16, 5, 0x24FFFFFF);
                activeX = x;
                activeW = w;
            } else if (hover) {
                RenderUtil.roundedRect(x, y, w, 16, 5, HOVER);
            }
            fr.drawStringWithShadow(TABS[i], x + 6, y + 4, active ? 0xFFFFFFFF : (hover ? 0xFFCCCCCC : MUTED));
            if (countText != null) {
                boolean hot = i == 1 && s != null && s.hasActiveAlert(now);
                float bx = x + 6 + labelW + 4;
                RenderUtil.roundedRect(bx, y + 3.5F, badgeW, 9, 4.5F, hot ? 0xD0E03030 : (i == 1 ? 0x90A06020 : 0x504FC3F7));
                small(countText, bx + (badgeW - fr.getStringWidth(countText) * 0.75F) / 2.0F, y + 5.5F, 0xFFFFFFFF);
            }
            buttons.add(new Button(x, y, w, 16, () -> selectTab(index)));
            x += w + 4;
        }
        // Sublinhado animado.
        if (tabX < 0) {
            tabX = activeX;
            tabW = activeW;
        }
        tabX += (activeX - tabX) * 0.35F;
        tabW += (activeW - tabW) * 0.35F;
        RenderUtil.roundedRect(tabX + 4, y + 17, tabW - 8, 2, 1, ACCENT);
        RenderUtil.rect(detX + 8, contentTop() - 1, detW - 16, 1, 0x14FFFFFF);
    }

    private void selectTab(int index) {
        if (tab != index) {
            tab = index;
            detTarget = detScroll = 0;
        }
    }

    private float drawInfoGrid(Entry e, PlayerData d, PlayerStats s, float y, long now) {
        List<String[]> cells = new ArrayList<String[]>();
        cells.add(new String[]{"Ping", ping(e)});
        if (s != null && e.entity != null) {
            int cps = s.cps(now);
            boolean self = e.entity instanceof EntityPlayerSP;
            cells.add(new String[]{"CPS", cpsText(cps, self) + " §8/ máx " + cpsText(s.maxCps(), self)});
            double[] r = s.reachStats();
            cells.add(new String[]{"Reach", r == null ? "§8sem hits" : color(r[0], 3.0, 3.3) + String.format("%.2f §8méd / %.2f", r[0], r[1])});
            float aim = s.aimError();
            cells.add(new String[]{"Mira", aim < 0 ? "§8sem dados" : (aim < 3 ? "§c" : aim < 6 ? "§e" : "§a") + String.format("%.1f° erro médio", aim)});
        } else {
            cells.add(new String[]{"CPS", "§8fora do alcance"});
            cells.add(new String[]{"Reach", "§8-"});
            cells.add(new String[]{"Mira", "§8-"});
        }
        if (d.isLoading()) {
            cells.add(new String[]{"Bans", "§8carregando..."});
            cells.add(new String[]{"Mute", "§8carregando..."});
        } else if (d.error != null) {
            cells.add(new String[]{"Bans", "§8" + d.error});
            cells.add(new String[]{"Mute", "§8" + d.error});
        } else {
            cells.add(new String[]{"Bans", d.banned ? "§cBanido" + (d.banBlacklistCount > 0 ? " §7(blacklist " + d.banBlacklistCount + ")" : "")
                    : d.banCount > 0 ? "§6" + d.banCount : "§aNenhum"});
            cells.add(new String[]{"Mute", d.muted ? "§cMutado" : d.muteCount > 0 ? "§6" + d.muteCount : "§aNão"});
            cells.add(new String[]{"Primeiro login", d.firstLogin > 0 ? DATE.format(new Date(d.firstLogin)) : "§8-"});
            cells.add(new String[]{"Último login", d.online ? "§aonline agora" : d.lastLogin > 0 ? DATE.format(new Date(d.lastLogin)) : "§8-"});
            cells.add(new String[]{"Amigos", d.friends >= 0 ? String.valueOf(d.friends) : "§8-"});
        }
        int total = s != null ? s.totalFlags() : 0;
        cells.add(new String[]{"Alertas", total == 0 ? "§a0" : "§c" + total});

        float gap = 5, cw = (detW - 16 - gap) / 2.0F, ch = 24;
        for (int i = 0; i < cells.size(); i++) {
            float cx = detX + 8 + (i % 2) * (cw + gap);
            float cy = y + (i / 2) * (ch + gap);
            RenderUtil.roundedRect(cx, cy, cw, ch, 5, CELL);
            small(cells.get(i)[0].toUpperCase(), cx + 6, cy + 4, MUTED);
            UiFont.get().drawStringWithShadow(UiFont.get().trimStringToWidth(cells.get(i)[1], (int) cw - 10), cx + 6, cy + 13, 0xFFFFFFFF);
        }
        return y + ((cells.size() + 1) / 2) * (ch + gap) - gap;
    }

    private float drawAlerts(PlayerStats s, float y, long now) {
        UiFont fr = UiFont.get();
        List<Check> fired = new ArrayList<Check>();
        if (s != null) {
            for (Check c : Check.values()) {
                if (s.count(c) > 0) {
                    fired.add(c);
                }
            }
            final PlayerStats fs = s;
            Collections.sort(fired, (a, b) -> Long.compare(fs.lastFlag(b).time, fs.lastFlag(a).time));
        }
        if (fired.isEmpty()) {
            fr.drawStringWithShadow("§aNenhum alerta para este jogador", detX + 10, y, 0xFFFFFFFF);
            return y + 10;
        }
        for (Check c : fired) {
            Flag f = s.lastFlag(c);
            boolean active = now - f.time <= PlayerStats.ACTIVE_MS;
            RenderUtil.roundedRect(detX + 8, y - 2, detW - 16, 22, 5, active ? 0x30FF3030 : CELL);
            RenderUtil.circle(detX + 15, y + 3.5F, 2.5F, active ? RED : ORANGE);
            fr.drawStringWithShadow("§l" + c.displayName + " §r§7x" + s.count(c)
                    + (c.experimental ? " §8(experimental)" : ""), detX + 22, y, 0xFFFFFFFF);
            String ago = "há " + ago(now - f.time);
            fr.drawStringWithShadow(ago, detX + detW - 12 - fr.getStringWidth(ago), y, MUTED);
            small(fr.trimStringToWidth(f.reason, (int) ((detW - 40) / 0.75F)), detX + 22, y + 10.5F, 0xFFAAAAAA);
            y += 25;
        }
        return y - 3;
    }

    private float drawHistory(PlayerStats s, float y, long now) {
        List<Flag> history = s != null ? s.history() : new ArrayList<Flag>();
        if (history.isEmpty()) {
            return y - 8;
        }
        UiFont fr = UiFont.get();
        y = section("Histórico de alertas", y);
        int n = 0;
        for (Flag f : history) {
            if (n++ == 15) {
                break;
            }
            String line = "§8" + TIME.format(new Date(f.time)) + "  §c" + f.check.displayName + " §7" + f.reason;
            fr.drawStringWithShadow(fr.trimStringToWidth(line, detW - 20), detX + 10, y, 0xFFFFFFFF);
            y += 11;
        }
        return y - 1;
    }

    private float drawChat(String name, float y, long now) {
        UiFont fr = UiFont.get();
        List<ChatLog.Entry> log = ChatLog.get(name);
        if (log.isEmpty()) {
            fr.drawStringWithShadow("§8Nenhuma mensagem vista ainda", detX + 10, y, 0xFFFFFFFF);
            return y + 10;
        }
        for (ChatLog.Entry m : log) {
            String time = TIME.format(new Date(m.time));
            int tw = fr.getStringWidth(time) + 6;
            fr.drawStringWithShadow(time, detX + 10, y, 0xFF666670);
            List<String> lines = fr.listFormattedStringToWidth(m.text, detW - 24 - tw);
            for (String line : lines) {
                fr.drawStringWithShadow(line, detX + 10 + tw, y, 0xFFE0E0E0);
                y += 10;
            }
            y += 2;
        }
        return y;
    }

    private float section(String title, float y) {
        UiFont.get().drawStringWithShadow("§l" + title, detX + 10, y, ACCENT);
        return y + 13;
    }

    /** Pílula colorida; retorna a borda direita. */
    private float pill(float x, float y, String text, int rgb) {
        UiFont fr = UiFont.get();
        float pw = fr.getStringWidth(text) * 0.75F + 8;
        RenderUtil.roundedRect(x, y, pw, 10, 4, 0x50000000 | (rgb & 0xFFFFFF));
        small(text, x + 4, y + 2, 0xFF000000 | brighten(rgb));
        return x + pw;
    }

    private void small(String text, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.75F, 0.75F, 1);
        UiFont.get().drawStringWithShadow(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void scrollbar(float x, float top, float viewH, float scroll, int contentH, boolean active) {
        if (contentH <= viewH) {
            return;
        }
        float thumbH = Math.max(18, viewH * viewH / contentH);
        float thumbY = top + (viewH - thumbH) * (scroll / (contentH - viewH));
        RenderUtil.roundedRect(x, thumbY, 3, thumbH, 1.5F, active ? 0xC0FFFFFF : 0x50FFFFFF);
    }

    private void clampList(int viewH) {
        float max = Math.max(0, listContentH - viewH);
        listTarget = Math.max(0, Math.min(max, listTarget));
        listScroll = Math.max(0, Math.min(max, listScroll));
    }

    private void clampDetail(int viewH) {
        float max = Math.max(0, detContentH - viewH);
        detTarget = Math.max(0, Math.min(max, detTarget));
        detScroll = Math.max(0, Math.min(max, detScroll));
    }

    // ================================================================ entrada

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        search.mouseClicked(mx, my, button);
        if (button != 0) {
            return;
        }
        for (Button b : new ArrayList<Button>(buttons)) {
            if (RenderUtil.inside(mx, my, b.x, b.y, b.w, b.h)) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                        new ResourceLocation("gui.button.press"), 1.0F));
                b.action.run();
                return;
            }
        }
        int listTop = listY + 23, listView = listH - 27;
        int detTop = contentTop(), detView = contentViewH();
        if (RenderUtil.inside(mx, my, listX + listW - 7, listTop, 7, listView) && listContentH > listView) {
            start(3, my, listTarget);
        } else if (RenderUtil.inside(mx, my, listX, listTop, listW, listView)) {
            start(1, my, listTarget);
        } else if (RenderUtil.inside(mx, my, detX + detW - 7, detTop, 7, detView) && detContentH > detView) {
            start(4, my, detTarget);
        } else if (RenderUtil.inside(mx, my, detX, detTop, detW, detView)) {
            start(2, my, detTarget);
        }
    }

    private void start(int area, int my, float scroll) {
        drag = area;
        pressY = my;
        pressScroll = scroll;
        dragMoved = false;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        int dy = my - pressY;
        if (Math.abs(dy) > 3) {
            dragMoved = true;
        }
        int listView = listH - 27, detView = contentViewH();
        switch (drag) {
            case 1:
                if (dragMoved) {
                    listTarget = listScroll = pressScroll - dy;
                }
                break;
            case 2:
                if (dragMoved) {
                    detTarget = detScroll = pressScroll - dy;
                }
                break;
            case 3:
                listTarget = listScroll = pressScroll + dy * (listContentH / (float) listView);
                break;
            case 4:
                detTarget = detScroll = pressScroll + dy * (detContentH / (float) detView);
                break;
            default:
                break;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (drag == 1 && !dragMoved) {
            int top = listY + 23;
            int index = (int) ((my - top + listScroll) / ROW_H);
            if (index >= 0 && index < entries.size()) {
                String name = entries.get(index).name;
                if (!name.equalsIgnoreCase(selected)) {
                    selected = name;
                    detTarget = detScroll = 0;
                }
            }
        }
        drag = 0;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int dir = wheel > 0 ? -1 : 1;
        if (RenderUtil.inside(mx, my, listX, listY, listW, listH)) {
            listTarget += dir * ROW_H * 2;
        } else if (RenderUtil.inside(mx, my, detX, detY, detW, detH)) {
            detTarget += dir * 36;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        if (search.isFocused()) {
            String before = search.getText();
            search.textboxKeyTyped(c, key);
            if (!before.equals(search.getText())) {
                listTarget = listScroll = 0;
            }
            return;
        }
        if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            selectTab((tab + (key == Keyboard.KEY_RIGHT ? 1 : TABS.length - 1)) % TABS.length);
            return;
        }
        if (key == Keyboard.KEY_DOWN || key == Keyboard.KEY_UP) {
            int i = -1;
            for (int k = 0; k < entries.size(); k++) {
                if (entries.get(k).name.equalsIgnoreCase(selected)) {
                    i = k;
                }
            }
            i = Math.max(0, Math.min(entries.size() - 1, i + (key == Keyboard.KEY_DOWN ? 1 : -1)));
            if (i >= 0 && i < entries.size()) {
                selected = entries.get(i).name;
                detTarget = detScroll = 0;
                float rowTop = i * ROW_H, view = listH - 27;
                if (rowTop < listTarget) {
                    listTarget = rowTop;
                } else if (rowTop + ROW_H > listTarget + view) {
                    listTarget = rowTop + ROW_H - view;
                }
            }
        } else if (Character.isLetterOrDigit(c) || c == '_') {
            // Começar a digitar já busca.
            search.setFocused(true);
            search.textboxKeyTyped(c, key);
            listTarget = listScroll = 0;
        }
    }

    // ================================================================ texto

    private String ping(Entry e) {
        if (e.info == null) {
            return "§8?";
        }
        int ms = e.info.getResponseTime();
        return (ms < 80 ? "§a" : ms < 150 ? "§e" : ms < 250 ? "§6" : "§c") + ms + "ms";
    }

    private static String cpsText(int cps, boolean self) {
        if (!self && cps >= PlayerStats.SWING_CAP) {
            return "§e7+";
        }
        return (cps >= 16 ? "§c" : cps >= 11 ? "§e" : "§a") + cps;
    }

    private static String color(double v, double warn, double bad) {
        return v >= bad ? "§c" : v >= warn ? "§e" : "§a";
    }

    static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return (s / 60) + "min";
        }
        return (s / 3600) + "h";
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 255) + 60);
        int g = Math.min(255, (rgb >> 8 & 255) + 60);
        int b = Math.min(255, (rgb & 255) + 60);
        return r << 16 | g << 8 | b;
    }
}
