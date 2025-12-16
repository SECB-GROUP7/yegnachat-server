package com.yegnachat.server.auth;

import com.yegnachat.server.DatabaseService;
import com.yegnachat.server.util.PasswordUtil;

import java.sql.*;

public class AuthService {

    private final DatabaseService db;

    public AuthService(DatabaseService db) {
        this.db = db;
    }

    public SessionInfo login(String username, String password) throws SQLException {
        String sql = "SELECT id, password_hash FROM users WHERE username = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            if (!PasswordUtil.checkPassword(password, rs.getString("password_hash")))
                return null;

            return SessionManager.createSession(rs.getInt("id"));
        }
    }
}
