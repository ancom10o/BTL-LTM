package com.mycompany.server;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static DataSource ds() {
        return DbPool.get();
    }

    public static boolean userExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM public.users WHERE username = ? LIMIT 1";
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void registerUser(String username, String password) throws SQLException {
        String sql = "INSERT INTO public.users (username, password) VALUES (?, ?)";
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
        }
    }

    public static boolean checkLogin(String username, String password) throws SQLException {
        String sql = "SELECT password FROM public.users WHERE username = ?";
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String pass = rs.getString("password");
                    return pass != null && pass.equals(password);
                }
            }
        }
        return false;
    }
    /**
     * Get last 10 matches for a user (where user is either player1 or player2)
     * Results are sorted by finished_at DESC (newest first)
     */
    public static List<MatchHistory> getLast10Matches(String username) throws SQLException {
        String sql = "SELECT * FROM public.matches WHERE player1 = ? OR player2 = ? ORDER BY finished_at DESC LIMIT 10";
        List<MatchHistory> matches = new ArrayList<>();
        
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, username);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        int matchId = rs.getInt("id");
                        String player1 = rs.getString("player1");
                        String player2 = rs.getString("player2");
                        int score1 = rs.getInt("score1");
                        int score2 = rs.getInt("score2");
                        String winner = rs.getString("winner");
                        Timestamp finishedAt = rs.getTimestamp("finished_at");
                        
                        // Validate data before adding
                        if (player1 != null && player2 != null && winner != null) {
                            matches.add(new MatchHistory(matchId, player1, player2, score1, score2, winner, finishedAt));
                        } else {
                            System.err.println("[Database] Warning: Skipping match with null values. ID=" + matchId);
                        }
                    } catch (SQLException e) {
                        System.err.println("[Database] Error reading match row: " + e.getMessage());
                        e.printStackTrace();
                        // Continue with next row
                    }
                }
            }
        }
        
        return matches;
    }

    /**
     * Get user stats: wins and total_matches
     */
    public static int[] getUserStats(String username) throws SQLException {
        String sql = "SELECT wins, total_matches FROM public.users WHERE username = ?";
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int wins = rs.getInt("wins");
                    int totalMatches = rs.getInt("total_matches");
                    // Ensure non-negative values
                    wins = Math.max(0, wins);
                    totalMatches = Math.max(0, totalMatches);
                    return new int[]{wins, totalMatches};
                }
            }
        } catch (SQLException e) {
            System.err.println("[Database] Error getting user stats for " + username + ": " + e.getMessage());
            throw e; // Re-throw to be handled by caller
        }
        // Return default if user not found
        return new int[]{0, 0};
    }

    /**
     * Get leaderboard: top users sorted by win rate and wins
     */
    public static List<LeaderboardEntry> getLeaderboard() throws SQLException {
        String sql = "SELECT username, wins, total_matches, " +
                     "ROUND(CASE WHEN total_matches = 0 THEN 0 " +
                     "     ELSE (wins * 100.0 / total_matches) END, 2) AS win_rate " +
                     "FROM public.users " +
                     "ORDER BY win_rate DESC, wins DESC, username ASC";
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        
        try (Connection conn = ds().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    int wins = rs.getInt("wins");
                    double winRate = rs.getDouble("win_rate");
                    leaderboard.add(new LeaderboardEntry(username, wins, winRate));
                }
            }
        }
        
        return leaderboard;
    }

    public static void debugInfo() {
    try (var c = DbPool.get().getConnection();
         var st = c.createStatement();
         var rs = st.executeQuery("select current_database(), current_schema(), current_setting('search_path')")) {
        if (rs.next()) {
            System.out.println("[DB] current_database=" + rs.getString(1) +
                               ", current_schema=" + rs.getString(2) +
                               ", search_path=" + rs.getString(3));
        }
    } catch (Exception e) { e.printStackTrace(); }
}
}
