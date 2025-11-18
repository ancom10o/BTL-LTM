package com.mycompany.server;

public class LeaderboardEntry {
    private String username;
    private int wins;
    private double winRate;

    public LeaderboardEntry(String username, int wins, double winRate) {
        this.username = username;
        this.wins = wins;
        this.winRate = winRate;
    }

    public String getUsername() {
        return username;
    }

    public int getWins() {
        return wins;
    }

    public double getWinRate() {
        return winRate;
    }
}

