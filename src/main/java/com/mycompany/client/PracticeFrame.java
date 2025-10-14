package com.mycompany.client;

import javax.swing.*;
import java.awt.*;

public class PracticeFrame extends JFrame {

    public PracticeFrame() {
        super("Practice Mode");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        JLabel label = new JLabel("<html><h2>Chế độ luyện tập</h2><p>Chức năng đang phát triển...</p></html>", SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);
    }
}
