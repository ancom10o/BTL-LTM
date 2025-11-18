package com.mycompany.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lưu trữ trạng thái của một match đang diễn ra
 */
public class MatchState {
    private final String matchKey;
    private final String player1;
    private final String player2;
    
    // Lưu đáp án của từng round: round -> (player -> AnswerInfo)
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, AnswerInfo>> roundAnswers = new ConcurrentHashMap<>();
    
    // Đánh dấu các round đã gửi RESULT (để tránh gửi 2 lần)
    private final ConcurrentHashMap<Integer, AtomicBoolean> roundResultSent = new ConcurrentHashMap<>();
    
    public MatchState(String matchKey, String player1, String player2) {
        this.matchKey = matchKey;
        this.player1 = player1;
        this.player2 = player2;
    }
    
    public String getMatchKey() { return matchKey; }
    public String getPlayer1() { return player1; }
    public String getPlayer2() { return player2; }
    
    /**
     * Thêm đáp án của một player cho một round
     */
    public void addAnswer(int round, String player, boolean isCorrect, long timeMs) {
        roundAnswers.computeIfAbsent(round, k -> new ConcurrentHashMap<>())
                   .put(player, new AnswerInfo(isCorrect, timeMs));
    }
    
    /**
     * Kiểm tra xem cả 2 player đã trả lời chưa
     */
    public boolean bothAnswered(int round) {
        ConcurrentHashMap<String, AnswerInfo> answers = roundAnswers.get(round);
        if (answers == null) return false;
        return answers.containsKey(player1) && answers.containsKey(player2);
    }
    
    /**
     * Tính điểm và trả về kết quả cho cả 2 player
     */
    public RoundResult calculateResult(int round) {
        ConcurrentHashMap<String, AnswerInfo> answers = roundAnswers.get(round);
        if (answers == null || !bothAnswered(round)) {
            return null;
        }
        
        AnswerInfo p1Answer = answers.get(player1);
        AnswerInfo p2Answer = answers.get(player2);
        
        int p1Score = 0;
        int p2Score = 0;
        
        // Nếu cả 2 đều đúng
        if (p1Answer.isCorrect && p2Answer.isCorrect) {
            // Ai nhanh hơn được 2 điểm, chậm hơn được 1 điểm
            if (p1Answer.timeMs < p2Answer.timeMs) {
                p1Score = 2;
                p2Score = 1;
            } else if (p2Answer.timeMs < p1Answer.timeMs) {
                p1Score = 1;
                p2Score = 2;
            } else {
                // Cùng thời gian: cả 2 đều 1 điểm
                p1Score = 1;
                p2Score = 1;
            }
        } else if (p1Answer.isCorrect) {
            // Chỉ player1 đúng
            p1Score = 2;
            p2Score = 0;
        } else if (p2Answer.isCorrect) {
            // Chỉ player2 đúng
            p1Score = 0;
            p2Score = 2;
        } else {
            // Cả 2 đều sai
            p1Score = 0;
            p2Score = 0;
        }
        
        return new RoundResult(p1Score, p2Score, p1Answer.isCorrect, p2Answer.isCorrect, 
                             p1Answer.timeMs, p2Answer.timeMs);
    }
    
    /**
     * Kiểm tra xem đã gửi RESULT cho round này chưa
     */
    public boolean isResultSent(int round) {
        AtomicBoolean sent = roundResultSent.get(round);
        return sent != null && sent.get();
    }
    
    /**
     * Đánh dấu đã gửi RESULT cho round này (chỉ gửi 1 lần)
     * @return true nếu đánh dấu thành công (chưa gửi trước đó), false nếu đã gửi rồi
     */
    public boolean markResultSent(int round) {
        AtomicBoolean sent = roundResultSent.computeIfAbsent(round, k -> new AtomicBoolean(false));
        return sent.compareAndSet(false, true);
    }
    
    /**
     * Xóa đáp án của một round (sau khi đã xử lý xong)
     */
    public void clearRound(int round) {
        roundAnswers.remove(round);
        roundResultSent.remove(round);
    }
    
    public static class AnswerInfo {
        final boolean isCorrect;
        final long timeMs;
        
        AnswerInfo(boolean isCorrect, long timeMs) {
            this.isCorrect = isCorrect;
            this.timeMs = timeMs;
        }
    }
    
    public static class RoundResult {
        public final int p1Score;
        public final int p2Score;
        public final boolean p1Correct;
        public final boolean p2Correct;
        public final long p1TimeMs;
        public final long p2TimeMs;
        
        RoundResult(int p1Score, int p2Score, boolean p1Correct, boolean p2Correct, 
                   long p1TimeMs, long p2TimeMs) {
            this.p1Score = p1Score;
            this.p2Score = p2Score;
            this.p1Correct = p1Correct;
            this.p2Correct = p2Correct;
            this.p1TimeMs = p1TimeMs;
            this.p2TimeMs = p2TimeMs;
        }
    }
}
