package com.yegnachat.server.user;

import com.yegnachat.server.DatabaseService;
import com.yegnachat.server.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserService {

    private final DatabaseService db;

    public UserService(DatabaseService db) {
        this.db = db;
    }


    public User getById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? extractUser(rs) : null;
        }
    }

    public User getByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? extractUser(rs) : null;
        }
    }

    private User extractUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("avatar_url"),
                rs.getString("bio"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }


    public boolean createUser(String username, String password, String avatarUrl, String bio) throws SQLException {
        try (Connection conn = db.getConnection()) {

            // Check duplicate username
            String checkSql = "SELECT 1 FROM users WHERE username = ?";
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, username);
                if (check.executeQuery().next()) {
                    return false;
                }
            }

            String insertSql = """
                INSERT INTO users (username, password_hash, avatar_url, bio)
                VALUES (?, ?, ?, ?)
            """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, username);
                ps.setString(2, PasswordUtil.hashPassword(password));
                ps.setString(3, avatarUrl);
                ps.setString(4, bio);
                return ps.executeUpdate() == 1;
            }
        }
    }


    public boolean changePassword(int userId, String oldPassword, String newPassword) throws SQLException {
        try (Connection conn = db.getConnection()) {

            String fetchSql = "SELECT password_hash FROM users WHERE id = ?";
            String currentHash;

            try (PreparedStatement ps = conn.prepareStatement(fetchSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return false;
                currentHash = rs.getString("password_hash");
            }

            if (!PasswordUtil.checkPassword(oldPassword, currentHash)) {
                return false;
            }

            String updateSql = "UPDATE users SET password_hash = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, PasswordUtil.hashPassword(newPassword));
                ps.setInt(2, userId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    public boolean updateBio(int userId, String bio) throws SQLException {
        String sql = "UPDATE users SET bio = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, bio);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean updatePreferredLanguage(int userId, String languageCode) throws SQLException {
        String sql = "UPDATE users SET preferred_language_code = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, languageCode);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }


    public List<User> searchUsers(String query, int excludeUserId) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = """
            SELECT id, username, avatar_url, bio
            FROM users
            WHERE LOWER(username) LIKE ?
              AND id <> ?
            LIMIT 20
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + query.toLowerCase() + "%");
            ps.setInt(2, excludeUserId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setBio(rs.getString("bio"));
                users.add(u);
            }
        }
        return users;
    }

    public List<User> listUsersWithMessages(int userId) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = """
            SELECT DISTINCT u.*
            FROM users u
            JOIN messages m
              ON (m.sender_id = ? AND m.receiver_id = u.id)
              OR (m.receiver_id = ? AND m.sender_id = u.id)
            WHERE u.id != ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setAvatarUrl(rs.getString("avatar_url"));
                u.setBio(rs.getString("bio"));
                users.add(u);
            }
        }
        return users;
    }

    public List<Map<String, Object>> listGroupsForUser(int userId) throws SQLException {
        List<Map<String, Object>> groups = new ArrayList<>();
        String sql = """
            SELECT g.id, g.name, g.avatar_url, g.about
            FROM chat_groups g
            JOIN group_members gm ON g.id = gm.group_id
            WHERE gm.user_id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                groups.add(Map.of(
                        "id", rs.getInt("id"),
                        "name", rs.getString("name"),
                        "avatar_url", rs.getString("avatar_url") != null ? rs.getString("avatar_url") : "",
                        "about", rs.getString("about") != null ? rs.getString("about") : ""
                ));
            }
        }
        return groups;
    }


    public boolean followUser(int followerId, int targetId) throws SQLException {
        String sql = """
            INSERT INTO user_follows (follower_id, target_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE follower_id = follower_id
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, followerId);
            ps.setInt(2, targetId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean unfollowUser(int followerId, int targetId) throws SQLException {
        String sql = "DELETE FROM user_follows WHERE follower_id = ? AND target_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, followerId);
            ps.setInt(2, targetId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean updateAvatar(int userId, String avatarUrl) throws SQLException {
        String sql = "UPDATE users SET avatar_url = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

}
