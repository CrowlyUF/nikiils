package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.NameResolver;
import com.nikiils.mushinfo.Settings;
import com.nikiils.mushinfo.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Troca o GuiChat vanilla por um que intercepta mensagens começando com ".info", para que o
 * comando nunca seja enviado ao servidor.
 */
public class ChatInterceptor {

    private static final String PREFIX = "§8[§eMush§bInfo§8] §r";

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui != null && event.gui.getClass() == GuiChat.class) {
            String text = "";
            try {
                text = ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) event.gui,
                        "defaultInputFieldText", "field_146409_v");
            } catch (Exception ignored) {
                // mantém o texto vazio
            }
            event.gui = new InfoGuiChat(text);
        }
    }

    /** Retorna true se a mensagem foi tratada como comando do mod. */
    static boolean handle(String message) {
        String msg = message.trim();
        if (msg.equalsIgnoreCase(".bind") || msg.toLowerCase().startsWith(".bind ")) {
            bind(msg.split("\\s+"));
            return true;
        }
        if (!msg.equalsIgnoreCase(".info") && !msg.toLowerCase().startsWith(".info ")) {
            return false;
        }
        String[] args = msg.split("\\s+");
        if (args.length < 2) {
            Collection<String> names = Tracker.names();
            send("§7Aperte §f" + MushGui.boundKey() + "§7 para abrir a GUI. §f.info <nick>§7 abre direto no jogador.");
            send("§f.bind clickgui <tecla>§7 troca a tecla de abrir a GUI.");
            send("§f.info clear§7 remove todos os cards, §f.info blur§7 liga/desliga o blur.");
            send("§7Cards ativos: §f" + (names.isEmpty() ? "nenhum" : String.join(", ", names)));
            return true;
        }
        String query = args[1];
        if (query.equalsIgnoreCase("clear")) {
            Tracker.clear();
            send("\u00a7eTodos os cards removidos.");
            return true;
        }
        if (query.equalsIgnoreCase("blur")) {
            Settings.blur = !Settings.blur;
            Settings.save();
            send(Settings.blur ? "\u00a7aBlur ligado." : "\u00a7eBlur desligado (mais FPS).");
            return true;
        }
        if (!query.matches("[A-Za-z0-9_]{1,16}")) {
            send("\u00a7cNick inválido: " + query);
            return true;
        }
        MushGui.requestOpen(resolve(query));
        return true;
    }

    /**
     * Completa o nick: um jogador já monitorado que combine tem prioridade (para remover com o mesmo
     * pedaço de nome), depois os jogadores online. Sem nenhum online, usa o texto como digitado.
     */
    private static String resolve(String query) {
        String q = query.toLowerCase();
        for (String tracked : Tracker.names()) {
            if (tracked.equalsIgnoreCase(query)) {
                return tracked;
            }
        }
        List<String> online = NameResolver.matches(query);
        if (!online.isEmpty() && online.get(0).equalsIgnoreCase(query)) {
            return online.get(0);
        }
        for (String tracked : Tracker.names()) {
            if (tracked.toLowerCase().startsWith(q)) {
                return tracked;
            }
        }
        if (online.isEmpty()) {
            send("\u00a77Ninguém online com \u00a7f" + query + "\u00a77, buscando esse nick exato.");
            return query;
        }
        if (online.size() > 1) {
            List<String> others = online.subList(1, Math.min(online.size(), 6));
            send("\u00a77Também combinam: \u00a7f" + String.join("\u00a77, \u00a7f", others)
                    + (online.size() > 6 ? "\u00a77..." : "") + " \u00a78(use TAB para escolher)");
        }
        return online.get(0);
    }

    /** .bind clickgui <tecla> */
    private static void bind(String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("clickgui")) {
            send("§7Uso: §f.bind clickgui <tecla>§7 (ex.: §f.bind clickgui j§7). Atual: §f" + MushGui.boundKey());
            return;
        }
        if (MushGui.bind(args[2])) {
            send("§aClickGUI agora abre com a tecla §f" + MushGui.boundKey() + "§a.");
        } else {
            send("§cTecla desconhecida: §f" + args[2] + "§7 (use letras, números ou nomes como RSHIFT, F6, INSERT).");
        }
    }

    private static void send(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + text));
        }
    }

    static final class InfoGuiChat extends GuiChat {
        private List<String> completions;
        private int completionIndex;

        InfoGuiChat(String defaultText) {
            super(defaultText);
        }

        /** TAB depois de ".info " completa com os nicks online, cicla a cada TAB. */
        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            String text = inputField.getText();
            if (keyCode == Keyboard.KEY_TAB && text.toLowerCase().startsWith(".info ")) {
                if (completions == null) {
                    completions = NameResolver.matches(text.substring(6).trim());
                    completionIndex = 0;
                } else {
                    completionIndex++;
                }
                if (!completions.isEmpty()) {
                    inputField.setText(".info " + completions.get(completionIndex % completions.size()));
                }
                return;
            }
            completions = null;
            super.keyTyped(typedChar, keyCode);
        }

        @Override
        public void sendChatMessage(String msg, boolean addToChat) {
            if (handle(msg)) {
                mc.ingameGUI.getChatGUI().addToSentMessages(msg);
                return;
            }
            super.sendChatMessage(msg, addToChat);
        }
    }
}
