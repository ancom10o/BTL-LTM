package com.mycompany.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AuthFrame extends JFrame {

    // ======= Cấu hình mặc định server (thay cho Host/Port UI) =======
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9090;

    private final JTabbedPane tabs = new JTabbedPane();

    // Login tab
    private final JTextField tfUserLogin = new JTextField(16);
    private final JPasswordField pfPassLogin = new JPasswordField(16);
    private final JCheckBox cbShowLogin = new JCheckBox("Show password");
    private final JButton btnLogin = new JButton("Login");

    // Register tab
    private final JTextField tfUserReg = new JTextField(16);
    private final JPasswordField pfPassReg = new JPasswordField(16);
    private final JPasswordField pfPassReg2 = new JPasswordField(16);
    private final JCheckBox cbShowReg = new JCheckBox("Show password");
    private final JButton btnRegister = new JButton("Register");

    private final JTextArea taLog = new JTextArea(8, 40);

    private char defaultEcho; // remember default echo char
    private AuthClient client;

    public AuthFrame() {
        super("Login / Register");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        defaultEcho = pfPassLogin.getEchoChar();
        taLog.setEditable(false);
        taLog.setLineWrap(true);

        // Không còn top bar Host/Port/Test
        add(buildTabs(), BorderLayout.CENTER);
        add(new JScrollPane(taLog), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Events
        cbShowLogin.addActionListener(e -> {
            boolean show = cbShowLogin.isSelected();
            char echo = show ? (char) 0 : defaultEcho;
            pfPassLogin.setEchoChar(echo);
        });

        cbShowReg.addActionListener(e -> {
            boolean show = cbShowReg.isSelected();
            char echo = show ? (char) 0 : defaultEcho;
            pfPassReg.setEchoChar(echo);
            pfPassReg2.setEchoChar(echo);
        });

        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> doRegister());

        addWindowListener(new WindowAdapter() {
    @Override public void windowClosing(WindowEvent e) { closeClientQuiet(); }
    });
    }

    private JComponent buildTabs() {
        tabs.add("Login", buildLoginPanel());
        tabs.add("Register", buildRegisterPanel());
        return tabs;
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Username:"), c);
        c.gridx = 1; p.add(tfUserLogin, c);

        c.gridx = 0; c.gridy = 1; p.add(new JLabel("Password:"), c);
        c.gridx = 1; p.add(pfPassLogin, c);

        // Checkbox show password ngay dưới mật khẩu
        c.gridx = 1; c.gridy = 2; c.anchor = GridBagConstraints.WEST;
        p.add(cbShowLogin, c);

        c.gridx = 1; c.gridy = 3; c.anchor = GridBagConstraints.EAST;
        p.add(btnLogin, c);

        return p;
    }

    private JPanel buildRegisterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Username:"), c);
        c.gridx = 1; p.add(tfUserReg, c);

        c.gridx = 0; c.gridy = 1; p.add(new JLabel("Password:"), c);
        c.gridx = 1; p.add(pfPassReg, c);

        c.gridx = 0; c.gridy = 2; p.add(new JLabel("Confirm password:"), c);
        c.gridx = 1; p.add(pfPassReg2, c);

        // Checkbox show password ngay dưới cụm mật khẩu
        c.gridx = 1; c.gridy = 3; c.anchor = GridBagConstraints.WEST;
        p.add(cbShowReg, c);

        c.gridx = 1; c.gridy = 4; c.anchor = GridBagConstraints.EAST;
        p.add(btnRegister, c);

        return p;
    }

    private boolean ensureClient(boolean showDialog) {
        try {
            closeClientQuiet();
            client = new AuthClient(DEFAULT_HOST, DEFAULT_PORT);
            client.connect();
            log("Connected to " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            if (showDialog) {
                JOptionPane.showMessageDialog(this, "Connection OK", "Info", JOptionPane.INFORMATION_MESSAGE);
            }
            return true;
        } catch (Exception ex) {
            log("Connect failed: " + ex.getMessage());
            if (showDialog) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Connection failed", JOptionPane.WARNING_MESSAGE);
            }
            return false;
        }
    }

    private void doLogin() {
        final String user = tfUserLogin.getText().trim();
        final String pass = new String(pfPassLogin.getPassword());

        if (!validateCreds(user, pass)) return;
        if (!ensureClient(true)) return;

        new SwingWorker<Void, Void>() {
            String resp;

            @Override
            protected Void doInBackground() throws Exception {
                String cmd = "LOGIN;" + user + ";" + pass;
                resp = client.sendCommand(cmd);
                log("> " + cmd);
                log("< " + String.valueOf(resp));
                return null;
            }

            @Override
            protected void done() {
                String r = (resp == null) ? "" : resp.trim();
                boolean ok = r.toUpperCase().startsWith("LOGIN_OK");
                if (ok) {
                    JOptionPane.showMessageDialog(AuthFrame.this, "Login successful.", "Login", JOptionPane.INFORMATION_MESSAGE);

                    final String hostVal = DEFAULT_HOST;
                    final int portVal = DEFAULT_PORT;
                    final String userVal = user;

                    SwingUtilities.invokeLater(() -> {
                        new LobbyFrame(userVal, hostVal, portVal, client).setVisible(true);
                        client = null;
                        dispose();
                    });

                } else {
                    JOptionPane.showMessageDialog(AuthFrame.this,
                            resp == null ? "No response" : resp,
                            "Login failed",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void doRegister() {
        String user = tfUserReg.getText().trim();
        String pass1 = new String(pfPassReg.getPassword());
        String pass2 = new String(pfPassReg2.getPassword());
        if (!validateCreds(user, pass1)) return;
        if (!pass1.equals(pass2)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Invalid input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!ensureClient(true)) return;

        new SwingWorker<Void, Void>() {
            String resp;
            @Override protected Void doInBackground() throws Exception {
                String cmd = "REGISTER;" + user + ";" + pass1;
                resp = client.sendCommand(cmd);
                log("> " + cmd);
                log("< " + resp);
                return null;
            }
            @Override protected void done() {
                if (resp != null && resp.toUpperCase().contains("REGISTER_OK")) {
                    JOptionPane.showMessageDialog(AuthFrame.this, "Register successful. You can login now.", "Register", JOptionPane.INFORMATION_MESSAGE);
                    tabs.setSelectedIndex(0);
                    tfUserLogin.setText(user);
                    pfPassLogin.setText(pass1);
                } else {
                    JOptionPane.showMessageDialog(AuthFrame.this, resp == null ? "No response" : resp, "Register failed", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private boolean validateCreds(String user, String pass) {
        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password.", "Missing data", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (user.contains(";") || pass.contains(";")) {
            JOptionPane.showMessageDialog(this, "Do not use ';' in username/password.", "Invalid character", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (pass.length() < 6) {
            JOptionPane.showMessageDialog(this, "Password should be at least 6 characters.", "Weak password", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void log(String s) { taLog.append(s + "\n"); }

    private void closeClientQuiet() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
    }
}
