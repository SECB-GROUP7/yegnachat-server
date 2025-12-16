package com.yegnachat.server.auth;

public class SessionInfo {
    private final int userId;
    private final String token;

    public SessionInfo(int userId, String token) {
        this.userId = userId;
        this.token = token;
    }

    public int getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
