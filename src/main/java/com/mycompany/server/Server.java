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
        System.out.println("Starting server on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("Client connected from " + socket.getInetAddress());
                    new ClientHandler(socket).start();
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                    e.printStackTrace();
                    // Continue accepting other connections
                }
            }
        } catch (java.net.BindException e) {
            System.err.println("ERROR: Port " + port + " is already in use!");
            System.err.println("Please close the existing server instance or use a different port.");
            System.err.println("You can find the process using: netstat -ano | findstr :9090");
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
