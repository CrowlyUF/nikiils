package com.nikiils.mushinfo;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Opções do mod, salvas em config/mushinfo.properties. */
public final class Settings {

    /** Fundo borrado na GUI e nos cards (desligar ganha FPS). */
    public static boolean blur = true;
    /** Mensagem no chat quando alguém dispara um check. */
    public static boolean chatAlerts = true;
    /** Avisar também checks experimentais (mais sujeitos a falso positivo). */
    public static boolean experimentalAlerts = false;

    private Settings() {
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "mushinfo.properties");
    }

    public static void load() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file())) {
            p.load(in);
        } catch (Exception ignored) {
            return; // primeira vez: usa os padrões
        }
        blur = Boolean.parseBoolean(p.getProperty("blur", "true"));
        chatAlerts = Boolean.parseBoolean(p.getProperty("chatAlerts", "true"));
        experimentalAlerts = Boolean.parseBoolean(p.getProperty("experimentalAlerts", "false"));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("blur", String.valueOf(blur));
        p.setProperty("chatAlerts", String.valueOf(chatAlerts));
        p.setProperty("experimentalAlerts", String.valueOf(experimentalAlerts));
        File f = file();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "MushInfo");
        } catch (Exception ignored) {
            // sem permissão de escrita: as opções valem só até fechar o jogo
        }
    }
}
