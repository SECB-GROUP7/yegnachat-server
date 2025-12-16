package com.yegnachat.server.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public static SessionInfo createSession(int userId) {
        String token = UUID.randomUUID().toString();
        SessionInfo info = new SessionInfo(userId, token);
        sessions.put(token, info);
        return info;
    }

    public static SessionInfo get(String token) {
        return sessions.get(token);
    }
}
