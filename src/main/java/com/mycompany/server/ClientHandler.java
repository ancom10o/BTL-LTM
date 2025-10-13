package com.mycompany.server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

public class ClientHandler extends Thread {
    private final Socket socket;
    private String currentUser = null;

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out   = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("Connected to server successfully!");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(";", 3);
                String cmd = parts[0].trim().toUpperCase();

                switch (cmd) {
                    case "REGISTER" -> handleRegister(parts, out);
                    case "LOGIN"    -> handleLogin(parts, out);
                    case "WHO"      -> handleWho(out);

                    // --- NEW: thách đấu ---
                    case "INVITE"   -> handleInvite(parts, out);
                    case "RESPOND"  -> handleRespond(parts, out);
                    case "POLL"     -> handlePoll(out);

                    case "LOGOUT"   -> { handleLogout(out); return; }
                    case "PING"     -> out.println("PONG");
                    case "EXIT"     -> { out.println("BYE"); return; }
                    default         -> out.println("ERROR;Unknown command");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (currentUser != null) {
                Server.ONLINE_USERS.remove(currentUser);
                currentUser = null;
            }
        }
    }

    private void handleRegister(String[] parts, PrintWriter out) throws SQLException {
        if (parts.length < 3) { out.println("ERROR;Syntax: REGISTER;username;password"); return; }
        String u = parts[1].trim(), p = parts[2].trim();
        if (u.isEmpty() || p.isEmpty()) { out.println("ERROR;Username or password cannot be empty"); return; }
        if (Database.userExists(u))     { out.println("ERROR;Username already exists"); return; }
        Database.registerUser(u, p);
        out.println("REGISTER_OK");
    }

    private void handleLogin(String[] parts, PrintWriter out) throws SQLException {
        if (parts.length < 3) { out.println("ERROR;Syntax: LOGIN;username;password"); return; }
        String u = parts[1].trim(), p = parts[2].trim();
        if (Database.checkLogin(u, p)) {
            currentUser = u;
            Server.ONLINE_USERS.add(currentUser);
            Server.box(currentUser); // tạo mailbox
            out.println("LOGIN_OK");
        } else {
            out.println("ERROR;Invalid username or password");
        }
    }

    private void handleWho(PrintWriter out) {
        String joined = String.join(",", Server.ONLINE_USERS);
        out.println("ONLINE;" + joined);
    }

    // ======= CHALLENGE HANDLERS =======

    // INVITE;toUser  -> push sự kiện cho người nhận: INVITE_FROM;fromUser
    private void handleInvite(String[] parts, PrintWriter out) {
        if (currentUser == null) { out.println("ERROR;Unauthenticated"); return; }
        if (parts.length < 2)    { out.println("ERROR;Syntax: INVITE;toUser"); return; }
        String to = parts[1].trim();
        if (to.isEmpty()) { out.println("ERROR;Missing recipient"); return; }
        if (!Server.ONLINE_USERS.contains(to)) { out.println("ERROR;User not online"); return; }
        if (to.equals(currentUser)) { out.println("ERROR;Cannot invite yourself"); return; }

        // đẩy sự kiện đến người nhận
        Server.box(to).add("INVITE_FROM;" + currentUser);
        out.println("INVITE_SENT");
    }

    // RESPOND;opponent;ACCEPT|REJECT  -> push kết quả cho người mời: INVITE_RESULT;responder;ACCEPT|REJECT
    private void handleRespond(String[] parts, PrintWriter out) {
        if (currentUser == null) { out.println("ERROR;Unauthenticated"); return; }
        if (parts.length < 3)    { out.println("ERROR;Syntax: RESPOND;opponent;ACCEPT|REJECT"); return; }
        String opponent = parts[1].trim();
        String decision = parts[2].trim().toUpperCase();
        if (!decision.equals("ACCEPT") && !decision.equals("REJECT")) {
            out.println("ERROR;Decision must be ACCEPT or REJECT"); return;
        }
        // gửi kết quả cho người mời
        Server.box(opponent).add("INVITE_RESULT;" + currentUser + ";" + decision);
        out.println("RESPOND_OK");
        // Option: nếu ACCEPT, có thể đẩy thêm START_MATCH cho cả hai ở đây
        if (decision.equals("ACCEPT")) {
            Server.box(opponent).add("START_MATCH;" + currentUser);
            Server.box(currentUser).add("START_MATCH;" + opponent);
        }
    }

    // POLL -> trả về 1 sự kiện hoặc NO_EVENT
    private void handlePoll(PrintWriter out) {
        if (currentUser == null) { out.println("ERROR;Unauthenticated"); return; }
        var q = Server.box(currentUser);
        String ev = q.poll();
        if (ev == null) out.println("NO_EVENT");
        else            out.println(ev); // ví dụ: INVITE_FROM;userA  / INVITE_RESULT;userB;ACCEPT  / START_MATCH;userB
    }

    private void handleLogout(PrintWriter out) {
        if (currentUser != null) {
            Server.ONLINE_USERS.remove(currentUser);
            currentUser = null;
        }
        out.println("LOGOUT_OK");
    }
}
