package com.yegnachat.server;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.load();

            String DB_URL = "jdbc:mysql://" + dotenv.get("DB_HOST")
                    + ":" + dotenv.get("DB_PORT") + "/" + dotenv.get("DB_NAME");

            String DB_USER = dotenv.get("DB_USER");
            String DB_PASS = dotenv.get("DB_PASS");

            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter server port: ");
            int serverPort = scanner.nextInt();

            ChatServer server = new ChatServer(serverPort, DB_URL, DB_USER, DB_PASS);

            server.startServer();

        } catch (Exception e) {
            System.err.println("Server failed to start");
            e.printStackTrace();
        }
    }
}
