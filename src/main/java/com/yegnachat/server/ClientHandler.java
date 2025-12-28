package com.yegnachat.server;

import com.yegnachat.server.auth.SessionInfo;
import com.yegnachat.server.util.LimitedInputStream;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final InputStream rawIn;
    private final MessageRouter router;


    private SessionInfo session;

    private static final Map<Integer, ClientHandler> ONLINE_USERS = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket, MessageRouter router) throws IOException {
        this.socket = socket;
        this.router = router;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.rawIn = socket.getInputStream();
    }

    public synchronized void setSession(SessionInfo newSession) {
        // Remove old session mapping if exists
        if (this.session != null) {
            ONLINE_USERS.remove(this.session.getUserId());
        }

        this.session = newSession;

        if (newSession != null) {
            ONLINE_USERS.put(newSession.getUserId(), this);
        }
    }

    // Add a helper to clear session (for logout)
    public synchronized void clearSession() {
        if (this.session != null) {
            ONLINE_USERS.remove(this.session.getUserId());
            this.session = null;
        }
    }

    public SessionInfo getSession() {
        return session;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String json = reader.readLine();
                if (json == null) break;

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

    protected void send(String json) {
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

    public InputStream readBinary(long size) throws IOException {
        return new LimitedInputStream(rawIn, size);
    }

    public void close() {
        try {
            if (session != null) {
                ONLINE_USERS.remove(session.getUserId());
            }
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
