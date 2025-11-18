package com.mycompany.client;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.InputStream;

public class AuthFrame extends Stage {

    // ======= Cấu hình mặc định server =======
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9090;

    private boolean isLoginMode = true; // true = Login, false = Register

    // Login fields
    private final TextField tfUserLogin = new TextField();
    private final PasswordField pfPassLogin = new PasswordField();
    private final TextField tfPassLoginVisible = new TextField();
    private final CheckBox cbShowLogin = new CheckBox("Show password");
    private final Button btnLogin = new Button("Login");
    private final Button btnRegister = new Button("Register");

    // Register fields
    private final TextField tfUserReg = new TextField();
    private final PasswordField pfPassReg = new PasswordField();
    private final PasswordField pfPassReg2 = new PasswordField();
    private final TextField tfPassRegVisible = new TextField();
    private final TextField tfPassReg2Visible = new TextField();
    private final CheckBox cbShowReg = new CheckBox("Show password");

    private final TextArea taLog = new TextArea();
    private AuthClient client;

    public AuthFrame() {
        super();
        setTitle("Login / Register");
        initStyle(StageStyle.DECORATED);
        setOnCloseRequest(this::handleClose);

        // Load background image
        InputStream bgStream = getClass().getResourceAsStream("/images/background/login_background.jpg");
        
        // Smaller window size - adjust to fit image better
        double windowWidth = 900;
        double windowHeight = 675;
        
        // Main container with background
        StackPane root = new StackPane();
        
        if (bgStream != null) {
            Image bgImage = new Image(bgStream);
            ImageView backgroundImageView = new ImageView(bgImage);
            // Make background cover the entire window without borders
            backgroundImageView.setFitWidth(windowWidth);
            backgroundImageView.setFitHeight(windowHeight);
            backgroundImageView.setPreserveRatio(false); // Fill entire area
            backgroundImageView.setSmooth(true);
            root.getChildren().add(backgroundImageView);
        } else {
            // Fallback background color if image not found
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #87CEEB 0%, #98D8C8 100%);");
        }

        // Login form panel (centered)
        VBox formPanel = buildLoginForm();
        formPanel.setAlignment(Pos.CENTER);
        formPanel.setMaxWidth(320); // Form width

        // Add form to root
        root.getChildren().add(formPanel);
        root.setAlignment(Pos.CENTER);
        
        // Add sound toggle button in top right corner
        Button btnSoundToggle = createSoundToggleButton();
        StackPane.setAlignment(btnSoundToggle, Pos.TOP_RIGHT);
        StackPane.setMargin(btnSoundToggle, new Insets(15, 15, 0, 0));
        root.getChildren().add(btnSoundToggle);
        
        // Set root background to match image edges
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, windowWidth, windowHeight);
        scene.setFill(null); // Transparent scene background
        setScene(scene);
        setResizable(false); // Prevent resizing to maintain layout
        centerOnScreen();

        // Start background music
        setOnShown(e -> {
            SoundManager.getInstance().playBackgroundMusic("/sounds/notification/back_ground.wav");
        });

        // Events
        cbShowLogin.setOnAction(e -> togglePasswordVisibility(true));
        cbShowReg.setOnAction(e -> togglePasswordVisibility(false));
        btnLogin.setOnAction(e -> doLogin());
        btnRegister.setOnAction(e -> {
            if (isLoginMode) {
                doRegister();
            } else {
                switchToLogin();
            }
        });
    }
    
    private Button createSoundToggleButton() {
        Button btn = new Button();
        updateSoundButtonIcon(btn);
        
        // Base style
        String baseStyle = "-fx-background-color: rgba(255, 255, 255, 0.7); " +
                          "-fx-background-radius: 20; " +
                          "-fx-pref-width: 40; " +
                          "-fx-pref-height: 40; " +
                          "-fx-font-size: 20px; " +
                          "-fx-cursor: hand; " +
                          "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);";
        btn.setStyle(baseStyle);
        
        // Hover effect - brighter background and scale up
        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                        "-fx-background-radius: 20; " +
                        "-fx-pref-width: 40; " +
                        "-fx-pref-height: 40; " +
                        "-fx-font-size: 20px; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 3);");
            // Scale up animation
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), btn);
            scaleUp.setToX(1.15);
            scaleUp.setToY(1.15);
            scaleUp.play();
        });
        
        // Mouse exit - return to normal
        btn.setOnMouseExited(e -> {
            btn.setStyle(baseStyle);
            // Scale down animation
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), btn);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.play();
        });
        
        // Click effect - scale down then up
        btn.setOnMousePressed(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(100), btn);
            scaleDown.setToX(0.9);
            scaleDown.setToY(0.9);
            scaleDown.play();
        });
        
        btn.setOnMouseReleased(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(100), btn);
            scaleUp.setToX(1.0);
            scaleUp.setToY(1.0);
            scaleUp.play();
        });
        
        btn.setOnAction(e -> {
            SoundManager soundManager = SoundManager.getInstance();
            soundManager.setSoundEnabled(!soundManager.isSoundEnabled());
            updateSoundButtonIcon(btn);
            
            // Click animation - bounce effect
            ScaleTransition bounce = new ScaleTransition(Duration.millis(200), btn);
            bounce.setToX(0.85);
            bounce.setToY(0.85);
            bounce.setAutoReverse(true);
            bounce.setCycleCount(2);
            bounce.play();
        });
        
        return btn;
    }
    
    private void updateSoundButtonIcon(Button btn) {
        if (SoundManager.getInstance().isSoundEnabled()) {
            btn.setText("🔊");
        } else {
            btn.setText("🔇");
        }
    }

    private VBox buildLoginForm() {
        VBox form = new VBox(16);
        form.setPadding(new Insets(12, 30, 24, 30)); // Reduced top padding, keep others
        form.setAlignment(Pos.CENTER);
        
        // Semi-transparent background for form
        form.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); " +
                     "-fx-background-radius: 16; " +
                     "-fx-border-radius: 16;");

        // Title
        Label titleLabel = new Label(isLoginMode ? "Login" : "Register");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28)); // Larger title
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);");

        // Input fields container
        VBox fieldsContainer = new VBox(14);
        fieldsContainer.setAlignment(Pos.CENTER);
        fieldsContainer.setMaxWidth(280);

        if (isLoginMode) {
            // Username field
            Label userLabel = new Label("User name");
            userLabel.setFont(Font.font("Arial", 12)); // Larger font
            userLabel.setTextFill(Color.WHITE);
            userLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);");
            
            tfUserLogin.setPrefHeight(36); // Larger height
            tfUserLogin.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                                "-fx-font-size: 13px; -fx-padding: 0 12;");

            // Password field
            Label passLabel = new Label("Password");
            passLabel.setFont(Font.font("Arial", 12)); // Larger font
            passLabel.setTextFill(Color.WHITE);
            passLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);");
            
            VBox passwordBox = new VBox(4);
            pfPassLogin.setPrefHeight(36); // Larger height
            pfPassLogin.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                               "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassLoginVisible.setPrefHeight(36); // Larger height
            tfPassLoginVisible.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                                      "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassLoginVisible.setVisible(false);
            passwordBox.getChildren().addAll(pfPassLogin, tfPassLoginVisible);

            // Show password checkbox
            cbShowLogin.setTextFill(Color.WHITE);
            cbShowLogin.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);");

            fieldsContainer.getChildren().addAll(
                userLabel, tfUserLogin,
                passLabel, passwordBox,
                cbShowLogin
            );
        } else {
            // Register mode
            Label userLabel = new Label("User name");
            userLabel.setFont(Font.font("Arial", 12)); // Larger font
            userLabel.setTextFill(Color.WHITE);
            userLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);");
            
            tfUserReg.setPrefHeight(36); // Larger height
            tfUserReg.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                             "-fx-font-size: 13px; -fx-padding: 0 12;");

            Label passLabel = new Label("Password");
            passLabel.setFont(Font.font("Arial", 12)); // Larger font
            passLabel.setTextFill(Color.WHITE);
            passLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);");
            
            VBox passwordBox1 = new VBox(4);
            pfPassReg.setPrefHeight(36); // Larger height
            pfPassReg.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                             "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassRegVisible.setPrefHeight(36); // Larger height
            tfPassRegVisible.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                                    "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassRegVisible.setVisible(false);
            passwordBox1.getChildren().addAll(pfPassReg, tfPassRegVisible);

            Label passLabel2 = new Label("Confirm password");
            passLabel2.setFont(Font.font("Arial", 12)); // Larger font
            passLabel2.setTextFill(Color.WHITE);
            passLabel2.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);");
            
            VBox passwordBox2 = new VBox(4);
            pfPassReg2.setPrefHeight(36); // Larger height
            pfPassReg2.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                              "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassReg2Visible.setPrefHeight(36); // Larger height
            tfPassReg2Visible.setStyle("-fx-background-radius: 18; -fx-background-color: white; " +
                                     "-fx-font-size: 13px; -fx-padding: 0 12;");
            tfPassReg2Visible.setVisible(false);
            passwordBox2.getChildren().addAll(pfPassReg2, tfPassReg2Visible);

            cbShowReg.setTextFill(Color.WHITE);
            cbShowReg.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);");

            fieldsContainer.getChildren().addAll(
                userLabel, tfUserReg,
                passLabel, passwordBox1,
                passLabel2, passwordBox2,
                cbShowReg
            );
        }

        // Buttons container
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        // Register button (left)
        btnRegister.setPrefSize(110, 38); // Larger buttons
        btnRegister.setStyle("-fx-background-color: #2d5016; " +
                           "-fx-background-radius: 19; " +
                           "-fx-text-fill: white; " +
                           "-fx-font-size: 14px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-cursor: hand;");
        btnRegister.setOnMouseEntered(e -> btnRegister.setStyle(
            "-fx-background-color: #3a6b1f; " +
            "-fx-background-radius: 19; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"));
        btnRegister.setOnMouseExited(e -> btnRegister.setStyle(
            "-fx-background-color: #2d5016; " +
            "-fx-background-radius: 19; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"));

        // Login button (right)
        btnLogin.setPrefSize(110, 38); // Larger buttons
        btnLogin.setStyle("-fx-background-color: #2d5016; " +
                        "-fx-background-radius: 19; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-cursor: hand;");
        btnLogin.setOnMouseEntered(e -> btnLogin.setStyle(
            "-fx-background-color: #3a6b1f; " +
            "-fx-background-radius: 19; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"));
        btnLogin.setOnMouseExited(e -> btnLogin.setStyle(
            "-fx-background-color: #2d5016; " +
            "-fx-background-radius: 19; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"));

        if (isLoginMode) {
            buttonBox.getChildren().addAll(btnRegister, btnLogin);
        } else {
            // In register mode, show "Back to Login" and "Register" buttons
            Button btnBack = new Button("Back to Login");
            btnBack.setPrefSize(110, 38); // Larger buttons
            btnBack.setStyle("-fx-background-color: #2d5016; " +
                           "-fx-background-radius: 19; " +
                           "-fx-text-fill: white; " +
                           "-fx-font-size: 14px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-cursor: hand;");
            btnBack.setOnMouseEntered(e -> btnBack.setStyle(
                "-fx-background-color: #3a6b1f; " +
                "-fx-background-radius: 19; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand;"));
            btnBack.setOnMouseExited(e -> btnBack.setStyle(
                "-fx-background-color: #2d5016; " +
                "-fx-background-radius: 19; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand;"));
            btnBack.setOnAction(e -> switchToLogin());
            buttonBox.getChildren().addAll(btnBack, btnRegister);
        }

        form.getChildren().addAll(titleLabel, fieldsContainer, buttonBox);

        return form;
    }

    private void switchToLogin() {
        isLoginMode = true;
        StackPane root = (StackPane) getScene().getRoot();
        root.getChildren().removeIf(node -> node instanceof VBox);
        VBox newForm = buildLoginForm();
        newForm.setAlignment(Pos.CENTER);
        newForm.setMaxWidth(320); // Form width
        root.getChildren().add(newForm);
        
        // Update button actions
        btnRegister.setOnAction(e -> switchToRegister());
    }

    private void switchToRegister() {
        isLoginMode = false;
        StackPane root = (StackPane) getScene().getRoot();
        root.getChildren().removeIf(node -> node instanceof VBox);
        VBox newForm = buildLoginForm();
        newForm.setAlignment(Pos.CENTER);
        newForm.setMaxWidth(320); // Form width
        root.getChildren().add(newForm);
        
        // Update button actions
        btnRegister.setOnAction(e -> doRegister());
    }

    private void togglePasswordVisibility(boolean isLogin) {
        if (isLogin) {
            boolean show = cbShowLogin.isSelected();
            if (show) {
                tfPassLoginVisible.setText(pfPassLogin.getText());
                tfPassLoginVisible.setVisible(true);
                pfPassLogin.setVisible(false);
            } else {
                pfPassLogin.setText(tfPassLoginVisible.getText());
                pfPassLogin.setVisible(true);
                tfPassLoginVisible.setVisible(false);
            }
        } else {
            boolean show = cbShowReg.isSelected();
            if (show) {
                tfPassRegVisible.setText(pfPassReg.getText());
                tfPassReg2Visible.setText(pfPassReg2.getText());
                tfPassRegVisible.setVisible(true);
                tfPassReg2Visible.setVisible(true);
                pfPassReg.setVisible(false);
                pfPassReg2.setVisible(false);
            } else {
                pfPassReg.setText(tfPassRegVisible.getText());
                pfPassReg2.setText(tfPassReg2Visible.getText());
                pfPassReg.setVisible(true);
                pfPassReg2.setVisible(true);
                tfPassRegVisible.setVisible(false);
                tfPassReg2Visible.setVisible(false);
            }
        }
    }

    private boolean ensureClient(boolean showDialog) {
        try {
            closeClientQuiet();
            client = new AuthClient(DEFAULT_HOST, DEFAULT_PORT);
            client.connect();
            log("Connected to " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            // Removed "Connection OK" dialog - no need to show
            return true;
        } catch (Exception ex) {
            log("Connect failed: " + ex.getMessage());
            if (showDialog) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi kết nối");
                    alert.setHeaderText("Không thể kết nối đến server: " + ex.getMessage());
                    alert.showAndWait();
                });
            }
            return false;
        }
    }

    private void doLogin() {
        final String user = tfUserLogin.getText().trim();
        final String pass = cbShowLogin.isSelected() ? tfPassLoginVisible.getText() : pfPassLogin.getText();

        if (!validateCreds(user, pass)) return;
        if (!ensureClient(true)) return;

        Task<String> loginTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                String cmd = "LOGIN;" + user + ";" + pass;
                String resp = client.sendCommand(cmd);
                log("> " + cmd);
                log("< " + resp);
                return resp;
            }
        };

        loginTask.setOnSucceeded(e -> {
            String resp = loginTask.getValue();
            String r = (resp == null) ? "" : resp.trim();
            boolean ok = r.toUpperCase().startsWith("LOGIN_OK");
            if (ok) {
                // Login successful - go directly to lobby, no alert
                final String hostVal = DEFAULT_HOST;
                final int portVal = DEFAULT_PORT;
                final String userVal = user;

                Platform.runLater(() -> {
                    // Don't close client here - LobbyFrame needs it
                    // Just create lobby and close auth frame
                    new LobbyFrame(userVal, hostVal, portVal, client).show();
                    // Don't set client = null here, let LobbyFrame manage it
                    close();
                });
            } else {
                // Login failed - show error message
                String errorMsg = "Sai tài khoản hoặc mật khẩu";
                if (resp != null && resp.toUpperCase().contains("ERROR")) {
                    if (resp.contains("Invalid") || resp.contains("sai")) {
                        errorMsg = "Sai tài khoản hoặc mật khẩu";
                    } else {
                        errorMsg = resp;
                    }
                }
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Đăng nhập thất bại");
                alert.setHeaderText(errorMsg);
                alert.showAndWait();
            }
        });

        loginTask.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Login failed: " + loginTask.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(loginTask).start();
    }

    private void doRegister() {
        String user = tfUserReg.getText().trim();
        String pass1 = cbShowReg.isSelected() ? tfPassRegVisible.getText() : pfPassReg.getText();
        String pass2 = cbShowReg.isSelected() ? tfPassReg2Visible.getText() : pfPassReg2.getText();

        if (!validateCreds(user, pass1)) return;
        if (!pass1.equals(pass2)) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Invalid input");
            alert.setHeaderText("Passwords do not match.");
            alert.showAndWait();
            return;
        }
        if (!ensureClient(true)) return;

        Task<String> registerTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                String cmd = "REGISTER;" + user + ";" + pass1;
                String resp = client.sendCommand(cmd);
                log("> " + cmd);
                log("< " + resp);
                return resp;
            }
        };

        registerTask.setOnSucceeded(e -> {
            String resp = registerTask.getValue();
            if (resp != null && resp.toUpperCase().contains("REGISTER_OK")) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Register");
                alert.setHeaderText("Register successful. You can login now.");
                alert.showAndWait();
                switchToLogin();
                tfUserLogin.setText(user);
                if (cbShowLogin.isSelected()) {
                    tfPassLoginVisible.setText(pass1);
                } else {
                    pfPassLogin.setText(pass1);
                }
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Register failed");
                alert.setHeaderText(resp == null ? "No response" : resp);
                alert.showAndWait();
            }
        });

        registerTask.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Register failed: " + registerTask.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(registerTask).start();
    }

    private boolean validateCreds(String user, String pass) {
        if (user.isEmpty() || pass.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Missing data");
            alert.setHeaderText("Please enter username and password.");
            alert.showAndWait();
            return false;
        }
        if (user.contains(";") || pass.contains(";")) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Invalid character");
            alert.setHeaderText("Do not use ';' in username/password.");
            alert.showAndWait();
            return false;
        }
        if (pass.length() < 6) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Weak password");
            alert.setHeaderText("Password should be at least 6 characters.");
            alert.showAndWait();
            return false;
        }
        return true;
    }

    private void log(String s) {
        Platform.runLater(() -> {
            if (taLog != null) {
                taLog.appendText(s + "\n");
            }
        });
    }

    private void handleClose(WindowEvent e) {
        // Stop background music when closing
        SoundManager.getInstance().stopBackgroundMusic();
        closeClientQuiet();
    }

    private void closeClientQuiet() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }
    }
}
