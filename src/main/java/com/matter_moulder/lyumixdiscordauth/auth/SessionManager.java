package com.matter_moulder.lyumixdiscordauth.auth;

import com.matter_moulder.lyumixdiscordauth.models.PlayerRestoredInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final ConcurrentHashMap<UUID, PlayerRestoredInfo> sessions
            = new ConcurrentHashMap<>();

    public static void put(UUID uuid, PlayerRestoredInfo info) {
        sessions.put(uuid, info);
    }

    public static PlayerRestoredInfo get(UUID uuid) {
        return sessions.get(uuid);
    }

    public static void remove(UUID uuid) {
        PlayerRestoredInfo info = sessions.remove(uuid);
        if (info != null) {
            info.delete();
        }
    }

    public static boolean contains(UUID uuid) {
        return sessions.containsKey(uuid);
    }
}
