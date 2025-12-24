package com.yegnachat.server.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserDao {

    private final Connection conn;

    public UserDao(Connection conn) {
        this.conn = conn;
    }

    public boolean createUser(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, avatar_url, bio) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setString(1, user.getUsername());
        ps.setString(2, user.getPasswordHash());
        ps.setString(3, user.getAvatarUrl());
        ps.setString(4, user.getBio());

        return ps.executeUpdate() == 1;
    }

    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return extractUser(rs);
        }
        return null;
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

    public User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);

        ResultSet rs = ps.executeQuery();
        return rs.next() ? extractUser(rs) : null;
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

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public List<Map<String, Object>> listGroupsForUser(int userId) throws SQLException {
        List<Map<String, Object>> groups = new ArrayList<>();

        String sql = """
                    SELECT g.id, g.name, g.avatar_url, g.about
                    FROM chat_groups g
                    JOIN group_members gm ON g.id = gm.group_id
                    WHERE gm.user_id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
    public boolean updatePassword(int userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }
    public boolean updateBio(int userId, String bio) throws SQLException {
        String sql = "UPDATE users SET bio = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bio);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }


}
