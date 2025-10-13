package com.mycompany.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;

public class Server {
    public static final Set<String> ONLINE_USERS = new ConcurrentSkipListSet<>();

    // Hộp thư sự kiện cho từng user
    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> MAILBOX = new ConcurrentHashMap<>();
    public static ConcurrentLinkedQueue<String> box(String user) {
        return MAILBOX.computeIfAbsent(user, k -> new ConcurrentLinkedQueue<>());
    }

    public static void main(String[] args) {
        DbPool.get();
        Database.debugInfo();

        int port = 9090;
        System.out.println("Server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connect " + socket.getInetAddress());
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
