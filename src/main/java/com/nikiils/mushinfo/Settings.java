package com.nikiils.mushinfo;

import com.nikiils.mushinfo.check.Check;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/** Opções do mod, salvas em config/mushinfo.properties. */
public final class Settings {

    /** Fundo borrado na GUI e nos cards (desligar ganha FPS). */
    public static boolean blur = true;
    /** Mensagem no chat quando alguém dispara um check. */
    public static boolean chatAlerts = true;
    /** Avisar também checks experimentais (mais sujeitos a falso positivo). */
    public static boolean experimentalAlerts = false;
    /** Checks desligados na aba Checks (não analisam nem avisam). */
    private static final Set<Check> DISABLED = EnumSet.noneOf(Check.class);

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
        DISABLED.clear();
        for (String name : p.getProperty("disabledChecks", "").split(",")) {
            try {
                DISABLED.add(Check.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // nome vazio ou check que não existe mais
            }
        }
    }

    public static boolean isEnabled(Check check) {
        return !DISABLED.contains(check);
    }

    public static void setEnabled(Check check, boolean enabled) {
        if (enabled) {
            DISABLED.remove(check);
        } else {
            DISABLED.add(check);
        }
        save();
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("blur", String.valueOf(blur));
        p.setProperty("chatAlerts", String.valueOf(chatAlerts));
        p.setProperty("experimentalAlerts", String.valueOf(experimentalAlerts));
        StringBuilder off = new StringBuilder();
        for (Check c : DISABLED) {
            off.append(off.length() == 0 ? "" : ",").append(c.name());
        }
        p.setProperty("disabledChecks", off.toString());
        File f = file();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "MushInfo");
        } catch (Exception ignored) {
            // sem permissão de escrita: as opções valem só até fechar o jogo
        }
    }
}
