package com.yegnachat.server.chat;

import com.yegnachat.server.DatabaseService;
import com.yegnachat.server.user.User;
import com.yegnachat.server.user.UserService;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatService {

    private final DatabaseService db;

    public ChatService(DatabaseService db) {
        this.db = db;
    }

    public void savePrivateMessage(int senderId, int receiverId, String content) throws SQLException {
        String sql = """
                    INSERT INTO messages (sender_id, receiver_id, content)
                    VALUES (?, ?, ?)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, content);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> fetchPrivateHistory(
            int userA,
            int userB,
            UserService userService
    ) throws SQLException {

        String sql = """
        SELECT sender_id, content
        FROM messages
        WHERE (sender_id=? AND receiver_id=?)
           OR (sender_id=? AND receiver_id=?)
        ORDER BY created_at
    """;

        List<Map<String, Object>> messages = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userA);
            ps.setInt(2, userB);
            ps.setInt(3, userB);
            ps.setInt(4, userA);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                int senderId = rs.getInt("sender_id");
                User u = userService.getById(senderId);

                messages.add(Map.of(
                        "sender_id", senderId,
                        "sender_username", u != null ? u.getUsername() : "Unknown",
                        "avatar_url", u != null && u.getAvatarUrl() != null ? u.getAvatarUrl() : "",
                        "content", rs.getString("content")
                ));
            }
        }
        return messages;
    }


    public void saveGroupMessage(int senderId, int groupId, String content) throws SQLException {
        String sql = """
                    INSERT INTO group_messages (group_id, sender_id, content)
                    VALUES (?, ?, ?)
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ps.setInt(2, senderId);
            ps.setString(3, content);
            ps.executeUpdate();
        }
    }

    public List<GroupMessage> fetchGroupHistory(int groupId) throws SQLException {
        String sql = """
        SELECT id, group_id, sender_id, content, created_at
        FROM group_messages
        WHERE group_id = ?
        ORDER BY created_at
    """;

        List<GroupMessage> messages = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                messages.add(new GroupMessage(
                        rs.getInt("id"),
                        rs.getInt("group_id"),
                        rs.getInt("sender_id"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return messages;
    }



    public List<Integer> getGroupMembers(int groupId) throws SQLException {
        String sql = """
                    SELECT user_id FROM group_members WHERE group_id=?
                """;

        List<Integer> members = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(rs.getInt("user_id"));
            }
        }
        return members;
    }

    public int createGroup(String name, String about, String avatarUrl, int creatorId) throws SQLException {
        String sql = "INSERT INTO chat_groups (name, about, avatar_url, created_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, about);
            ps.setString(3, avatarUrl);
            ps.setInt(4, creatorId);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int groupId = keys.getInt(1);
                addUserToGroup(groupId, creatorId, "owner");
                return groupId;
            } else {
                throw new SQLException("Failed to retrieve group ID");
            }
        }
    }

    public void addUsersToGroup(int groupId, List<Integer> userIds) throws SQLException {
        String sql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?)";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int userId : userIds) {
                    ps.setInt(1, groupId);
                    ps.setInt(2, userId);
                    ps.setString(3, "member");
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void addUserToGroup(int groupId, int userId, String role) throws SQLException {
        String sql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    public boolean isUserInGroup(int groupId, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    // List groups a user belongs to
    public List<Map<String, Object>> listGroupsForUser(int userId) throws SQLException {
        String sql = """
                    SELECT g.id, g.name, g.about, g.avatar_url
                    FROM chat_groups g
                    JOIN group_members gm ON g.id = gm.group_id
                    WHERE gm.user_id = ?
                """;

        List<Map<String, Object>> groups = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                groups.add(Map.of(
                        "id", rs.getInt("id"),
                        "name", rs.getString("name"),
                        "about", rs.getString("about"),
                        "avatar_url", rs.getString("avatar_url")
                ));
            }
        }
        return groups;
    }


    // Leave group
    public boolean leaveGroup(int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id=? AND user_id=?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean isAdminInGroup(int groupId, int userId) throws SQLException {
        String sql = "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && (rs.getString("role").equalsIgnoreCase("admin")
                    || rs.getString("role").equalsIgnoreCase("owner"));
        }
    }

    public boolean removeUserFromGroup(int groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean isOwnerInGroup(int groupId, int userId) throws SQLException {
        String sql = "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getString("role").equalsIgnoreCase("owner");
        }
    }

    public boolean updateUserRole(int groupId, int userId, String newRole) throws SQLException {
        String sql = "UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setInt(2, groupId);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean updateGroupInfo(int groupId, String name, String about, String avatarUrl) throws SQLException {
        String sql = "UPDATE chat_groups SET name = ?, about = ?, avatar_url = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, about);
            ps.setString(3, avatarUrl);
            ps.setInt(4, groupId);
            return ps.executeUpdate() > 0;
        }
    }
    public List<Map<String, Object>> listGroupAdmins(int groupId) throws SQLException {
        String sql = "SELECT u.id, u.username, u.avatar_url, gm.role " +
                "FROM users u " +
                "JOIN group_members gm ON u.id = gm.user_id " +
                "WHERE gm.group_id = ? AND (gm.role='admin' OR gm.role='owner')";
        List<Map<String, Object>> admins = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                admins.add(Map.of(
                        "id", rs.getInt("id"),
                        "username", rs.getString("username"),
                        "avatar_url", rs.getString("avatar_url"),
                        "role", rs.getString("role")
                ));
            }
        }
        return admins;
    }

    public Map<String, Object> getGroupInfo(int groupId) throws SQLException {
        String sql = "SELECT id, name, about, avatar_url, created_by FROM chat_groups WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) return null; // group not found

            return Map.of(
                    "id", rs.getInt("id"),
                    "name", rs.getString("name"),
                    "about", rs.getString("about"),
                    "avatar_url", rs.getString("avatar_url"),
                    "created_by", rs.getInt("created_by")
            );
        }
    }



}
