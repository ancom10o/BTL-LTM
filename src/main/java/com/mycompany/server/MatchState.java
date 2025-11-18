package com.mycompany.server;

import java.util.concurrent.ConcurrentHashMap;

public class MatchState {

    // 1 answer của 1 player trong 1 round
    public static class RoundAnswer {
        public final String username;
        public final int answerIndex;   // 0..3, hoặc -1 = không chọn
        public final long timeMs;       // thời gian bấm
        public final boolean isCorrect; // đúng/sai

        public RoundAnswer(String username, int answerIndex, long timeMs, boolean isCorrect) {
            this.username = username;
            this.answerIndex = answerIndex;
            this.timeMs = timeMs;
            this.isCorrect = isCorrect;
        }
    }

    // state của 1 round (2 người)
    public static class RoundState {
        public int correctIndex = -1;
        public RoundAnswer p1;
        public RoundAnswer p2;

        public boolean isComplete() {
            return p1 != null && p2 != null;
        }
    }

    // matchKey = "playerA;playerB" (alphabet)
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, RoundState>> matches = new ConcurrentHashMap<>();
    // tổng điểm
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> scores = new ConcurrentHashMap<>();

    public static String getMatchKey(String u1, String u2) {
        return (u1.compareToIgnoreCase(u2) < 0) ? (u1 + ";" + u2) : (u2 + ";" + u1);
    }

    private static RoundState getOrCreateRound(String matchKey, int roundNo) {
        matches.computeIfAbsent(matchKey, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<Integer, RoundState> rounds = matches.get(matchKey);
        return rounds.computeIfAbsent(roundNo, k -> new RoundState());
    }

    // server set correctIndex khi bắt đầu câu (sau này mình gọi từ logic câu hỏi)
    public static void setCorrectIndex(String matchKey, int roundNo, int correctIndex) {
        RoundState rs = getOrCreateRound(matchKey, roundNo);
        rs.correctIndex = correctIndex;
    }

    // submit answer, trả về RoundState để handler kiểm tra isComplete()
    public static RoundState submitAnswer(String matchKey, int roundNo,
                                          String username, int answerIndex, long timeMs) {
        RoundState rs = getOrCreateRound(matchKey, roundNo);

        boolean isCorrect = (rs.correctIndex >= 0 && answerIndex == rs.correctIndex);

        RoundAnswer answer = new RoundAnswer(username, answerIndex, timeMs, isCorrect);

        String[] players = matchKey.split(";");
        String p1Name = players[0];
        String p2Name = players[1];

        if (username.equals(p1Name)) {
            rs.p1 = answer;
        } else if (username.equals(p2Name)) {
            rs.p2 = answer;
        }
        return rs;
    }

    public static int addScore(String matchKey, String username, int delta) {
        scores.computeIfAbsent(matchKey, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<String, Integer> map = scores.get(matchKey);
        int newScore = map.getOrDefault(username, 0) + delta;
        map.put(username, newScore);
        return newScore;
    }

    public static int getScore(String matchKey, String username) {
        ConcurrentHashMap<String, Integer> map = scores.get(matchKey);
        if (map == null) return 0;
        return map.getOrDefault(username, 0);
    }

    public static void clearMatch(String matchKey) {
        matches.remove(matchKey);
        scores.remove(matchKey);
    }
}
