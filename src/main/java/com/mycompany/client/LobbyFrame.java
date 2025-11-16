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
        
        ImageView backgroundImageView = null;
        if (bgStream != null) {
            Image bgImage = new Image(bgStream);
            backgroundImageView = new ImageView(bgImage);
            backgroundImageView.setPreserveRatio(false);
            backgroundImageView.setSmooth(true);
            root.getChildren().add(backgroundImageView);
        } else {
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #87CEEB 0%, #98D8C8 100%);");
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
        
        // Bind background image size to scene size for dynamic resizing
        if (backgroundImageView != null) {
            backgroundImageView.fitWidthProperty().bind(scene.widthProperty());
            backgroundImageView.fitHeightProperty().bind(scene.heightProperty());
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
        btnPractice.setOnAction(e -> new PracticeFrame().show());

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
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Cảnh báo");
                alert.setHeaderText("Vui lòng chọn một người chơi để thách đấu.");
                alert.showAndWait();
                return;
            }
            if (selected.equals(username)) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Cảnh báo");
                alert.setHeaderText("Bạn không thể thách đấu chính mình.");
                alert.showAndWait();
                return;
            }
            dialog.close();
            sendInvite(selected);
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
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Thách đấu");
                        alert.setHeaderText(from + " thách đấu bạn. Chấp nhận?");
                        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                        alert.showAndWait().ifPresent(result -> {
                            respondInvite(from, result == ButtonType.YES);
                        });
                    });
                } else if (up.startsWith("INVITE_RESULT;")) {
                    String[] p = resp.split(";", 3);
                    String who = (p.length > 1) ? p[1] : "?";
                    String result = (p.length > 2) ? p[2] : "";
                    event("Lời thách đấu của bạn đến " + who + ": " + result);
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
    private void sendInvite(String to) {
        if (to == null || to.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("Vui lòng chọn một người chơi để thách đấu.");
            alert.showAndWait();
            return;
        }
        if (to.equals(username)) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("Bạn không thể thách đấu chính mình.");
            alert.showAndWait();
            return;
        }
        if (client == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("Không kết nối được.");
            alert.showAndWait();
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
                event("Bạn đã gửi lời thách đấu đến " + to + ".");
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Thành công");
                alert.setHeaderText("Đã gửi lời thách đấu đến " + to);
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi");
                alert.setHeaderText("Gửi lời thách đấu thất bại: " + (resp == null ? "Không có phản hồi" : resp));
                alert.showAndWait();
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
