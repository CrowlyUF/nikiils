package com.nikiils.mushinfo.check;

import com.nikiils.mushinfo.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

import java.util.HashMap;
import java.util.Map;

/** Avisos no chat quando um check dispara. Clicar no aviso abre a GUI no jogador. */
final class Alerts {

    /** Um aviso por jogador+check a cada 15 s, para não inundar o chat. */
    private static final long COOLDOWN_MS = 15_000L;
    private static final Map<String, Long> LAST = new HashMap<String, Long>();

    private Alerts() {
    }

    static void onFlag(PlayerStats s, Flag f, int count) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Settings.chatAlerts || mc.thePlayer == null || (f.check.experimental && !Settings.experimentalAlerts)
                || s.name.equalsIgnoreCase(mc.thePlayer.getName())) {
            return;
        }
        String key = s.name.toLowerCase() + ":" + f.check;
        Long last = LAST.get(key);
        if (last != null && f.time - last < COOLDOWN_MS) {
            return;
        }
        LAST.put(key, f.time);
        ChatComponentText text = new ChatComponentText("§8[§eMush§bInfo§8] §f" + s.name
                + " §7» §c" + f.check.displayName + " §7x" + count + " §8(" + f.reason + ")");
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ".info " + s.name));
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ChatComponentText("§7Clique para abrir §f" + s.name + "§7 no MushInfo")));
        text.setChatStyle(style);
        mc.thePlayer.addChatMessage(text);
    }
}
