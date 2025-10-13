package com.mycompany.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LobbyFrame extends JFrame {
    private final String username;
    private final String host;
    private final int port;
    private AuthClient client;     // ⭐ dùng lại socket đã LOGIN

    // UI controls
    private final JTextArea taLog = new JTextArea(10, 40);
    private final JButton btnPing = new JButton("Send PING");
    private final JButton btnLogout = new JButton("Logout");

    // Online panel
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> lstOnline = new JList<>(onlineModel);
    private final JButton btnRefresh = new JButton("Refresh");
    private final JButton btnChallenge = new JButton("Challenge");

    // Invites / Events
    private final JTextArea taEvent = new JTextArea(12, 32);

    private Timer whoTimer;     // poll WHO
    private Timer eventTimer;   // poll POLL

    // ⭐ Nhận client đã login
    public LobbyFrame(String username, String host, int port, AuthClient loggedInClient) {
        super("Lobby");
        this.username = username;
        this.host = host;
        this.port = port;
        this.client = loggedInClient;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top bar
        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("Welcome, " + username + "  |  Server: " + host + ":" + port), BorderLayout.WEST);
        add(top, BorderLayout.NORTH);

        // Left: console + events
        taLog.setEditable(false);
        taLog.setLineWrap(true);
        taEvent.setEditable(false);
        taEvent.setLineWrap(true);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(new JLabel("Console"), BorderLayout.NORTH);
        left.add(new JScrollPane(taLog), BorderLayout.CENTER);

        JPanel eventPanel = new JPanel(new BorderLayout(6, 6));
        eventPanel.add(new JLabel("Invites / Events"), BorderLayout.NORTH);
        eventPanel.add(new JScrollPane(taEvent), BorderLayout.CENTER);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, left, eventPanel);
        leftSplit.setResizeWeight(0.6);

        // Right: online users
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(new JLabel("Online users"), BorderLayout.NORTH);
        lstOnline.setVisibleRowCount(12);
        right.add(new JScrollPane(lstOnline), BorderLayout.CENTER);
        JPanel rightBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBottom.add(btnChallenge);
        rightBottom.add(btnRefresh);
        right.add(rightBottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, right);
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        // Bottom
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(btnPing);
        bottom.add(btnLogout);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Events
        btnPing.addActionListener(e -> doPing());
        btnLogout.addActionListener(e -> doLogout());
        btnRefresh.addActionListener(e -> refreshOnline());
        btnChallenge.addActionListener(e -> sendInvite());

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                // ⭐ KHÔNG reconnect nữa – đã login sẵn
                log("Using existing session on " + host + ":" + port);
                startPolling();
                refreshOnline();
            }
            @Override public void windowClosing(WindowEvent e) {
                stopPolling();
                closeClientQuiet(); // đóng kết nối khi cửa sổ đóng
            }
        });
    }

    /* ================== Poll timers ================== */
    private void startPolling() {
        if (whoTimer != null) whoTimer.stop();
        whoTimer = new Timer(5000, e -> refreshOnline());
        whoTimer.setInitialDelay(200);
        whoTimer.start();

        if (eventTimer != null) eventTimer.stop();
        eventTimer = new Timer(1000, e -> pollEvent());
        eventTimer.setInitialDelay(500);
        eventTimer.start();
    }
    private void stopPolling() {
        if (whoTimer != null) whoTimer.stop();
        if (eventTimer != null) eventTimer.stop();
    }

    /* ================== WHO (online list) ================== */
    private void refreshOnline() {
        if (client == null) return;
        new SwingWorker<Void, Void>() {
            String resp;
            @Override protected Void doInBackground() {
                try { resp = client.sendCommand("WHO"); }
                catch (Exception ex) { resp = "ERROR:" + ex.getMessage(); }
                return null;
            }
            @Override protected void done() {
                if (resp == null) return;
                if (resp.toUpperCase().startsWith("ONLINE;")) {
                    String list = resp.substring("ONLINE;".length());
                    List<String> users = Arrays.stream(list.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    onlineModel.clear();
                    for (String u : users) onlineModel.addElement(u);
                } else {
                    log("< " + resp);
                }
            }
        }.execute();
    }

    /* ================== Invite / Challenge ================== */
    private void sendInvite() {
        String to = lstOnline.getSelectedValue();
        if (to == null || to.isEmpty()) { JOptionPane.showMessageDialog(this, "Select an online user to challenge."); return; }
        if (to.equals(username)) { JOptionPane.showMessageDialog(this, "Cannot challenge yourself."); return; }
        if (client == null) { JOptionPane.showMessageDialog(this, "Not connected."); return; }

        new SwingWorker<Void,Void>() {
            String resp;
            @Override protected Void doInBackground() {
                try { resp = client.sendCommand("INVITE;" + to); }
                catch (Exception ex) { resp = "ERROR:" + ex.getMessage(); }
                return null;
            }
            @Override protected void done() {
                if (resp != null && resp.startsWith("INVITE_SENT")) {
                    event("You invited " + to + " to a match.");
                } else {
                    event("Invite failed: " + (resp == null ? "No response" : resp));
                }
            }
        }.execute();
    }

    private void pollEvent() {
        if (client == null) return;
        new SwingWorker<Void,Void>() {
            String resp;
            @Override protected Void doInBackground() {
                try { resp = client.sendCommand("POLL"); }
                catch (Exception ex) { resp = null; }
                return null;
            }
            @Override protected void done() {
                if (resp == null || resp.equalsIgnoreCase("NO_EVENT")) return;

                String up = resp.toUpperCase();
                if (up.startsWith("INVITE_FROM;")) {
                    String from = resp.substring("INVITE_FROM;".length());
                    int choice = JOptionPane.showConfirmDialog(LobbyFrame.this,
                            from + " challenges you. Accept?",
                            "Challenge", JOptionPane.YES_NO_OPTION);
                    respondInvite(from, choice == JOptionPane.YES_OPTION);
                } else if (up.startsWith("INVITE_RESULT;")) {
                    String[] p = resp.split(";", 3);
                    String who = (p.length > 1) ? p[1] : "?";
                    String result = (p.length > 2) ? p[2] : "";
                    event("Your challenge to " + who + ": " + result);
                } else if (up.startsWith("START_MATCH;")) {
                    String opp = resp.substring("START_MATCH;".length());
                    event("Match starting with " + opp + "!");
                    SwingUtilities.invokeLater(() -> new GameFrame(username, opp, client).setVisible(true));
                } else {
                    event("< " + resp);
                }
            }
        }.execute();
    }

    private void respondInvite(String opponent, boolean accept) {
        if (client == null) return;
        new SwingWorker<Void,Void>() {
            String resp;
            @Override protected Void doInBackground() {
                try {
                    resp = client.sendCommand("RESPOND;" + opponent + ";" + (accept ? "ACCEPT" : "REJECT"));
                } catch (Exception ex) { resp = "ERROR:" + ex.getMessage(); }
                return null;
            }
            @Override protected void done() {
                if (resp != null && resp.startsWith("RESPOND_OK")) {
                    event("You " + (accept ? "accepted" : "rejected") + " " + opponent + "'s challenge.");
                } else {
                    event("Respond failed: " + (resp == null ? "No response" : resp));
                }
            }
        }.execute();
    }

    /* ================== Misc ================== */
    private void doPing() {
        if (client == null) { JOptionPane.showMessageDialog(this, "Not connected."); return; }
        new SwingWorker<Void, Void>() {
            String resp;
            @Override protected Void doInBackground() throws Exception {
                resp = client.sendCommand("PING;" + username);
                return null;
            }
            @Override protected void done() { log("> PING"); log("< " + resp); }
        }.execute();
    }

    private void doLogout() {
        stopPolling();
        try { if (client != null) client.sendCommand("LOGOUT"); } catch (Exception ignored) {}
        closeClientQuiet();
        SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
        dispose();
    }

    private void log(String s)   { taLog.append(s + "\n"); }
    private void event(String s) { taEvent.append(s + "\n"); }

    private void closeClientQuiet() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
    }
}
