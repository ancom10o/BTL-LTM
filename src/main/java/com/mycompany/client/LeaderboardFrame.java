package com.mycompany.client;

import javax.swing.*;
import java.awt.*;

public class LeaderboardFrame extends JFrame {

    public LeaderboardFrame() {
        super("Leaderboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        JLabel label = new JLabel("<html><h2>Bảng xếp hạng</h2><p>Chức năng đang phát triển...</p></html>", SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);
    }
}
