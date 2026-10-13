package com.nikiils.mushinfo.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consulta https://mush.com.br/api/player/<nick> em segundo plano e guarda o resultado em cache.
 *
 * A API retorna "banned": true e "ban_blacklist_count" quando o jogador está banido; os campos são
 * omitidos quando não há punição. Campos de mute são lidos de qualquer chave contendo "mute".
 */
public final class MushApi {

    private static final String URL_BASE = "https://mush.com.br/api/player/";
    private static final long REFRESH_MS = 60_000L;
    private static final long RETRY_MS = 15_000L;

    private static final Map<String, PlayerData> CACHE = new ConcurrentHashMap<String, PlayerData>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "MushInfo-API");
        t.setDaemon(true);
        return t;
    });

    private MushApi() {
    }

    /** Retorna os dados em cache (pode ser "carregando") e agenda atualização quando vencidos. */
    public static PlayerData get(String name) {
        String key = name.toLowerCase();
        PlayerData data = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (data == null) {
            data = PlayerData.loading();
            CACHE.put(key, data);
            fetch(name, key);
        } else if (!data.fetching && now - data.fetchedAt > (data.error != null ? RETRY_MS : REFRESH_MS)) {
            data.fetching = true;
            fetch(name, key);
        }
        return data;
    }

    public static void invalidate(String name) {
        CACHE.remove(name.toLowerCase());
    }

    private static void fetch(final String name, final String key) {
        POOL.submit(() -> CACHE.put(key, request(name)));
    }

    private static PlayerData request(String name) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_BASE + URLEncoder.encode(name, "UTF-8")).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (MushInfo mod 1.8.9)");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return PlayerData.error("HTTP " + code);
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
                root = new JsonParser().parse(reader).getAsJsonObject();
            }
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                return PlayerData.error("Não encontrado");
            }
            return parse(root.getAsJsonObject("response"));
        } catch (Exception e) {
            return PlayerData.error("Erro na API");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static PlayerData parse(JsonObject r) {
        PlayerData d = new PlayerData();
        d.fetchedAt = System.currentTimeMillis();
        d.banned = bool(r, "banned");
        d.banBlacklistCount = integer(r, "ban_blacklist_count");

        // Qualquer outro campo de ban/mute que a API venha a expor.
        for (Map.Entry<String, JsonElement> e : r.entrySet()) {
            String k = e.getKey().toLowerCase();
            JsonElement v = e.getValue();
            if (k.contains("mute") || k.contains("silenc")) {
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    d.muted |= v.getAsBoolean();
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    d.muteCount = Math.max(d.muteCount, v.getAsInt());
                } else if (v.isJsonObject() || v.isJsonArray()) {
                    d.muted = true;
                }
            } else if (k.contains("ban") && !k.equals("banned") && !k.equals("ban_blacklist_count")) {
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    d.banCount = Math.max(d.banCount, v.getAsInt());
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    d.banned |= v.getAsBoolean();
                }
            }
        }

        JsonObject account = r.has("account") && r.get("account").isJsonObject() ? r.getAsJsonObject("account") : null;
        if (account != null && account.has("username")) {
            d.username = account.get("username").getAsString();
        }
        JsonObject tag = r.has("rank_tag") && r.get("rank_tag").isJsonObject() ? r.getAsJsonObject("rank_tag") : null;
        if (tag != null && tag.has("name")) {
            d.rank = tag.get("name").getAsString();
            if (tag.has("color")) {
                try {
                    d.rankColor = Integer.parseInt(tag.get("color").getAsString().replace("#", ""), 16);
                } catch (NumberFormatException ignored) {
                    // mantém a cor padrão
                }
            }
        }
        d.online = bool(r, "connected");
        return d;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
    }
}
