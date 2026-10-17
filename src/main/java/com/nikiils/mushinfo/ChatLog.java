package com.nikiils.mushinfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda as últimas mensagens de chat de cada jogador. O formato do chat muda de servidor para
 * servidor ("[Mod] nick: oi", "nick » oi"...), então procura o separador e pega, antes dele, a última
 * palavra que é o nick de alguém online.
 */
public final class ChatLog {

    private static final int MAX = 100;
    private static final String[] SEPARATORS = {": ", " » ", "» ", "> ", " - "};
    private static final Map<String, ArrayDeque<Entry>> LOGS = new ConcurrentHashMap<String, ArrayDeque<Entry>>();

    public static final class Entry {
        public final long time;
        public final String text;

        Entry(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    /** Mensagens do jogador, da mais nova para a mais antiga. */
    public static List<Entry> get(String name) {
        ArrayDeque<Entry> q = LOGS.get(name.toLowerCase());
        if (q == null) {
            return new ArrayList<Entry>();
        }
        synchronized (q) {
            return new ArrayList<Entry>(q);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2 || event.message == null) {
            return; // barra de ação
        }
        String raw = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (raw == null || raw.startsWith("[MushInfo]")) {
            return;
        }
        int idx = -1, len = 0;
        for (String sep : SEPARATORS) {
            int i = raw.indexOf(sep);
            if (i > 0 && i < 80 && (idx < 0 || i < idx)) {
                idx = i;
                len = sep.length();
            }
        }
        if (idx < 0) {
            return;
        }
        String message = raw.substring(idx + len).trim();
        if (message.isEmpty()) {
            return;
        }
        Set<String> online = onlineNames();
        String[] tokens = raw.substring(0, idx).split("[^A-Za-z0-9_]+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (online.contains(tokens[i].toLowerCase())) {
                add(tokens[i], message);
                return;
            }
        }
    }

    private static void add(String name, String message) {
        String key = name.toLowerCase();
        ArrayDeque<Entry> q = LOGS.get(key);
        if (q == null) {
            q = new ArrayDeque<Entry>();
            LOGS.put(key, q);
        }
        synchronized (q) {
            q.addFirst(new Entry(System.currentTimeMillis(), message));
            while (q.size() > MAX) {
                q.removeLast();
            }
        }
    }

    private static Set<String> onlineNames() {
        Set<String> out = new HashSet<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                out.add(info.getGameProfile().getName().toLowerCase());
            }
        }
        if (mc.theWorld != null) {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                out.add(p.getName().toLowerCase());
            }
        }
        return out;
    }
}
