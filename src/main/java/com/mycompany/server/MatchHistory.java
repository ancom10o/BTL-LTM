package com.mycompany.server;

import java.sql.Timestamp;

/**
 * DTO for match history records
 */
public class MatchHistory {
    private final int matchId;
    private final String player1;
    private final String player2;
    private final int score1;
    private final int score2;
    private final String winner;
    private final Timestamp finishedAt;

    public MatchHistory(int matchId, String player1, String player2, int score1, int score2, String winner, Timestamp finishedAt) {
        this.matchId = matchId;
        this.player1 = player1;
        this.player2 = player2;
        this.score1 = score1;
        this.score2 = score2;
        this.winner = winner;
        this.finishedAt = finishedAt;
    }

    public int getMatchId() { return matchId; }
    public String getPlayer1() { return player1; }
    public String getPlayer2() { return player2; }
    public int getScore1() { return score1; }
    public int getScore2() { return score2; }
    public String getWinner() { return winner; }
    public Timestamp getFinishedAt() { return finishedAt; }

    /**
     * Format as HISTORY;matchId;player1;player2;score1;score2;winner;finishedAt
     */
    public String toHistoryString() {
        return String.format("HISTORY;%d;%s;%s;%d;%d;%s;%s",
            matchId, player1, player2, score1, score2, winner, finishedAt.toString());
    }
}

