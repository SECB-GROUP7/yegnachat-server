package com.yegnachat.server.image;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImageHandler implements HttpHandler {

    private final Path rootDir;
    public ImageHandler(Path rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            String uriPath = exchange.getRequestURI().getPath();
            // /uploads/avatars/u_12.png
            String relativePath = uriPath.replaceFirst("/uploads/?", "");

            Path filePath = rootDir.resolve(relativePath).normalize();


            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, Files.size(filePath));

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(filePath, os);
            }
        } else if ("POST".equalsIgnoreCase(method)) {

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String purpose = exchange.getRequestHeaders().getFirst("X-Purpose");
            int ownerId = Integer.parseInt(exchange.getRequestHeaders().getFirst("X-Owner-Id"));

            try {
                String imageUrl = ImageUploadService.uploadImage(purpose, ownerId, exchange.getRequestBody(), contentType);
                String responseJson = "{\"status\":\"ok\",\"image_url\":\"" + imageUrl + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseJson.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseJson.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }


        }

    }

    private String mimeToExt(String mime) {
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
