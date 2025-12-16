package com.yegnachat.server.user;

import com.yegnachat.server.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    public List<User> listAllExcept(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id <> ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();
        List<User> users = new ArrayList<>();

        while (rs.next()) {
            users.add(extractUser(rs));
        }
        return users;
    }

}
