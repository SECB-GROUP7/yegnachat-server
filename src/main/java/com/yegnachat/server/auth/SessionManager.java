package com.yegnachat.server.auth;

import com.yegnachat.server.DatabaseService;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private static DatabaseService db;

    public static void init(DatabaseService databaseService) {
        db = databaseService;
    }

    public static SessionInfo createSession(int userId, String preferredLanguageCode) throws SQLException {
        String token = UUID.randomUUID().toString();
        SessionInfo info = new SessionInfo(userId, token, preferredLanguageCode);

        // Save to memory
        sessions.put(token, info);

        // Persist to database
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sessions (user_id, token, expires_at) VALUES (?, ?, ?)"
             )) {
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setTimestamp(3, Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS))); // 7-day expiry
            ps.executeUpdate();
        }

        return info;
    }

    public static SessionInfo get(String token) throws SQLException {
        // Check in memory first
        SessionInfo info = sessions.get(token);
        if (info != null) return info;

        // Load from DB if exists and not expired
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT s.user_id, s.token, u.preferred_language_code " +
                             "FROM sessions s JOIN users u ON s.user_id = u.id " +
                             "WHERE s.token = ? AND (s.expires_at IS NULL OR s.expires_at > NOW())"
             )) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                info = new SessionInfo(
                        rs.getInt("user_id"),
                        rs.getString("token"),
                        rs.getString("preferred_language_code")
                );
                // Cache in memory
                sessions.put(token, info);
                return info;
            }
        }
        return null;
    }

    public static void invalidate(String token) throws SQLException {
        sessions.remove(token);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }
}
