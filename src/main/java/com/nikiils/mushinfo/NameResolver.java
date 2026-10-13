package com.nikiils.mushinfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Acha o nick completo a partir de um pedaço ("Luiz" -> "luizgatinho771") entre os jogadores online. */
public final class NameResolver {

    private NameResolver() {
    }

    /**
     * Nicks online que combinam com o texto, do melhor para o pior: nome exato, começa com o texto,
     * contém o texto. Dentro de cada grupo, quem está mais perto vem primeiro, depois o nome mais curto.
     */
    public static List<String> matches(String query) {
        final String q = query.toLowerCase();
        Minecraft mc = Minecraft.getMinecraft();
        final Map<String, Double> distance = new HashMap<String, Double>();
        Map<String, String> names = new LinkedHashMap<String, String>();

        if (mc.theWorld != null && mc.thePlayer != null) {
            for (EntityPlayer p : new ArrayList<EntityPlayer>(mc.theWorld.playerEntities)) {
                String n = p.getName();
                if (valid(n)) {
                    names.put(n.toLowerCase(), n);
                    distance.put(n.toLowerCase(), (double) p.getDistanceToEntity(mc.thePlayer));
                }
            }
        }
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : new ArrayList<NetworkPlayerInfo>(mc.getNetHandler().getPlayerInfoMap())) {
                String n = info.getGameProfile().getName();
                if (valid(n) && !names.containsKey(n.toLowerCase())) {
                    names.put(n.toLowerCase(), n);
                }
            }
        }

        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (e.getKey().contains(q)) {
                out.add(e.getValue());
            }
        }
        Collections.sort(out, (a, b) -> {
            int ra = rank(a.toLowerCase(), q), rb = rank(b.toLowerCase(), q);
            if (ra != rb) {
                return ra - rb;
            }
            Double da = distance.get(a.toLowerCase()), db = distance.get(b.toLowerCase());
            if (da != null || db != null) {
                if (da == null) {
                    return 1;
                }
                if (db == null) {
                    return -1;
                }
                int c = Double.compare(da, db);
                if (c != 0) {
                    return c;
                }
            }
            if (a.length() != b.length()) {
                return a.length() - b.length();
            }
            return a.compareToIgnoreCase(b);
        });
        return out;
    }

    private static int rank(String name, String q) {
        return name.equals(q) ? 0 : name.startsWith(q) ? 1 : 2;
    }

    private static boolean valid(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }
}
