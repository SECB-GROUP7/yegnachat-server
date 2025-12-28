package com.yegnachat.server;

import com.yegnachat.server.http.HttpImageServer;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.load();

            String DB_URL = "jdbc:mysql://" + dotenv.get("DB_HOST")
                    + ":" + dotenv.get("DB_PORT") + "/" + dotenv.get("DB_NAME");

            String DB_USER = dotenv.get("DB_USER");
            String DB_PASS = dotenv.get("DB_PASS");

            Path imageRoot = Path.of(dotenv.get("DB_IMAGE_LOCATION"));
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter socket server port: ");
            int socketPort = scanner.nextInt();

            System.out.print("Enter HTTP image server port: ");
            int httpPort = scanner.nextInt();

            ChatServer chatServer = new ChatServer(socketPort, DB_URL, DB_USER, DB_PASS);
            HttpImageServer httpServer = new HttpImageServer(httpPort, imageRoot);

            // Start HTTP server
            httpServer.start();

            chatServer.startServer();

        } catch (Exception e) {
            System.err.println("Server failed to start");
            e.printStackTrace();
        }
    }
}
