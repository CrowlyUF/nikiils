package com.nikiils.mushinfo.api;

public final class PlayerData {

    public volatile boolean fetching;
    public long fetchedAt;
    public String error;

    public String username;
    public String rank;
    /** Cor da tag do rank (RGB), ex.: 0xAA00AA para Mod. */
    public int rankColor = 0xAAAAAA;
    public boolean online;
    public boolean banned;
    public int banBlacklistCount;
    public int banCount;
    public boolean muted;
    public int muteCount;

    static PlayerData loading() {
        PlayerData d = new PlayerData();
        d.fetching = true;
        return d;
    }

    static PlayerData error(String message) {
        PlayerData d = new PlayerData();
        d.error = message;
        d.fetchedAt = System.currentTimeMillis();
        return d;
    }

    public boolean isLoading() {
        return fetching && fetchedAt == 0;
    }
}
