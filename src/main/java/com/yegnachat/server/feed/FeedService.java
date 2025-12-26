package com.yegnachat.server.feed;

import com.yegnachat.server.DatabaseService;
import java.sql.*;
import java.util.*;

public class FeedService {

    private final DatabaseService db;

    public FeedService(DatabaseService db) {
        this.db = db;
    }


    public long createPost(int userId, String content, String imageUrl) throws SQLException {
        String sql = """
            INSERT INTO posts (user_id, content, image_url)
            VALUES (?, ?, ?)
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, userId);
            ps.setString(2, content);
            ps.setString(3, imageUrl);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<Map<String, Object>> listFeedPosts(int limit, int offset) throws SQLException {
        String sql = """
            SELECT p.id, p.content, p.image_url, p.created_at,
                   u.id AS user_id, u.username, u.avatar_url,
                   (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS like_count,
                   (SELECT COUNT(*) FROM post_comments pc WHERE pc.post_id = p.id) AS comment_count
            FROM posts p
            JOIN users u ON u.id = p.user_id
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
        """;

        List<Map<String, Object>> posts = new ArrayList<>();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                posts.add(Map.of(
                        "post_id", rs.getLong("id"),
                        "content", rs.getString("content"),
                        "image_url", rs.getString("image_url"),
                        "created_at", rs.getTimestamp("created_at").toString(),
                        "user", Map.of(
                                "id", rs.getInt("user_id"),
                                "username", rs.getString("username"),
                                "avatar_url", rs.getString("avatar_url")
                        ),
                        "likes", rs.getInt("like_count"),
                        "comments", rs.getInt("comment_count")
                ));
            }
        }
        return posts;
    }

    public boolean likePost(int userId, long postId) throws SQLException {
        String sql = """
            INSERT INTO post_likes (post_id, user_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE liked_at = CURRENT_TIMESTAMP
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean unlikePost(int userId, long postId) throws SQLException {
        String sql = "DELETE FROM post_likes WHERE post_id = ? AND user_id = ?";

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /* ================= COMMENTS ================= */

    public long addComment(int userId, long postId, String content) throws SQLException {
        String sql = """
            INSERT INTO post_comments (post_id, user_id, content)
            VALUES (?, ?, ?)
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, postId);
            ps.setInt(2, userId);
            ps.setString(3, content);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<Map<String, Object>> listComments(long postId) throws SQLException {
        String sql = """
            SELECT c.id, c.content, c.created_at,
                   u.id AS user_id, u.username, u.avatar_url
            FROM post_comments c
            JOIN users u ON u.id = c.user_id
            WHERE c.post_id = ?
            ORDER BY c.created_at ASC
        """;

        List<Map<String, Object>> comments = new ArrayList<>();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                comments.add(Map.of(
                        "comment_id", rs.getLong("id"),
                        "content", rs.getString("content"),
                        "created_at", rs.getTimestamp("created_at").toString(),
                        "user", Map.of(
                                "id", rs.getInt("user_id"),
                                "username", rs.getString("username"),
                                "avatar_url", rs.getString("avatar_url")
                        )
                ));
            }
        }
        return comments;
    }
}
