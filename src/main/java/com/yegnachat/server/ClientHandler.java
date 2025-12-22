package com.yegnachat.server;

import com.yegnachat.server.auth.SessionInfo;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final MessageRouter router;

    private SessionInfo session;

    private static final Map<Integer, ClientHandler> ONLINE_USERS = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket, MessageRouter router) throws IOException {
        this.socket = socket;
        this.router = router;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public void setSession(SessionInfo session) {
        this.session = session;
        if (session != null) {
            ONLINE_USERS.put(session.getUserId(), this);
        }
    }

    public SessionInfo getSession() {
        return session;
    }

    @Override
    public void run() {
        try {
            String json;
            while ((json = reader.readLine()) != null) {
                String response = router.route(json, this);
                if (response != null) {
                    send(response);
                }
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    private void send(String json) {
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            close();
        }
    }

    // For online user only
    public static boolean sendToUser(int userId, String json) {
        ClientHandler client = ONLINE_USERS.get(userId);
        if (client != null) {
            client.send(json);
            return true;
        }
        return false;
    }

    // For online users only
    public static void sendToUsers(List<Integer> userIds, String json, int senderId) {
        for (int id : userIds) {
            if (id != senderId) {
                sendToUser(id, json);
            }
        }
    }

    public static void broadcastRaw(String json) {
        for (ClientHandler client : ONLINE_USERS.values()) {
            client.send(json);
        }
    }

    private void close() {
        try {
            if (session != null) {
                ONLINE_USERS.remove(session.getUserId());
            }
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
