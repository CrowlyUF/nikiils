package com.alan.clients.component.impl.player;

import com.alan.clients.component.Component;

import java.util.HashMap;

/**
 * The original resolved IRC nicknames/capes through the Rise backend, which no longer exists.
 * Kept as a no-op so name formatting call sites stay untouched.
 */
public class IRCInfoComponent extends Component {
    public static final HashMap<String, UserInfo> usersMap = new HashMap<>();

    public static String formatNick(String message, String name) {
        return message;
    }

    public static void checkList() {
    }
}
