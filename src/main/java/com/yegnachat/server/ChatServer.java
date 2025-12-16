package com.yegnachat.server;

import com.yegnachat.server.auth.AuthService;
import com.yegnachat.server.chat.ChatService;
import com.yegnachat.server.user.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    private final ServerSocket serverSocket;
    private final DatabaseService databaseService;
    private final MessageRouter router;

    public ChatServer(int port, String dbUrl, String dbUser, String dbPassword) throws IOException {

        databaseService = new DatabaseService(dbUrl, dbUser, dbPassword);

        AuthService authService = new AuthService(databaseService);
        ChatService chatService = new ChatService(databaseService);
        UserService userService = new UserService(databaseService);

        this.router = new MessageRouter(authService, chatService, userService);
        this.serverSocket = new ServerSocket(port);

        System.out.println("Server started on port " + port);
    }

    public void startServer() {
        System.out.println("Waiting for clients...");

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(socket, router);

                Thread thread = Thread.ofVirtual().unstarted(clientHandler);
                thread.start();

            } catch (IOException e) {
                System.out.println("Accept error: " + e.getMessage());
            }
        }
    }

    public void stopServer() {
        try {
            serverSocket.close();
            databaseService.close();
            System.out.println("Server stopped.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
