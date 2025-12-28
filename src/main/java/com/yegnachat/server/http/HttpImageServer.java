package com.yegnachat.server.http;

import com.sun.net.httpserver.HttpServer;
import com.yegnachat.server.image.ImageHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public class HttpImageServer {

    private final HttpServer server;

    public HttpImageServer(int port, Path imageRoot) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/uploads", new ImageHandler(imageRoot));

        server.setExecutor(null);
        System.out.println("HTTP Image Server started on port " + port);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }
}
