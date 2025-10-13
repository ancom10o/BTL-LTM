package com.mycompany.server;

import javax.sql.DataSource;
import java.sql.*;

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
