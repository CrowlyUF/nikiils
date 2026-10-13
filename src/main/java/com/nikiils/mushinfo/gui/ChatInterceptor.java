package com.nikiils.mushinfo.gui;

import com.nikiils.mushinfo.Tracker;
import com.nikiils.mushinfo.api.MushApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.Collection;

/**
 * Troca o GuiChat vanilla por um que intercepta mensagens começando com ".info", para que o
 * comando nunca seja enviado ao servidor.
 */
public class ChatInterceptor {

    private static final String PREFIX = "§8[§bMushInfo§8] §r";

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
        if (!msg.equalsIgnoreCase(".info") && !msg.toLowerCase().startsWith(".info ")) {
            return false;
        }
        String[] args = msg.split("\\s+");
        if (args.length < 2) {
            Collection<String> names = Tracker.names();
            send("§7Uso: §f.info <nick>§7 (de novo para remover), §f.info clear");
            send("§7Monitorando: §f" + (names.isEmpty() ? "ninguém" : String.join(", ", names)));
            return true;
        }
        String name = args[1];
        if (name.equalsIgnoreCase("clear")) {
            Tracker.clear();
            send("§eLista de monitorados limpa.");
            return true;
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            send("§cNick inválido: " + name);
            return true;
        }
        if (Tracker.toggle(name)) {
            MushApi.invalidate(name);
            MushApi.get(name);
            send("§aMostrando informações de §f" + name + "§a.");
        } else {
            send("§eParou de mostrar §f" + name + "§e.");
        }
        return true;
    }

    private static void send(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + text));
        }
    }

    static final class InfoGuiChat extends GuiChat {
        InfoGuiChat(String defaultText) {
            super(defaultText);
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
