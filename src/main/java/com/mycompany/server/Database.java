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

    /**
     * Save match result to database and update player statistics
     */
    public static void saveMatch(String player1, String player2, int score1, int score2, String winner) throws SQLException {
        try (Connection conn = ds().getConnection()) {
            conn.setAutoCommit(false); // Start transaction
            
            try {
                // Insert match record
                String insertMatchSql = "INSERT INTO public.matches (player1, player2, score1, score2, winner, total_rounds, started_at, finished_at) " +
                                       "VALUES (?, ?, ?, ?, ?, 10, NOW(), NOW())";
                int matchId;
                try (PreparedStatement ps = conn.prepareStatement(insertMatchSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, player1);
                    ps.setString(2, player2);
                    ps.setInt(3, score1);
                    ps.setInt(4, score2);
                    ps.setString(5, winner);
                    ps.executeUpdate();
                    
                    // Get generated match ID
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            matchId = rs.getInt(1);
                        } else {
                            throw new SQLException("Failed to get match ID");
                        }
                    }
                }
                
                // Update statistics for both players
                // Increment total_matches for both
                String updateTotalSql = "UPDATE public.users SET total_matches = total_matches + 1 WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateTotalSql)) {
                    ps.setString(1, player1);
                    ps.executeUpdate();
                    
                    ps.setString(1, player2);
                    ps.executeUpdate();
                }
                
                // Increment wins for the winner (if not draw)
                if (!winner.equals("draw")) {
                    String updateWinsSql = "UPDATE public.users SET wins = wins + 1 WHERE username = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateWinsSql)) {
                        String winnerUsername = winner.equals("player1") ? player1 : player2;
                        ps.setString(1, winnerUsername);
                        ps.executeUpdate();
                    }
                }
                
                conn.commit(); // Commit transaction
                System.out.println("[Database] Match saved successfully: matchId=" + matchId + 
                                 ", " + player1 + " vs " + player2 + ", score=" + score1 + "-" + score2 + ", winner=" + winner);
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                System.err.println("[Database] Error saving match, transaction rolled back: " + e.getMessage());
                throw e;
            }
        }
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
