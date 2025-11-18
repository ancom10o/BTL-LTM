package com.mycompany.server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

public class ClientHandler extends Thread {
    private final Socket socket;
    private String currentUser = null;

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("Connected to server successfully!");
            out.flush();

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(";", 3);
                    String cmd = parts[0].trim().toUpperCase();

                    // Only log non-polling commands to reduce noise
                    if (!cmd.equals("POLL") && !cmd.equals("WHO")) {
                    }

                    try {
                        switch (cmd) {
                        case "REGISTER" -> {
                            try {
                                handleRegister(parts, out);
                            } catch (SQLException e) {
                                System.err.println("[REGISTER] SQLException: " + e.getMessage());
                                e.printStackTrace();
                                out.println("ERROR;Database error during registration");
                                out.flush();
                            }
                        }
                        case "LOGIN"    -> handleLogin(parts, out);
                        case "WHO"      -> handleWho(out);

                            // --- NEW: thách đấu ---
                            case "INVITE"   -> handleInvite(parts, out);
                            case "RESPOND"  -> handleRespond(parts, out);
                        case "POLL"     -> handlePoll(out);
                        case "GET_HISTORY" -> handleGetHistory(parts, out);
                        case "GET_LEADERBOARD" -> handleGetLeaderboard(parts, out);

                            case "LOGOUT"   -> { handleLogout(out); return; }
                            case "PING"     -> out.println("PONG");
                            case "EXIT"     -> { out.println("BYE"); return; }
                            
                           case"ANSWER"  -> handleAnswer(parts, out);
                            default         -> out.println("ERROR;Unknown command");
                        }
                        out.flush(); // Ensure response is sent
                    } catch (Exception e) {
                        System.err.println("[ClientHandler] Error handling command " + cmd + ": " + e.getMessage());
                        e.printStackTrace();
                        try {
                            out.println("ERROR;Server error: " + e.getMessage());
                            out.flush();
                        } catch (Exception ex) {
                            System.err.println("[ClientHandler] Failed to send error message: " + ex.getMessage());
                        }
                        // Don't close connection, continue processing
                    }
                } catch (Exception e) {
                    System.err.println("[ClientHandler] Error processing line: " + e.getMessage());
                    e.printStackTrace();
                    // Continue to next line
                }
            }
        } catch (IOException e) {
            System.err.println("[ClientHandler] IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ClientHandler] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (currentUser != null) {
                Server.ONLINE_USERS.remove(currentUser);
                currentUser = null;
            }
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("[ClientHandler] Error closing resources: " + e.getMessage());
            }
        }
    }
    // ANSWER;opponent;roundNo;answerIndex;elapsedMs
private void handleAnswer(String[] parts, PrintWriter out) {
    if (currentUser == null) {
        out.println("ERROR;Unauthenticated");
        return;
    }
    if (parts.length < 5) {
        out.println("ERROR;Syntax: ANSWER;opponent;roundNo;answerIndex;elapsedMs");
        return;
    }

    String opponent = parts[1].trim();
    int roundNo     = Integer.parseInt(parts[2].trim());
    int answerIndex = Integer.parseInt(parts[3].trim());
    long elapsedMs  = Long.parseLong(parts[4].trim());

    // matchKey luôn theo alphabet
    String matchKey = MatchState.getMatchKey(currentUser, opponent);

    // Tạm thời: CHƯA có câu hỏi thật, anh cho correctIndex = 0 để test.
    // Sau này em thay bằng setCorrectIndex đúng trước khi bắt đầu round.
    MatchState.setCorrectIndex(matchKey, roundNo, 0);

    MatchState.RoundState rs = MatchState.submitAnswer(
            matchKey, roundNo, currentUser, answerIndex, elapsedMs
    );

    out.println("ANSWER_OK");

    if (rs != null && rs.isComplete()) {
        calculateAndSendRoundResult(matchKey, roundNo, rs);
    }
}

private void calculateAndSendRoundResult(String matchKey, int roundNo,
                                         MatchState.RoundState rs) {

    String[] players = matchKey.split(";");
    String player1 = players[0];
    String player2 = players[1];

    MatchState.RoundAnswer p1 = rs.p1;
    MatchState.RoundAnswer p2 = rs.p2;

    int p1Points = 0;
    int p2Points = 0;

    if (!p1.isCorrect && !p2.isCorrect) {
        // 0-0
    } else if (p1.isCorrect && !p2.isCorrect) {
        p1Points = 2;
    } else if (!p1.isCorrect && p2.isCorrect) {
        p2Points = 2;
    } else {
        // cả 2 đúng
        if (p1.timeMs < p2.timeMs) {
            p1Points = 2;
            p2Points = 1;
        } else if (p1.timeMs > p2.timeMs) {
            p1Points = 1;
            p2Points = 2;
        } else {
            p1Points = 1;
            p2Points = 1;
        }
    }

    int total1 = MatchState.addScore(matchKey, player1, p1Points);
    int total2 = MatchState.addScore(matchKey, player2, p2Points);

    // Format:
    // ANSWER_RESULT;roundNo;p1Correct;p1TimeMs;p1Points;p2Correct;p2TimeMs;p2Points;totalScoreP1;totalScoreP2
    String msg = String.format(
            "ANSWER_RESULT;%d;%s;%d;%d;%s;%d;%d;%d;%d",
            roundNo,
            p1.isCorrect, p1.timeMs, p1Points,
            p2.isCorrect, p2.timeMs, p2Points,
            total1, total2
    );

    Server.box(player1).add(msg);
    Server.box(player2).add(msg);

    System.out.println("[SERVER] Round " + roundNo + " | " + msg);
}


    private void handleRegister(String[] parts, PrintWriter out) throws SQLException {
        if (parts.length < 3) { out.println("ERROR;Syntax: REGISTER;username;password"); return; }
        String u = parts[1].trim(), p = parts[2].trim();
        if (u.isEmpty() || p.isEmpty()) { out.println("ERROR;Username or password cannot be empty"); return; }
        if (Database.userExists(u))     { out.println("ERROR;Username already exists"); return; }
        Database.registerUser(u, p);
        out.println("REGISTER_OK");
    }

    private void handleLogin(String[] parts, PrintWriter out) {
        try {
            if (parts.length < 3) { 
                out.println("ERROR;Syntax: LOGIN;username;password"); 
                out.flush();
                return; 
            }
            String u = parts[1].trim(), p = parts[2].trim();
            if (Database.checkLogin(u, p)) {
                currentUser = u;
                Server.ONLINE_USERS.add(currentUser);
                Server.box(currentUser); // tạo mailbox
                out.println("LOGIN_OK");
                out.flush();
            } else {
                out.println("ERROR;Invalid username or password");
                out.flush();
            }
        } catch (SQLException e) {
            System.err.println("[LOGIN] SQLException: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Database error during login");
                out.flush();
            } catch (Exception ex) {
                System.err.println("[LOGIN] Failed to send error: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[LOGIN] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Unexpected error during login");
                out.flush();
            } catch (Exception ex) {
                System.err.println("[LOGIN] Failed to send error: " + ex.getMessage());
            }
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
        
        if (decision.equals("ACCEPT")) {
            // Tạo seed cố định cho cả 2 client (dùng long để client parse)
            long seed = System.currentTimeMillis();

            // Xác định player1 và player2 theo thứ tự alphabet (không phân biệt hoa thường)
            String player1;
            String player2;
            if (currentUser.compareToIgnoreCase(opponent) < 0) {
                player1 = currentUser;
                player2 = opponent;
            } else {
                player1 = opponent;
                player2 = currentUser;
            }

            // Chọn thời điểm bắt đầu trận đấu theo đồng hồ server: sau 5s nữa
            long startAtMs = System.currentTimeMillis() + 5000;

            // Gửi MATCH_START cho cả hai:
            // FORMAT: MATCH_START;player1;player2;seed;startAtMs
            String matchStart = "MATCH_START;" + player1 + ";" + player2 + ";" + seed + ";" + startAtMs;
            System.out.println("[SERVER] MATCH_START -> " + matchStart);
            Server.box(player1).add(matchStart);
            Server.box(player2).add(matchStart);
        }
    }

    // POLL -> trả về 1 sự kiện hoặc NO_EVENT
    private void handlePoll(PrintWriter out) {
        if (currentUser == null) { 
            out.println("ERROR;Unauthenticated"); 
            out.flush();
            return; 
        }
        var q = Server.box(currentUser);
        String ev = q.poll();
        if (ev == null) {
            out.println("NO_EVENT");
        } else {
            out.println(ev); // ví dụ: INVITE_FROM;userA  / INVITE_RESULT;userB;ACCEPT  / START_MATCH;userB
            // Only log important events (not HISTORY, LEADERBOARD, or NO_EVENT)
            if (ev != null && !ev.equals("NO_EVENT") && 
                !ev.startsWith("HISTORY") && !ev.startsWith("LEADERBOARD")) {
                System.out.println("[POLL] Send to " + currentUser + ": " + ev);
            }
        }
        out.flush();
    }

    // GET_HISTORY;username -> send matches via mailbox queue
    private void handleGetHistory(String[] parts, PrintWriter out) {
        try {
            if (currentUser == null) { 
                out.println("ERROR;Unauthenticated"); 
                out.flush();
                return; 
            }
            if (parts.length < 2) { 
                out.println("ERROR;Syntax: GET_HISTORY;username"); 
                out.flush();
                return; 
            }
            
            String requestedUser = parts[1].trim();
            // Security: users can only request their own history
            if (!requestedUser.equals(currentUser)) {
                out.println("ERROR;You can only request your own history");
                out.flush();
                return;
            }
            
            List<MatchHistory> matches = Database.getLast10Matches(currentUser);
            var mailbox = Server.box(currentUser);
            
            // Send each match as HISTORY;player1;player2;score1;score2;winner
            for (MatchHistory match : matches) {
                try {
                    // Check for null values
                    String p1 = match.getPlayer1();
                    String p2 = match.getPlayer2();
                    int score1 = match.getScore1();
                    int score2 = match.getScore2();
                    String winner = match.getWinner();
                    
                    if (p1 == null || p2 == null || winner == null) {
                        System.err.println("[GET_HISTORY] Warning: Null value in match, skipping. p1=" + p1 + ", p2=" + p2 + ", winner=" + winner);
                        continue;
                    }
                    
                    // Format: HISTORY;player1;player2;score1;score2;winner
                    mailbox.add("HISTORY;" + p1 + ";" + p2 + ";" + score1 + ";" + score2 + ";" + winner);
                } catch (Exception e) {
                    System.err.println("[GET_HISTORY] Error processing match: " + e.getMessage());
                    e.printStackTrace();
                    // Continue with next match instead of crashing
                }
            }
            
            // Send HISTORY_END to signal completion
            mailbox.add("HISTORY_END");
            
            // Log summary only - one line
            System.out.println("[GET_HISTORY] History sent to " + currentUser);
            
            out.println("HISTORY_REQUEST_OK");
            out.flush();
        } catch (SQLException e) {
            System.err.println("[GET_HISTORY] SQLException: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Database error: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[GET_HISTORY] Failed to send error: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[GET_HISTORY] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Unexpected error: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[GET_HISTORY] Failed to send error: " + ex.getMessage());
            }
        }
    }

    // GET_LEADERBOARD -> send leaderboard via mailbox queue
    private void handleGetLeaderboard(String[] parts, PrintWriter out) {
        try {
            List<LeaderboardEntry> leaderboard = Database.getLeaderboard();
            // Use currentUser if available, otherwise use a temporary mailbox
            String userKey = currentUser != null ? currentUser : "anonymous";
            var mailbox = Server.box(userKey);
            
            // Send each entry as LEADERBOARD;username;wins;winRate
            for (LeaderboardEntry entry : leaderboard) {
                mailbox.add(String.format("LEADERBOARD;%s;%d;%.2f", 
                    entry.getUsername(), entry.getWins(), entry.getWinRate()));
            }
            
            // Send LEADERBOARD_END to signal completion
            mailbox.add("LEADERBOARD_END");
            
            // Log summary only - one line
            System.out.println("[GET_LEADERBOARD] Leaderboard sent to " + (currentUser != null ? currentUser : "anonymous"));
            
            out.println("LEADERBOARD_REQUEST_OK");
            out.flush();
        } catch (SQLException e) {
            System.err.println("[GET_LEADERBOARD] SQLException: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Database error: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[GET_LEADERBOARD] Failed to send error: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[GET_LEADERBOARD] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Unexpected error: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[GET_LEADERBOARD] Failed to send error: " + ex.getMessage());
            }
        }
    }

    private void handleLogout(PrintWriter out) {
        if (currentUser != null) {
            Server.ONLINE_USERS.remove(currentUser);
            currentUser = null;
        }
        out.println("LOGOUT_OK");
    }
}
