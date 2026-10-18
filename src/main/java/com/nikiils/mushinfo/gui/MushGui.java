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
 * ClickGUI (tecla R por padrão, troca com ".bind clickgui <tecla>"): lista de jogadores à esquerda (cabeça, original/pirata, alertas, busca) e os
 * detalhes do selecionado à direita (informações, alertas, histórico e chat). As duas colunas rolam
 * com a roda do mouse ou segurando e arrastando.
 */
public class MushGui extends GuiScreen {

    // ---------------------------------------------------------------- cores
    private static final int WINDOW = 0xF20B0C10;
    private static final int WINDOW_EDGE = 0xFF22242D;
    private static final int PANEL = 0xFF101116;
    private static final int PANEL_EDGE = 0xFF1B1D24;
    private static final int CELL = 0xFF15161C;
    private static final int CELL_EDGE = 0xFF20222A;
    private static final int HOVER = 0xFF1C1E27;
    private static final int SELECTED = 0xFF1A1E29;
    private static final int ACCENT = 0xFF4FC3F7;
    private static final int ACCENT_DARK = 0xFF2B86B0;
    private static final int ACCENT_BG = 0x2A4FC3F7;
    private static final int TEXT = 0xFFE9EAEE;
    private static final int MUTED = 0xFF7C7F8C;
    private static final int RED = 0xFFFF5555;
    private static final int ORANGE = 0xFFFFAA33;
    private static final int GREEN = 0xFF55E07A;
    private static final int OPEN_MS = 260;
    private static final int CLOSE_MS = 170;

    private static final int ROW_H = 24;
    private static final int HEADER_H = 32;
    private static final int DETAIL_HEADER_H = 50;
    private static final int TABS_H = 24;
    private static final String[] TABS = {"Infos", "Alertas", "Mensagens"};

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy");

    /** Jogador selecionado (continua selecionado ao reabrir). */
    private static String selected;
    /** Aba aberta nos detalhes (0 Infos, 1 Alertas, 2 Mensagens); continua ao trocar de jogador. */
    private static int tab;
    /** Tela principal: 0 = Jogadores, 1 = Checks. */
    private static int view;
    private float checkScroll, checkTarget;
    private int checkContentH;
    /** Cards de check visíveis neste frame (clique sem arrastar alterna o check). */
    private final List<Button> checkCards = new ArrayList<Button>();
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
    /** Animações de hover/estado por elemento (0..1), suavizadas pelo tempo entre frames. */
    private final Map<String, Float> anims = new java.util.HashMap<String, Float>();
    private long lastFrame;
    private float dt;
    private boolean closing;
    private long closeStart;
    private long viewChangedAt, tabChangedAt;
    private float viewX = -1, viewW;

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

    /** Troca a tecla de abrir a GUI. Retorna false se o nome da tecla não existe. */
    public static boolean bind(String keyName) {
        int code = Keyboard.getKeyIndex(keyName.toUpperCase());
        if (code == Keyboard.KEY_NONE) {
            return false;
        }
        Opener.KEY.setKeyCode(code);
        KeyBinding.resetKeyBindingArrayAndHash();
        Minecraft.getMinecraft().gameSettings.saveOptions();
        return true;
    }

    public static String boundKey() {
        return Keyboard.getKeyName(Opener.KEY.getKeyCode());
    }

    /** Abre no próximo tick (o chat fecha a si mesmo logo depois de mandar a mensagem). */
    public static void requestOpen(String name) {
        pendingOpen = name;
        pending = true;
    }

    /** Tecla e abertura agendada. */
    public static final class Opener {
        /** Tecla que abre a GUI. Fica salva no options.txt do Minecraft como qualquer outra tecla. */
        static final KeyBinding KEY = new KeyBinding("Abrir MushInfo", Keyboard.KEY_R, "MushInfo");
        private final KeyBinding key = KEY;

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
        long now = System.currentTimeMillis();
        dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        if (closing && now - closeStart >= CLOSE_MS) {
            RenderUtil.globalAlpha = 1.0F;
            mc.displayGuiScreen(null);
            return;
        }
        // p: 0 = fechado, 1 = aberto. Abre com ease-out, fecha com ease-in.
        float p = closing ? 1 - easeIn((now - closeStart) / (float) CLOSE_MS) : easeOut((now - openedAt) / (float) OPEN_MS);

        rebuildEntries();
        buttons.clear();
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();

        listScroll += (listTarget - listScroll) * 0.35F;
        detScroll += (detTarget - detScroll) * 0.35F;

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        // Fundo escurecido com vinheta (bordas mais escuras que o centro).
        RenderUtil.rect(0, 0, width, height, ((int) (0x96 * p)) << 24);
        int edge = ((int) (0x80 * p)) << 24;
        float vw = width * 0.22F, vh = height * 0.22F;
        RenderUtil.gradientH(0, 0, vw, height, edge, 0);
        RenderUtil.gradientH(width - vw, 0, vw, height, 0, edge);
        RenderUtil.gradient(0, 0, width, vh, edge, 0);
        RenderUtil.gradient(0, height - vh, width, vh, 0, edge);

        GlStateManager.pushMatrix();
        float scale = 0.92F + 0.08F * p;
        GlStateManager.translate(width / 2.0F, height / 2.0F + (1 - p) * 14, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(-width / 2.0F, -height / 2.0F, 0);
        RenderUtil.globalAlpha = p;

        RenderUtil.shadow(x0, y0, w, h, 12, 14, 0xD0);
        if (p >= 1.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x0, y0, 0);
            RenderUtil.blur(w, h, 12, x0, y0, 1.0F, sf);
            GlStateManager.popMatrix();
        }
        RenderUtil.roundedRect(x0, y0, w, h, 12, WINDOW);
        RenderUtil.roundedOutline(x0, y0, w, h, 12, WINDOW_EDGE);
        RenderUtil.rect(x0 + 10, y0 + HEADER_H - 5, w - 20, 1, PANEL_EDGE);

        drawHeader(mouseX, mouseY);
        // Troca entre Jogadores/Checks: o conteúdo entra com fade e sobe um pouco.
        float c = easeOut((now - viewChangedAt) / 200.0F);
        RenderUtil.globalAlpha = p * c;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, (1 - c) * 6, 0);
        if (view == 0) {
            drawList(mouseX, mouseY, now);
            drawDetail(mouseX, mouseY, now);
        } else {
            drawChecks(mouseX, mouseY);
        }
        GlStateManager.popMatrix();

        RenderUtil.globalAlpha = 1.0F;
        GlStateManager.popMatrix();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private float anim(String key, boolean on, float speed) {
        Float v = anims.get(key);
        float target = on ? 1 : 0;
        float cur = v == null ? target : v;
        cur += (target - cur) * Math.min(1, dt * speed);
        anims.put(key, cur);
        return cur;
    }

    private static float easeOut(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static float easeIn(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t;
    }

    /** Fecha com animação (a GUI some de verdade quando a animação termina). */
    private void close() {
        if (!closing) {
            closing = true;
            closeStart = System.currentTimeMillis();
        }
    }

    /** Botão padrão: fundo escuro com borda, hover suave; {@code active} = preenchido com a cor de destaque. */
    private void button(String key, float x, float y, float bw, float bh, String label, boolean active,
                        int mx, int my, Runnable action) {
        UiFont fr = UiFont.get();
        boolean hover = !closing && RenderUtil.inside(mx, my, x, y, bw, bh);
        float hv = anim("btn:" + key, hover, 14);
        float on = anim("btnOn:" + key, active, 12);
        int fill = RenderUtil.mix(RenderUtil.mix(CELL, HOVER, hv), RenderUtil.mix(ACCENT_DARK, 0xFF3497C4, hv), on);
        int border = RenderUtil.mix(RenderUtil.mix(CELL_EDGE, 0xFF3A3D4A, hv), ACCENT, on);
        RenderUtil.roundedRect(x, y, bw, bh, 6, fill);
        RenderUtil.roundedOutline(x, y, bw, bh, 6, border);
        fr.drawStringWithShadow(label, x + (bw - fr.getStringWidth(label)) / 2.0F, y + (bh - 8) / 2.0F,
                RenderUtil.mix(TEXT, 0xFFFFFFFF, on));
        buttons.add(new Button(x, y, bw, bh, action));
    }

    private void drawHeader(int mx, int my) {
        UiFont fr = UiFont.get();
        float cy = y0 + (HEADER_H - 4) / 2.0F; // centro vertical do cabeçalho
        fr.drawStringWithShadow("\u00a7e\u00a7lMush\u00a7b\u00a7lInfo", x0 + 14, cy - 4, 0xFFFFFFFF);

        // Fechar: botão redondo que fica vermelho no hover.
        float closeX = x0 + w - 20;
        boolean hoverClose = !closing && RenderUtil.inside(mx, my, closeX - 8, cy - 8, 16, 16);
        float hc = anim("close", hoverClose, 14);
        RenderUtil.circle(closeX, cy, 7.5F, RenderUtil.mix(CELL, 0xFFD94444, hc));
        int xColor = RenderUtil.mix(MUTED, 0xFFFFFFFF, hc);
        RenderUtil.line(closeX - 2.8F, cy - 2.8F, closeX + 2.8F, cy + 2.8F, 1.6F, xColor);
        RenderUtil.line(closeX - 2.8F, cy + 2.8F, closeX + 2.8F, cy - 2.8F, 1.6F, xColor);
        buttons.add(new Button(closeX - 8, cy - 8, 16, 16, this::close));

        // Abas principais: controle segmentado com destaque que desliza.
        String[] views = {"Jogadores", "Checks"};
        float sx = x0 + 26 + fr.getStringWidth("\u00a7lMushInfo"), sy = cy - 8.5F;
        float total = 4;
        float[] xs = new float[views.length], ws = new float[views.length];
        for (int i = 0; i < views.length; i++) {
            xs[i] = sx + total;
            ws[i] = fr.getStringWidth(views[i]) + 16;
            total += ws[i] + 2;
        }
        total += 2;
        RenderUtil.roundedRect(sx, sy, total, 17, 8.5F, PANEL);
        RenderUtil.roundedOutline(sx, sy, total, 17, 8.5F, PANEL_EDGE);
        if (viewX < 0) {
            viewX = xs[view];
            viewW = ws[view];
        }
        float k = Math.min(1, dt * 16);
        viewX += (xs[view] - viewX) * k;
        viewW += (ws[view] - viewW) * k;
        RenderUtil.roundedRect(viewX, sy + 2, viewW, 13, 6.5F, ACCENT_DARK);
        for (int i = 0; i < views.length; i++) {
            final int index = i;
            boolean hover = !closing && RenderUtil.inside(mx, my, xs[i], sy, ws[i], 17);
            float hv = anim("view" + i, hover || view == i, 14);
            fr.drawStringWithShadow(views[i], xs[i] + 8, sy + 4.5F, RenderUtil.mix(MUTED, 0xFFFFFFFF, hv));
            buttons.add(new Button(xs[i], sy, ws[i], 17, () -> {
                if (view != index) {
                    view = index;
                    viewChangedAt = System.currentTimeMillis();
                }
                drag = 0;
            }));
        }

        // Interruptores à direita: rótulos curtos se não couberem entre as abas e o botão de fechar.
        float segEnd = sx + total + 14, right = closeX - 18;
        String[] full = {"Experimentais", "Alertas no chat", "Blur"};
        float need = 0;
        for (String l : full) {
            need += fr.getStringWidth(l) + 26 + 12;
        }
        boolean compact = need > right - segEnd;
        float bx = right;
        bx = toggle(mx, my, bx, compact ? "Exp." : "Experimentais", Settings.experimentalAlerts, () -> {
            Settings.experimentalAlerts = !Settings.experimentalAlerts;
            Settings.save();
        }) - 12;
        bx = toggle(mx, my, bx, compact ? "Chat" : "Alertas no chat", Settings.chatAlerts, () -> {
            Settings.chatAlerts = !Settings.chatAlerts;
            Settings.save();
        }) - 12;
        toggle(mx, my, bx, "Blur", Settings.blur, () -> {
            Settings.blur = !Settings.blur;
            Settings.save();
        });
    }

    /** Rótulo + interruptor animado terminando em {@code right}. Retorna a borda esquerda. */
    private float toggle(int mx, int my, float right, String label, boolean on, Runnable action) {
        UiFont fr = UiFont.get();
        float lw = fr.getStringWidth(label);
        float bw = lw + 6 + 20;
        float bx = right - bw, cy = y0 + (HEADER_H - 4) / 2.0F;
        boolean hover = !closing && RenderUtil.inside(mx, my, bx - 2, cy - 8, bw + 4, 16);
        float hv = anim("tgH:" + label, hover, 14);
        float a = anim("tg:" + label, on, 16);
        fr.drawStringWithShadow(label, bx, cy - 4, RenderUtil.mix(MUTED, TEXT, Math.max(hv, a * 0.6F)));
        float tx = bx + lw + 6, ty = cy - 5;
        RenderUtil.roundedRect(tx, ty, 20, 10, 5, RenderUtil.mix(RenderUtil.mix(0xFF262833, 0xFF30333F, hv), ACCENT_DARK, a));
        RenderUtil.circle(tx + 5 + 10 * a, ty + 5, 3.6F, RenderUtil.mix(0xFF9A9DA8, 0xFFFFFFFF, a));
        buttons.add(new Button(bx - 2, cy - 8, bw + 4, 16, action));
        return bx;
    }

    private void drawList(int mx, int my, long now) {
        UiFont fr = UiFont.get();
        float base = RenderUtil.globalAlpha;
        RenderUtil.roundedRect(listX, listY, listW, listH, 8, PANEL);
        RenderUtil.roundedOutline(listX, listY, listW, listH, 8, PANEL_EDGE);

        // Busca
        float sh = anim("search", search.isFocused(), 12);
        RenderUtil.roundedRect(listX + 5, listY + 5, listW - 10, 16, 6, RenderUtil.mix(CELL, HOVER, sh));
        RenderUtil.roundedOutline(listX + 5, listY + 5, listW - 10, 16, 6, RenderUtil.mix(CELL_EDGE, ACCENT, sh * 0.8F));
        if (search.getText().isEmpty() && !search.isFocused()) {
            fr.drawStringWithShadow("Buscar jogador... \u00a78(" + entries.size() + ")", listX + 11, listY + 9, 0xFF62656F);
        }
        String typed = search.getText();
        boolean caret = search.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0;
        fr.drawStringWithShadow(typed + (caret ? "\u00a77|" : ""), listX + 11, listY + 9, TEXT);

        int top = listY + 26, viewH = listH - 30;
        listContentH = entries.size() * ROW_H;
        clampList(viewH);

        RenderUtil.scissor(listX, top, listW, viewH);
        float y = top - listScroll;
        int index = 0;
        for (Entry e : entries) {
            int i = index++;
            if (y + ROW_H < top || y > top + viewH) {
                y += ROW_H;
                continue;
            }
            // Ao abrir, as linhas entram uma depois da outra.
            float rp = easeOut((now - openedAt - i * 22) / 240.0F);
            RenderUtil.globalAlpha = base * rp;
            float ox = (1 - rp) * 14;

            boolean sel = e.name.equalsIgnoreCase(selected);
            boolean hover = drag == 0 && !closing && RenderUtil.inside(mx, my, listX + 4, y, listW - 11, ROW_H - 2)
                    && my >= top && my < top + viewH;
            float hv = anim("row:" + e.name, hover, 14);
            if (sel) {
                RenderUtil.roundedRect(listX + 4 + ox, y + 1, listW - 11, ROW_H - 2, 6, SELECTED);
                RenderUtil.roundedOutline(listX + 4 + ox, y + 1, listW - 11, ROW_H - 2, 6, 0xFF2A3446);
                RenderUtil.roundedRect(listX + 4 + ox, y + 6, 2.5F, ROW_H - 12, 1.25F, ACCENT);
            } else if (hv > 0.01F) {
                RenderUtil.roundedRect(listX + 4 + ox, y + 1, listW - 11, ROW_H - 2, 6, RenderUtil.mix(HOVER & 0x00FFFFFF, HOVER, hv));
            }
            RenderUtil.roundedRect(listX + 10 + ox, y + 3, 18, 18, 3, 0xFF07080B);
            RenderUtil.drawHead(skin(e), (int) (listX + 11 + ox), (int) y + 4, 16);

            PlayerStats s = PlayerChecks.INSTANCE.peek(e.name);
            boolean active = s != null && s.hasActiveAlert(now);
            fr.drawStringWithShadow(e.name, listX + 33 + ox, y + 4, active ? 0xFFFF7A7A : TEXT);
            int acc = account(e);
            String accText = acc == 1 ? "\u00a7aOriginal" : acc == 2 ? "\u00a77Pirata" : "\u00a78?";
            small(accText + (e.entity == null ? " \u00a78\u00b7 longe" : ""), listX + 33 + ox, y + 14, 0xFFFFFFFF);

            float rx = listX + listW - 12 + ox;
            int flags = s != null ? s.totalFlags() : 0;
            if (flags > 0) {
                String n = String.valueOf(flags);
                float bw = Math.max(13, fr.getStringWidth(n) + 7);
                rx -= bw;
                RenderUtil.roundedRect(rx, y + 6, bw, 12, 6, active ? 0xFFC93434 : 0xFF7A5222);
                fr.drawStringWithShadow(n, rx + (bw - fr.getStringWidth(n)) / 2.0F, y + 8, 0xFFFFFFFF);
                rx -= 3;
            }
            if (Tracker.isTracked(e.name)) {
                rx -= 12;
                RenderUtil.roundedRect(rx, y + 6, 12, 12, 6, ACCENT_DARK);
                small("C", rx + 4, y + 9, 0xFFFFFFFF);
            }
            y += ROW_H;
        }
        RenderUtil.globalAlpha = base;
        if (entries.isEmpty()) {
            fr.drawStringWithShadow("Ningu\u00e9m encontrado", listX + 10, top + 6, MUTED);
        }
        RenderUtil.endScissor();
        scrollbar(listX + listW - 5, top, viewH, listScroll, listContentH, drag == 3);
    }

    private void drawDetail(int mx, int my, long now) {
        UiFont fr = UiFont.get();
        RenderUtil.roundedRect(detX, detY, detW, detH, 8, PANEL);
        RenderUtil.roundedOutline(detX, detY, detW, detH, 8, PANEL_EDGE);
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
        final String name = e.name;
        float bw = 84, bx = detX + detW - bw - 10;
        button("card", bx, detY + 9, bw, 18, tracked ? "Card ativo" : "Card info", tracked, mx, my, () -> {
            if (Tracker.toggle(name)) {
                MushApi.get(name);
            }
        });
        button("copy", bx, detY + 30, bw, 15, "Copiar nick", false, mx, my, () -> setClipboardString(name));

        RenderUtil.rect(detX + 10, detY + DETAIL_HEADER_H - 1, detW - 20, 1, PANEL_EDGE);

        drawTabs(mx, my, e.name, s, now);

        // ---- conteúdo da aba, com rolagem
        int top = contentTop(), viewH = contentViewH();
        clampDetail(viewH);
        RenderUtil.scissor(detX, top, detW, viewH);
        float tc = easeOut((now - tabChangedAt) / 180.0F);
        float baseAlpha = RenderUtil.globalAlpha;
        RenderUtil.globalAlpha = baseAlpha * tc;
        float y = top + 6 - detScroll + (1 - tc) * 6;
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
        RenderUtil.globalAlpha = baseAlpha;
        RenderUtil.endScissor();
        scrollbar(detX + detW - 5, top, viewH, detScroll, detContentH, drag == 4);
    }

    // ================================================================ aba Checks

    private static final int CHECK_H = 32;

    /** Liga/desliga cada check. Desligado = não analisa, não conta e não avisa. */
    private void drawChecks(int mx, int my) {
        UiFont fr = UiFont.get();
        long now = System.currentTimeMillis();
        float base = RenderUtil.globalAlpha;
        float ax = listX, ay = listY, aw = w - 16, ah = listH;
        RenderUtil.roundedRect(ax, ay, aw, ah, 8, PANEL);
        RenderUtil.roundedOutline(ax, ay, aw, ah, 8, PANEL_EDGE);
        fr.drawStringWithShadow("\u00a7lChecks", ax + 12, ay + 9, TEXT);
        small("Desligado = n\u00e3o analisa, n\u00e3o conta alerta e n\u00e3o avisa no chat.", ax + 12, ay + 20, MUTED);

        float bw1 = fr.getStringWidth("S\u00f3 confi\u00e1veis") + 18, bw2 = fr.getStringWidth("Ligar todos") + 18;
        float bx = ax + aw - 10 - bw1;
        button("onlyReliable", bx, ay + 8, bw1, 17, "S\u00f3 confi\u00e1veis", false, mx, my, () -> {
            for (Check c : Check.values()) {
                Settings.setEnabled(c, !c.experimental);
            }
        });
        button("enableAll", bx - 6 - bw2, ay + 8, bw2, 17, "Ligar todos", false, mx, my, () -> {
            for (Check c : Check.values()) {
                Settings.setEnabled(c, true);
            }
        });
        RenderUtil.rect(ax + 10, ay + 31, aw - 20, 1, PANEL_EDGE);

        int top = (int) ay + 36, viewH = (int) ah - 40;
        Check[] checks = Check.values();
        int rows = (checks.length + 1) / 2;
        checkContentH = rows * (CHECK_H + 6);
        float max = Math.max(0, checkContentH - viewH);
        checkTarget = Math.max(0, Math.min(max, checkTarget));
        checkScroll += (checkTarget - checkScroll) * 0.35F;
        checkScroll = Math.max(0, Math.min(max, checkScroll));

        checkCards.clear();
        RenderUtil.scissor(ax, top, aw, viewH);
        float gap = 6, cw = (aw - 18 - gap) / 2.0F;
        long shownAt = Math.max(openedAt, viewChangedAt);
        for (int i = 0; i < checks.length; i++) {
            final Check c = checks[i];
            float cx = ax + 9 + (i % 2) * (cw + gap);
            float cy = top + (i / 2) * (CHECK_H + 6) - checkScroll;
            if (cy + CHECK_H < top || cy > top + viewH) {
                continue;
            }
            float rp = easeOut((now - shownAt - i * 18) / 240.0F);
            RenderUtil.globalAlpha = base * rp;
            cy += (1 - rp) * 8;
            boolean on = Settings.isEnabled(c);
            boolean hover = drag == 0 && !closing && RenderUtil.inside(mx, my, cx, cy, cw, CHECK_H) && my >= top && my < top + viewH;
            float hv = anim("chk:" + c, hover, 14);
            float a = anim("sw:" + c, on, 16);
            RenderUtil.roundedRect(cx, cy, cw, CHECK_H, 7, RenderUtil.mix(CELL, HOVER, hv));
            RenderUtil.roundedOutline(cx, cy, cw, CHECK_H, 7, RenderUtil.mix(CELL_EDGE, 0xFF33364A, hv));
            fr.drawStringWithShadow("\u00a7l" + c.displayName, cx + 10, cy + 6, RenderUtil.mix(0xFF80838E, 0xFFFFFFFF, a));
            if (c.experimental) {
                float ex = cx + 10 + fr.getStringWidth("\u00a7l" + c.displayName) + 5;
                RenderUtil.roundedRect(ex, cy + 5.5F, fr.getStringWidth("exp.") * 0.75F + 7, 9, 4.5F, 0xFF4A3418);
                small("exp.", ex + 3.5F, cy + 7.5F, 0xFFFFC27A);
            }
            small(fr.trimStringToWidth(c.description, (int) ((cw - 54) / 0.75F)), cx + 10, cy + 18,
                    RenderUtil.mix(0xFF5E616B, 0xFFA3A6B0, a));
            float sx = cx + cw - 34, sy = cy + CHECK_H / 2.0F - 6;
            RenderUtil.roundedRect(sx, sy, 24, 12, 6, RenderUtil.mix(RenderUtil.mix(0xFF262833, 0xFF30333F, hv), ACCENT_DARK, a));
            RenderUtil.circle(sx + 6 + 12 * a, sy + 6, 4.2F, RenderUtil.mix(0xFF9A9DA8, 0xFFFFFFFF, a));
            if (my >= top && my < top + viewH) {
                checkCards.add(new Button(cx, cy, cw, CHECK_H, () -> Settings.setEnabled(c, !Settings.isEnabled(c))));
            }
        }
        RenderUtil.globalAlpha = base;
        RenderUtil.endScissor();
        scrollbar(ax + aw - 5, top, viewH, checkScroll, checkContentH, false);
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
                RenderUtil.roundedRect(x, y, w, 16, 5, SELECTED);
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
            tabChangedAt = System.currentTimeMillis();
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
            RenderUtil.roundedRect(cx, cy, cw, ch, 6, CELL);
            RenderUtil.roundedOutline(cx, cy, cw, ch, 6, CELL_EDGE);
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
            RenderUtil.roundedRect(detX + 8, y - 2, detW - 16, 22, 6, active ? 0xFF2A1416 : CELL);
            RenderUtil.roundedOutline(detX + 8, y - 2, detW - 16, 22, 6, active ? 0xFF5A2226 : CELL_EDGE);
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
        RenderUtil.roundedRect(x, top, 2.5F, viewH, 1.25F, 0x10FFFFFF);
        RenderUtil.roundedRect(x, thumbY, 2.5F, thumbH, 1.25F, active ? ACCENT : 0x60FFFFFF);
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
        if (closing) {
            return;
        }
        if (view == 0) {
            search.mouseClicked(mx, my, button);
        } else {
            search.setFocused(false);
        }
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
        if (view == 1) {
            if (RenderUtil.inside(mx, my, listX, listY + 36, w - 16, listH - 40)) {
                start(5, my, checkTarget);
            }
            return;
        }
        int listTop = listY + 26, listView = listH - 30;
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
        int listView = listH - 30, detView = contentViewH();
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
            case 5:
                if (dragMoved) {
                    checkTarget = checkScroll = pressScroll - dy;
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (drag == 5 && !dragMoved) {
            for (Button b : new ArrayList<Button>(checkCards)) {
                if (RenderUtil.inside(mx, my, b.x, b.y, b.w, b.h)) {
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                            new ResourceLocation("gui.button.press"), 1.0F));
                    b.action.run();
                    break;
                }
            }
        }
        if (drag == 1 && !dragMoved) {
            int top = listY + 26;
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
        if (view == 1) {
            checkTarget += dir * 36;
            return;
        }
        if (RenderUtil.inside(mx, my, listX, listY, listW, listH)) {
            listTarget += dir * ROW_H * 2;
        } else if (RenderUtil.inside(mx, my, detX, detY, detW, detH)) {
            detTarget += dir * 36;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (closing) {
            return;
        }
        // A tecla de abrir também fecha, menos enquanto digita na busca.
        if (key == Keyboard.KEY_ESCAPE || (key == Opener.KEY.getKeyCode() && !search.isFocused())) {
            close();
            return;
        }
        if (view == 1) {
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
                float rowTop = i * ROW_H, view = listH - 30;
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
