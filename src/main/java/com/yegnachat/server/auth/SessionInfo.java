package com.yegnachat.server.auth;

public class SessionInfo {
    private final int userId;
    private final String token;
    private String preferredLanguageCode;

    public SessionInfo(int userId, String token, String preferredLanguageCode) {
        this.userId = userId;
        this.token = token;
        this.preferredLanguageCode = preferredLanguageCode;
    }

    public int getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getPreferredLanguageCode() {
        return preferredLanguageCode;
    }

    public void setPreferredLanguageCode(String preferredLanguageCode) {
        this.preferredLanguageCode = preferredLanguageCode;
    }
}
