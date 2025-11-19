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

                    // Split với limit cao để lấy đủ các phần (ANSWER_RESULT có 6 phần)
                    String[] parts = line.split(";", -1); // -1 để giữ lại tất cả các phần
                    String cmd = parts[0].trim().toUpperCase();

                    // Only log non-polling commands to reduce noise
                    if (!cmd.equals("POLL") && !cmd.equals("WHO")) {
                        System.out.println("[ClientHandler] Received command: " + cmd + " from " + currentUser);
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
                        case "ANSWER_RESULT" -> handleAnswerResult(parts, out);
                        case "MATCH_END" -> handleMatchEnd(parts, out);

                            case "LOGOUT"   -> { handleLogout(out); return; }
                            case "PING"     -> out.println("PONG");
                            case "EXIT"     -> { out.println("BYE"); return; }
                            
                           // case"ANSWER"  -> handleAnswer(parts, out); // Deprecated, use ANSWER_RESULT instead
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
    
    // Deprecated: Old ANSWER handler - replaced by ANSWER_RESULT
    /*
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
    */


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
    
    // ANSWER_RESULT;matchKey;username;round;status;timeMs
    private void handleAnswerResult(String[] parts, PrintWriter out) {
        System.out.println("[ANSWER_RESULT] Starting handler, parts.length=" + parts.length + ", currentUser=" + currentUser);
        
        if (currentUser == null) {
            System.err.println("[ANSWER_RESULT] Unauthenticated");
            try {
                out.println("ERROR;Unauthenticated");
                out.flush();
            } catch (Exception e) {
                System.err.println("[ANSWER_RESULT] Failed to send error: " + e.getMessage());
            }
            return;
        }
        if (parts.length < 6) {
            System.err.println("[ANSWER_RESULT] Invalid syntax, parts.length=" + parts.length);
            try {
                out.println("ERROR;Syntax: ANSWER_RESULT;matchKey;username;round;status;timeMs");
                out.flush();
            } catch (Exception e) {
                System.err.println("[ANSWER_RESULT] Failed to send error: " + e.getMessage());
            }
            return;
        }
        
        try {
            String matchKey = parts[1].trim();
            String username = parts[2].trim();
            int round = Integer.parseInt(parts[3].trim());
            String status = parts[4].trim().toUpperCase();
            long timeMs = Long.parseLong(parts[5].trim());
            
            System.out.println("[ANSWER_RESULT] Received from " + currentUser + 
                             " | matchKey=" + matchKey + ", round=" + round + 
                             ", status=" + status + ", timeMs=" + timeMs);
            
            // Kiểm tra username có khớp với currentUser không
            if (!username.equals(currentUser)) {
                System.err.println("[ANSWER_RESULT] Username mismatch: " + username + " != " + currentUser);
                out.println("ERROR;Username mismatch");
                out.flush();
                return;
            }
            
            boolean isCorrect = status.equals("CORRECT");
            
            // Lấy hoặc tạo MatchState
            MatchState matchState = Server.ACTIVE_MATCHES.computeIfAbsent(matchKey, k -> {
                // Parse matchKey để lấy player1 và player2
                // Format: player1_player2_seed
                String[] keyParts = k.split("_");
                if (keyParts.length < 3) {
                    System.err.println("[ANSWER_RESULT] Invalid matchKey format: " + k);
                    return null;
                }
                String player1 = keyParts[0];
                String player2 = keyParts[1];
                System.out.println("[ANSWER_RESULT] Created new MatchState: " + player1 + " vs " + player2);
                return new MatchState(k, player1, player2);
            });
            
            if (matchState == null) {
                System.err.println("[ANSWER_RESULT] Failed to create/get MatchState for: " + matchKey);
                out.println("ERROR;Invalid matchKey");
                out.flush();
                return;
            }
            
            // Thêm đáp án
            matchState.addAnswer(round, username, isCorrect, timeMs);
            System.out.println("[ANSWER_RESULT] Added answer for " + username + 
                             " | round=" + round + ", correct=" + isCorrect + ", time=" + timeMs + "ms");
            
            // Kiểm tra xem cả 2 player đã trả lời chưa
            boolean bothAnswered = matchState.bothAnswered(round);
            System.out.println("[ANSWER_RESULT] Both answered? " + bothAnswered + 
                             " | P1=" + matchState.getPlayer1() + ", P2=" + matchState.getPlayer2() +
                             " | Thread=" + Thread.currentThread().getName());
            
            if (bothAnswered) {
                // Kiểm tra xem đã gửi RESULT cho round này chưa (tránh gửi 2 lần)
                // Sử dụng synchronized để đảm bảo chỉ một thread có thể gửi RESULT
                synchronized (matchState) {
                    // Kiểm tra lại sau khi synchronized (double-check)
                    if (!matchState.bothAnswered(round)) {
                        System.out.println("[ANSWER_RESULT] Both answered check failed after sync, skipping");
                        out.println("ANSWER_RESULT_OK");
                        out.flush();
                        return;
                    }
                    
                    if (!matchState.markResultSent(round)) {
                        // Đã gửi rồi, không gửi lại
                        System.out.println("[ANSWER_RESULT] RESULT already sent for round " + round + ", skipping (Thread=" + Thread.currentThread().getName() + ")");
                        out.println("ANSWER_RESULT_OK");
                        out.flush();
                        return;
                    }
                    
                    // Tính điểm
                    MatchState.RoundResult result = matchState.calculateResult(round);
                    
                    if (result == null) {
                        System.err.println("[ANSWER_RESULT] Failed to calculate result for round " + round);
                        // Không cần reset flag vì markResultSent đã set thành true
                        // Nếu tính toán thất bại, vẫn giữ flag để không gửi lại
                        out.println("ANSWER_RESULT_OK");
                        out.flush();
                        return;
                    }
                    
                    // Gửi kết quả cho cả 2 player (chỉ gửi 1 lần)
                    // FORMAT: RESULT;matchKey;round;p1Score;p2Score;p1Correct;p2Correct;p1TimeMs;p2TimeMs
                    String resultMsg = "RESULT;" + matchKey + ";" + round + ";" + 
                                     result.p1Score + ";" + result.p2Score + ";" + 
                                     (result.p1Correct ? "1" : "0") + ";" + 
                                     (result.p2Correct ? "1" : "0") + ";" +
                                     result.p1TimeMs + ";" + result.p2TimeMs;
                    
                    Server.box(matchState.getPlayer1()).add(resultMsg);
                    Server.box(matchState.getPlayer2()).add(resultMsg);
                    
                    System.out.println("[RESULT] Round " + round + " sent to " + matchState.getPlayer1() + 
                                     " and " + matchState.getPlayer2() + 
                                     " | P1=" + matchState.getPlayer1() + "(" + (result.p1TimeMs/1000.0) + "s): " + 
                                     (result.p1Correct ? "Đúng" : "Sai") + " +" + result.p1Score + "đ" +
                                     " | P2=" + matchState.getPlayer2() + "(" + (result.p2TimeMs/1000.0) + "s): " + 
                                     (result.p2Correct ? "Đúng" : "Sai") + " +" + result.p2Score + "đ" +
                                     " | Thread=" + Thread.currentThread().getName());
                    
                    // Xóa đáp án của round này để giải phóng memory
                    matchState.clearRound(round);
                }
            } else {
                System.out.println("[ANSWER_RESULT] Waiting for other player...");
            }
            
            System.out.println("[ANSWER_RESULT] Sending ANSWER_RESULT_OK to " + currentUser);
            out.println("ANSWER_RESULT_OK");
            out.flush();
            System.out.println("[ANSWER_RESULT] Response sent successfully to " + currentUser);
        } catch (NumberFormatException e) {
            System.err.println("[ANSWER_RESULT] NumberFormatException: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Invalid number format");
                out.flush();
            } catch (Exception ex) {
                System.err.println("[ANSWER_RESULT] Failed to send error: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[ANSWER_RESULT] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Invalid parameters: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[ANSWER_RESULT] Failed to send error: " + ex.getMessage());
            }
        }
    }
    
    // MATCH_END;matchKey;player1;player2;score1;score2
    private void handleMatchEnd(String[] parts, PrintWriter out) {
        System.out.println("[MATCH_END] Starting handler, parts.length=" + parts.length + ", currentUser=" + currentUser);
        
        if (currentUser == null) {
            System.err.println("[MATCH_END] Unauthenticated");
            try {
                out.println("ERROR;Unauthenticated");
                out.flush();
            } catch (Exception e) {
                System.err.println("[MATCH_END] Failed to send error: " + e.getMessage());
            }
            return;
        }
        
        if (parts.length < 6) {
            System.err.println("[MATCH_END] Invalid syntax, parts.length=" + parts.length);
            try {
                out.println("ERROR;Syntax: MATCH_END;matchKey;player1;player2;score1;score2");
                out.flush();
            } catch (Exception e) {
                System.err.println("[MATCH_END] Failed to send error: " + e.getMessage());
            }
            return;
        }
        
        try {
            String matchKey = parts[1].trim();
            String player1 = parts[2].trim();
            String player2 = parts[3].trim();
            int score1 = Integer.parseInt(parts[4].trim());
            int score2 = Integer.parseInt(parts[5].trim());
            
            System.out.println("[MATCH_END] Received from " + currentUser + 
                             " | matchKey=" + matchKey + ", player1=" + player1 + 
                             ", player2=" + player2 + ", score1=" + score1 + ", score2=" + score2);
            
            // Kiểm tra currentUser có phải là player1 hoặc player2 không
            if (!currentUser.equals(player1) && !currentUser.equals(player2)) {
                System.err.println("[MATCH_END] User mismatch: " + currentUser + " is not " + player1 + " or " + player2);
                out.println("ERROR;User not in match");
                out.flush();
                return;
            }
            
            // Xác định người thắng
            String winner;
            if (score1 > score2) {
                winner = "player1";
            } else if (score2 > score1) {
                winner = "player2";
            } else {
                winner = "draw";
            }
            
            // Lưu match vào database (chỉ lưu 1 lần, kiểm tra xem đã lưu chưa)
            // Sử dụng synchronized để đảm bảo chỉ một thread có thể lưu match
            synchronized (Server.ACTIVE_MATCHES) {
                MatchState matchState = Server.ACTIVE_MATCHES.get(matchKey);
                if (matchState != null) {
                    // Kiểm tra xem đã lưu match chưa (dùng một flag trong MatchState hoặc kiểm tra database)
                    // Tạm thời: luôn lưu, sau này có thể thêm flag để tránh lưu 2 lần
                    try {
                        Database.saveMatch(player1, player2, score1, score2, winner);
                        System.out.println("[MATCH_END] Match saved to database: " + player1 + " vs " + player2 + 
                                         ", score=" + score1 + "-" + score2 + ", winner=" + winner);
                        
                        // Xóa MatchState khỏi ACTIVE_MATCHES sau khi đã lưu
                        Server.ACTIVE_MATCHES.remove(matchKey);
                    } catch (SQLException e) {
                        System.err.println("[MATCH_END] Error saving match to database: " + e.getMessage());
                        e.printStackTrace();
                        out.println("ERROR;Database error: " + e.getMessage());
                        out.flush();
                        return;
                    }
                } else {
                    System.out.println("[MATCH_END] MatchState not found for matchKey: " + matchKey + ", trying to save anyway");
                    // Vẫn cố gắng lưu vào database (có thể match đã bị xóa khỏi ACTIVE_MATCHES)
                    try {
                        Database.saveMatch(player1, player2, score1, score2, winner);
                        System.out.println("[MATCH_END] Match saved to database (no MatchState): " + player1 + " vs " + player2);
                    } catch (SQLException e) {
                        System.err.println("[MATCH_END] Error saving match to database: " + e.getMessage());
                        e.printStackTrace();
                        out.println("ERROR;Database error: " + e.getMessage());
                        out.flush();
                        return;
                    }
                }
            }
            
            out.println("MATCH_END_OK");
            out.flush();
            System.out.println("[MATCH_END] Response sent successfully to " + currentUser);
        } catch (NumberFormatException e) {
            System.err.println("[MATCH_END] NumberFormatException: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Invalid number format");
                out.flush();
            } catch (Exception ex) {
                System.err.println("[MATCH_END] Failed to send error: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[MATCH_END] Error: " + e.getMessage());
            e.printStackTrace();
            try {
                out.println("ERROR;Invalid parameters: " + e.getMessage());
                out.flush();
            } catch (Exception ex) {
                System.err.println("[MATCH_END] Failed to send error: " + ex.getMessage());
            }
        }
    }
}
