package com.mycompany.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class AuthClient implements Closeable {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private boolean greetingConsumed = false;

    public AuthClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public synchronized void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(5000);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        consumeGreeting(300);
    }

    public synchronized String consumeGreeting(int timeoutMs) throws IOException {
        if (reader == null) return null;
        int old = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeoutMs);
            String g = reader.readLine();  // sẽ ném SocketTimeoutException nếu không có gì
            greetingConsumed = true;
            return g;
        } catch (SocketTimeoutException ignore) {
            return null; // không có greeting
        } finally {
            socket.setSoTimeout(old);
        }
    }

    public synchronized String sendCommand(String line) throws IOException {
        if (socket == null || socket.isClosed()) connect();
        // đảm bảo greeting đã bị bỏ trước khi gửi lệnh
        if (!greetingConsumed) {
            try { consumeGreeting(1); } catch (IOException ignored) {}
        }
        writer.write(line);
        writer.newLine();
        writer.flush();
        String resp = reader.readLine();
        if (resp == null) throw new EOFException("Server closed connection.");
        return resp.trim();
    }

    @Override
    public synchronized void close() throws IOException {
        IOException ex = null;
        try { if (writer != null) writer.close(); } catch (IOException e) { ex = e; }
        try { if (reader != null) reader.close(); } catch (IOException e) { ex = e; }
        try { if (socket != null) socket.close(); } catch (IOException e) { ex = e; }
        if (ex != null) throw ex;
    }
}
