package com.mycompany.client;

import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LobbyFrame extends Stage {
    private final String username;
    private final String host;
    private final int port;
    private AuthClient client;    

    // Online users list (excluding self)
    private final ListView<String> lstOnline = new ListView<>();
    
    // Invites / Events log
    private final TextArea taEvent = new TextArea();

    private ScheduledService<Void> whoService;
    private ScheduledService<String> eventService;

    public LobbyFrame(String username, String host, int port, AuthClient loggedInClient) {
        super();
        this.username = username;
        this.host = host;
        this.port = port;
        this.client = loggedInClient;

        setTitle("Lobby");
        setResizable(true); // Allow resizing from all sides
        setOnCloseRequest(this::handleClose);

        // Load background image
        InputStream bgStream = getClass().getResourceAsStream("/images/background/lobby_background.jpg");
        
        // Initial window size
        double windowWidth = 900;
        double windowHeight = 675;
        
        // Main container with background
        StackPane root = new StackPane();
        
        ImageView backgroundImageView;
        Image bgImage;
        if (bgStream != null) {
            bgImage = new Image(bgStream);
            backgroundImageView = new ImageView(bgImage);
            backgroundImageView.setPreserveRatio(true); // Keep aspect ratio
            backgroundImageView.setSmooth(true);
            // Set initial size
            backgroundImageView.setFitWidth(windowWidth);
            backgroundImageView.setFitHeight(windowHeight);
            root.getChildren().add(backgroundImageView);
        } else {
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #87CEEB 0%, #98D8C8 100%);");
            backgroundImageView = null;
            bgImage = null;
        }

        // Menu overlay in the center
        VBox menuPanel = buildMenuPanel();
        menuPanel.setAlignment(Pos.CENTER);
        menuPanel.setPadding(new Insets(20));

        root.getChildren().add(menuPanel);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, windowWidth, windowHeight);
        scene.setFill(null);
        setScene(scene);
        
        // Bind background image size to scene size, keeping aspect ratio
        // Image will scale to cover the area, parts may be cropped
        final ImageView finalImageView = backgroundImageView;
        final Image finalImage = bgImage;
        if (finalImageView != null && finalImage != null) {
            // Use listener to calculate size that covers the area while maintaining aspect ratio
            scene.widthProperty().addListener((obs, oldVal, newVal) -> updateImageSize(finalImageView, finalImage, scene));
            scene.heightProperty().addListener((obs, oldVal, newVal) -> updateImageSize(finalImageView, finalImage, scene));
            updateImageSize(finalImageView, finalImage, scene);
        }
        
        centerOnScreen();

        // Start polling
        setOnShown(e -> {
            startPolling();
            refreshOnline();
        });
    }

    private VBox buildMenuPanel() {
        VBox menu = new VBox(15);
        menu.setAlignment(Pos.CENTER);
        menu.setMaxWidth(300);
        menu.setPadding(new Insets(20));

        // Button style
        String buttonStyle = "-fx-background-color: rgba(220, 220, 220, 0.9); " +
                           "-fx-background-radius: 15; " +
                           "-fx-text-fill: black; " +
                           "-fx-font-size: 16px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-pref-width: 250; " +
                           "-fx-pref-height: 50; " +
                           "-fx-cursor: hand;";
        
        String buttonHoverStyle = "-fx-background-color: rgba(240, 240, 240, 0.95); " +
                                 "-fx-background-radius: 15; " +
                                 "-fx-text-fill: black; " +
                                 "-fx-font-size: 16px; " +
                                 "-fx-font-weight: bold; " +
                                 "-fx-pref-width: 250; " +
                                 "-fx-pref-height: 50; " +
                                 "-fx-cursor: hand;";

        // Thi đấu đối kháng
        Button btnCompetitive = new Button("Thi đấu đối kháng");
        btnCompetitive.setStyle(buttonStyle);
        btnCompetitive.setOnMouseEntered(e -> btnCompetitive.setStyle(buttonHoverStyle));
        btnCompetitive.setOnMouseExited(e -> btnCompetitive.setStyle(buttonStyle));
        btnCompetitive.setOnAction(e -> showCompetitiveMatchDialog());

        // Luyện tập
        Button btnPractice = new Button("Luyện tập");
        btnPractice.setStyle(buttonStyle);
        btnPractice.setOnMouseEntered(e -> btnPractice.setStyle(buttonHoverStyle));
        btnPractice.setOnMouseExited(e -> btnPractice.setStyle(buttonStyle));
        btnPractice.setOnAction(e -> {
            // Hide lobby when opening practice (don't close, just hide)
            hide();
            // Pass reference to this lobby window
            PracticeFrame practiceFrame = new PracticeFrame(this);
            practiceFrame.show();
        });

        // Bảng xếp hạng
        Button btnLeaderboard = new Button("Bảng xếp hạng");
        btnLeaderboard.setStyle(buttonStyle);
        btnLeaderboard.setOnMouseEntered(e -> btnLeaderboard.setStyle(buttonHoverStyle));
        btnLeaderboard.setOnMouseExited(e -> btnLeaderboard.setStyle(buttonStyle));
        btnLeaderboard.setOnAction(e -> new LeaderboardFrame().show());

        // Lịch sử đấu
        Button btnHistory = new Button("Lịch sử đấu");
        btnHistory.setStyle(buttonStyle);
        btnHistory.setOnMouseEntered(e -> btnHistory.setStyle(buttonHoverStyle));
        btnHistory.setOnMouseExited(e -> btnHistory.setStyle(buttonStyle));
        btnHistory.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Lịch sử đấu");
            alert.setHeaderText("Chức năng đang phát triển...");
            centerAlertOnLobby(alert);
            alert.showAndWait();
        });

        // Thoát game
        Button btnExit = new Button("Thoát game");
        btnExit.setStyle(buttonStyle);
        btnExit.setOnMouseEntered(e -> btnExit.setStyle(buttonHoverStyle));
        btnExit.setOnMouseExited(e -> btnExit.setStyle(buttonStyle));
        btnExit.setOnAction(e -> doLogout());

        menu.getChildren().addAll(
            btnCompetitive,
            btnPractice,
            btnLeaderboard,
            btnHistory,
            btnExit
        );

        return menu;
    }

    private void updateImageSize(ImageView imageView, Image image, Scene scene) {
        double sceneWidth = scene.getWidth();
        double sceneHeight = scene.getHeight();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        
        double scaleX = sceneWidth / imageWidth;
        double scaleY = sceneHeight / imageHeight;
        // Use the larger scale to ensure coverage (may crop)
        double scale = Math.max(scaleX, scaleY);
        
        imageView.setFitWidth(imageWidth * scale);
        imageView.setFitHeight(imageHeight * scale);
    }

    private void centerAlertOnLobby(Alert alert) {
        alert.setOnShown(evt -> {
            Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            double alertWidth = alertStage.getWidth();
            double alertHeight = alertStage.getHeight();
            double lobbyX = this.getX();
            double lobbyY = this.getY();
            double lobbyWidth = this.getWidth();
            double lobbyHeight = this.getHeight();
            
            alertStage.setX(lobbyX + (lobbyWidth - alertWidth) / 2);
            alertStage.setY(lobbyY + (lobbyHeight - alertHeight) / 2);
        });
    }

    private void showCustomAlert(String title, String message, Alert.AlertType type) {
        // Create custom dialog with rounded corners
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.setTitle(title);
        dialog.setResizable(false);
        dialog.initStyle(StageStyle.TRANSPARENT); // For rounded corners

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; " +
                     "-fx-background-radius: 20; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 14px; " +
                            "-fx-text-fill: #333; " +
                            "-fx-alignment: center;");
        messageLabel.setMaxWidth(350);

        Button okButton = new Button("OK");
        okButton.setStyle("-fx-background-color: #2d5016; " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 100; " +
                         "-fx-pref-height: 35; " +
                         "-fx-cursor: hand;");
        okButton.setOnMouseEntered(e -> okButton.setStyle(
            "-fx-background-color: #3a6b1f; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        okButton.setOnMouseExited(e -> okButton.setStyle(
            "-fx-background-color: #2d5016; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        okButton.setOnAction(e -> dialog.close());

        root.getChildren().addAll(messageLabel, okButton);

        Scene scene = new Scene(root);
        scene.setFill(null); // Transparent background
        dialog.setScene(scene);

        // Center dialog relative to lobby window
        dialog.setOnShown(evt -> {
            Stage dialogStage = (Stage) dialog.getScene().getWindow();
            double dialogWidth = dialogStage.getWidth();
            double dialogHeight = dialogStage.getHeight();
            double lobbyX = this.getX();
            double lobbyY = this.getY();
            double lobbyWidth = this.getWidth();
            double lobbyHeight = this.getHeight();
            
            dialogStage.setX(lobbyX + (lobbyWidth - dialogWidth) / 2);
            dialogStage.setY(lobbyY + (lobbyHeight - dialogHeight) / 2);
        });

        dialog.showAndWait();
    }

    private void showChallengeDialog(String opponent) {
        // Create custom dialog with rounded corners for challenge notification
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.setTitle("Thách đấu");
        dialog.setResizable(false);
        dialog.initStyle(StageStyle.TRANSPARENT); // For rounded corners

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; " +
                     "-fx-background-radius: 20; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);");

        Label messageLabel = new Label(opponent + " thách đấu bạn. Chấp nhận?");
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 15px; " +
                            "-fx-text-fill: #333; " +
                            "-fx-font-weight: bold; " +
                            "-fx-alignment: center;");
        messageLabel.setMaxWidth(350);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button yesButton = new Button("Có");
        yesButton.setStyle("-fx-background-color: #2d5016; " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 100; " +
                         "-fx-pref-height: 35; " +
                         "-fx-cursor: hand;");
        yesButton.setOnMouseEntered(e -> yesButton.setStyle(
            "-fx-background-color: #3a6b1f; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        yesButton.setOnMouseExited(e -> yesButton.setStyle(
            "-fx-background-color: #2d5016; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        yesButton.setOnAction(e -> {
            dialog.close();
            respondInvite(opponent, true);
        });

        Button noButton = new Button("Không");
        noButton.setStyle("-fx-background-color: #666; " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 100; " +
                         "-fx-pref-height: 35; " +
                         "-fx-cursor: hand;");
        noButton.setOnMouseEntered(e -> noButton.setStyle(
            "-fx-background-color: #777; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        noButton.setOnMouseExited(e -> noButton.setStyle(
            "-fx-background-color: #666; " +
            "-fx-background-radius: 15; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-pref-width: 100; " +
            "-fx-pref-height: 35; " +
            "-fx-cursor: hand;"));
        noButton.setOnAction(e -> {
            dialog.close();
            respondInvite(opponent, false);
        });

        buttonBox.getChildren().addAll(yesButton, noButton);
        root.getChildren().addAll(messageLabel, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(null); // Transparent background
        dialog.setScene(scene);

        // Center dialog relative to lobby window
        dialog.setOnShown(evt -> {
            Stage dialogStage = (Stage) dialog.getScene().getWindow();
            double dialogWidth = dialogStage.getWidth();
            double dialogHeight = dialogStage.getHeight();
            double lobbyX = this.getX();
            double lobbyY = this.getY();
            double lobbyWidth = this.getWidth();
            double lobbyHeight = this.getHeight();
            
            dialogStage.setX(lobbyX + (lobbyWidth - dialogWidth) / 2);
            dialogStage.setY(lobbyY + (lobbyHeight - dialogHeight) / 2);
        });

        dialog.showAndWait();
    }

    private void showCompetitiveMatchDialog() {
        // Refresh online list first
        refreshOnline();
        
        // Create dialog to select opponent
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.setTitle("Chọn đối thủ");
        dialog.setResizable(false);

        VBox dialogRoot = new VBox(15);
        dialogRoot.setPadding(new Insets(20));
        dialogRoot.setStyle("-fx-background-color: white;");

        Label titleLabel = new Label("Chọn người chơi để thách đấu:");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        // List of online users (excluding self)
        ListView<String> dialogList = new ListView<>();
        dialogList.setPrefHeight(300);
        dialogList.setPrefWidth(300);
        dialogList.setItems(lstOnline.getItems()); // Use same items as main list

        Button btnChallenge = new Button("Thách đấu");
        btnChallenge.setStyle("-fx-background-color: #2d5016; " +
                            "-fx-background-radius: 10; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 14px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-pref-width: 150; " +
                            "-fx-pref-height: 35; " +
                            "-fx-cursor: hand;");
        btnChallenge.setOnAction(e -> {
            String selected = dialogList.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isEmpty()) {
                showCustomAlert("Cảnh báo", "Vui lòng chọn một người chơi để thách đấu.", Alert.AlertType.WARNING);
                return;
            }
            if (selected.equals(username)) {
                showCustomAlert("Cảnh báo", "Bạn không thể thách đấu chính mình.", Alert.AlertType.WARNING);
                return;
            }
            // Don't close dialog immediately - wait for invite result
            sendInvite(selected, dialog);
        });

        Button btnCancel = new Button("Hủy");
        btnCancel.setStyle("-fx-background-color: #666; " +
                         "-fx-background-radius: 10; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 150; " +
                         "-fx-pref-height: 35; " +
                         "-fx-cursor: hand;");
        btnCancel.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(btnChallenge, btnCancel);

        dialogRoot.getChildren().addAll(titleLabel, new ScrollPane(dialogList), buttonBox);

        Scene dialogScene = new Scene(dialogRoot, 350, 450);
        dialog.setScene(dialogScene);
        
        // Center dialog relative to lobby window, not screen
        double dialogWidth = 350;
        double dialogHeight = 450;
        double lobbyX = this.getX();
        double lobbyY = this.getY();
        double lobbyWidth = this.getWidth();
        double lobbyHeight = this.getHeight();
        
        dialog.setX(lobbyX + (lobbyWidth - dialogWidth) / 2);
        dialog.setY(lobbyY + (lobbyHeight - dialogHeight) / 2);
        
        dialog.showAndWait();
    }

    /* ================== Poll timers ================== */
    private void startPolling() {
        if (whoService == null) {
            whoService = new ScheduledService<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() {
                            if (client == null) return null;
                            try {
                                String resp = client.sendCommand("WHO");
                                Platform.runLater(() -> {
                                    if (resp != null && resp.toUpperCase().startsWith("ONLINE;")) {
                    String list = resp.substring("ONLINE;".length());
                    List<String> users = Arrays.stream(list.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                                                .filter(s -> !s.equals(username)) // Exclude self
                            .collect(Collectors.toList());
                                        lstOnline.getItems().clear();
                                        lstOnline.getItems().addAll(users);
                                    }
                                });
                            } catch (Exception ex) {
                                // Silent error handling
                            }
                return null;
            }
                    };
                }
            };
            whoService.setPeriod(Duration.seconds(5));
            whoService.setDelay(Duration.millis(200));
        }

        if (eventService == null) {
            eventService = new ScheduledService<String>() {
                @Override
                protected Task<String> createTask() {
                    return new Task<String>() {
                        @Override
                        protected String call() {
                            if (client == null) return null;
                            try {
                                return client.sendCommand("POLL");
                            } catch (Exception ex) {
                return null;
            }
                        }
                    };
                }
            };
            eventService.setPeriod(Duration.seconds(1));
            eventService.setDelay(Duration.millis(500));
            eventService.setOnSucceeded(e -> {
                String resp = eventService.getValue();
                if (resp == null || resp.equalsIgnoreCase("NO_EVENT")) return;

                String up = resp.toUpperCase();
                if (up.startsWith("INVITE_FROM;")) {
                    String from = resp.substring("INVITE_FROM;".length());
                    Platform.runLater(() -> {
                        showChallengeDialog(from);
                    });
                } else if (up.startsWith("INVITE_RESULT;")) {
                    String[] p = resp.split(";", 3);
                    String who = (p.length > 1) ? p[1] : "?";
                    String result = (p.length > 2) ? p[2] : "";
                    event("Lời thách đấu của bạn đến " + who + ": " + result);
                    
                    // Only show alert if rejected
                    if (result.toUpperCase().contains("REJECT") || result.toUpperCase().contains("TỪ CHỐI")) {
                        Platform.runLater(() -> {
                            showCustomAlert("Thông báo", who + " đã từ chối lời thách đấu của bạn.", Alert.AlertType.WARNING);
                        });
                    }
                } else if (up.startsWith("START_MATCH;")) {
                    String opp = resp.substring("START_MATCH;".length());
                    event("Trận đấu bắt đầu với " + opp + "!");
                    Platform.runLater(() -> {
                        new GameFrame(username, opp, client).show();
                        close();
                    });
                }
            });
        }

        if (!whoService.isRunning()) whoService.start();
        if (!eventService.isRunning()) eventService.start();
    }

    private void stopPolling() {
        if (whoService != null) whoService.cancel();
        if (eventService != null) eventService.cancel();
    }

    /* ================== WHO (online list) ================== */
    private void refreshOnline() {
        if (client == null) return;
        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                try {
                    return client.sendCommand("WHO");
                } catch (Exception ex) {
                    return "ERROR:" + ex.getMessage();
                }
            }
        };

        task.setOnSucceeded(e -> {
            String resp = task.getValue();
            if (resp == null) return;
            if (resp.toUpperCase().startsWith("ONLINE;")) {
                String list = resp.substring("ONLINE;".length());
                List<String> users = Arrays.stream(list.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .filter(s -> !s.equals(username)) // Exclude self
                        .collect(Collectors.toList());
                lstOnline.getItems().clear();
                lstOnline.getItems().addAll(users);
            }
        });

        new Thread(task).start();
    }

    /* ================== Invite / Challenge ================== */
    private void sendInvite(String to, Stage dialogToClose) {
        if (to == null || to.isEmpty()) {
            showCustomAlert("Cảnh báo", "Vui lòng chọn một người chơi để thách đấu.", Alert.AlertType.WARNING);
            return;
        }
        if (to.equals(username)) {
            showCustomAlert("Cảnh báo", "Bạn không thể thách đấu chính mình.", Alert.AlertType.WARNING);
            return;
        }
        if (client == null) {
            showCustomAlert("Lỗi", "Không kết nối được.", Alert.AlertType.ERROR);
            if (dialogToClose != null) dialogToClose.close();
            return;
        }

        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                try {
                    return client.sendCommand("INVITE;" + to);
                } catch (Exception ex) {
                    return "ERROR:" + ex.getMessage();
                }
            }
        };

        task.setOnSucceeded(e -> {
            String resp = task.getValue();
            if (resp != null && resp.startsWith("INVITE_SENT")) {
                // No alert when invite is sent successfully - wait for response
                event("Bạn đã gửi lời thách đấu đến " + to + ".");
                // Don't close dialog - let user send more invites if needed
            } else {
                // Only show error if sending failed
                showCustomAlert("Lỗi", "Gửi lời thách đấu thất bại: " + (resp == null ? "Không có phản hồi" : resp), Alert.AlertType.ERROR);
                // Don't close dialog on error, let user try again
            }
        });

        new Thread(task).start();
    }

    private void respondInvite(String opponent, boolean accept) {
        if (client == null) return;
        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                try {
                    return client.sendCommand("RESPOND;" + opponent + ";" + (accept ? "ACCEPT" : "REJECT"));
                } catch (Exception ex) {
                    return "ERROR:" + ex.getMessage();
                }
            }
        };

        task.setOnSucceeded(e -> {
            String resp = task.getValue();
                if (resp != null && resp.startsWith("RESPOND_OK")) {
                event("Bạn đã " + (accept ? "chấp nhận" : "từ chối") + " lời thách đấu từ " + opponent + ".");
                } else {
                event("Phản hồi thất bại: " + (resp == null ? "Không có phản hồi" : resp));
            }
        });

        new Thread(task).start();
    }

    private void doLogout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận");
        confirmAlert.setHeaderText("Bạn có chắc muốn thoát game?");
        confirmAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        centerAlertOnLobby(confirmAlert);
        confirmAlert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.YES) {
                stopPolling();
                try {
                    if (client != null) client.sendCommand("LOGOUT");
                } catch (Exception ignored) {
                }
                closeClientQuiet();
                Platform.runLater(() -> new AuthFrame().show());
                close();
            }
        });
    }

    private void event(String s) {
        Platform.runLater(() -> {
            if (taEvent != null) {
                taEvent.appendText(s + "\n");
            }
        });
    }

    private void handleClose(WindowEvent e) {
        stopPolling();
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
