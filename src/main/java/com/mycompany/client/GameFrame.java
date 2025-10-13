package com.mycompany.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameFrame extends JFrame {
    private final String username;
    private final String opponent;
    private final AuthClient client; // shared session (can be null if you prefer)

    private final JTextArea taLog = new JTextArea(10, 48);
    private final JButton btnLeave = new JButton("Leave Match");

    public GameFrame(String username, String opponent, AuthClient client) {
        super("Match: " + username + " vs " + opponent);
        this.username = username;
        this.opponent = opponent;
        this.client = client;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Header
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Playing: " + username + "  vs  " + opponent), BorderLayout.WEST);
        add(top, BorderLayout.NORTH);

        // Center placeholder (canvas area)
        JPanel arena = new JPanel();
        arena.setPreferredSize(new Dimension(640, 360));
        arena.setBackground(new Color(245, 246, 250));
        arena.setBorder(BorderFactory.createTitledBorder("Game Area (placeholder)"));
        add(arena, BorderLayout.CENTER);

        // Right side log
        taLog.setEditable(false);
        taLog.setLineWrap(true);
        JScrollPane sp = new JScrollPane(taLog);
        sp.setPreferredSize(new Dimension(300, 360));
        add(sp, BorderLayout.EAST);

        // Bottom bar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(btnLeave);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Events
        btnLeave.addActionListener(e -> doLeave());

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                log("Match started with " + opponent);
            }
        });
    }

    private void doLeave() {
        // Optionally tell server later (e.g., SEND/LEAVE). For now just close.
        dispose();
    }

    private void log(String s) { taLog.append(s + "\n"); }
}
