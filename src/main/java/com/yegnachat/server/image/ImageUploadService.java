package com.yegnachat.server.image;

import com.yegnachat.server.DatabaseService;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class ImageUploadService {
    private static Dotenv dotenv = Dotenv.load();
    private static Path rootDir = Path.of(dotenv.get("DB_IMAGE_LOCATION"));
    private static DatabaseService db;

    public static void setDb(DatabaseService db) {
        ImageUploadService.db = db;
    }

    public static String uploadImage(String purpose, int ownerId, InputStream fileStream, String mime) throws SQLException, IOException {
        String ext = mimeToExt(mime);
        Path saveDir = rootDir.resolve(purpose);
        Files.createDirectories(saveDir);

        String filename = purpose + "_" + ownerId + "_" + System.currentTimeMillis() + ext;
        Path savePath = saveDir.resolve(filename);

        try (OutputStream os = Files.newOutputStream(savePath)) {
            fileStream.transferTo(os);
        }

        String imageUrl = "/uploads/" + purpose + "/" + filename;

        // Update database
        try (Connection conn = db.getConnection()) {
            String sql = switch (purpose) {
                case "avatar" -> "UPDATE users SET avatar_url=? WHERE id=?";
                case "group_avatar" -> "UPDATE chat_groups SET avatar_url=? WHERE id=?";
                default -> throw new IllegalArgumentException("Unknown purpose: " + purpose);
            };
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, imageUrl);
                ps.setInt(2, ownerId);
                ps.executeUpdate();
            }
        }

        return imageUrl;
    }

    public static String uploadPostImage(long postId, InputStream fileStream, String mime) throws IOException {
        String ext = mimeToExt(mime);
        Path saveDir = rootDir.resolve("posts");
        Files.createDirectories(saveDir);

        String filename = "post_" + postId + "_" + System.currentTimeMillis() + ext;
        Path savePath = saveDir.resolve(filename);

        try (OutputStream os = Files.newOutputStream(savePath)) {
            fileStream.transferTo(os);
        }

        return "/uploads/posts/" + filename;
    }


    private static String mimeToExt(String mime) {
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}

