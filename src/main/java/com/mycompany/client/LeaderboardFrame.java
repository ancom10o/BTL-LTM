package com.mycompany.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.ScaleTransition;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardFrame extends Stage {
    private AuthClient client;
    private String username;
    private TableView<LeaderboardData> tableView;
    private ObservableList<LeaderboardData> dataList;
    private HBox userInfoBox;
    private Label userInfoLabel;

    public LeaderboardFrame(AuthClient client, String username) {
        super();
        this.client = client;
        this.username = username;
        this.dataList = FXCollections.observableArrayList();
        
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT); // No title bar
        setResizable(false);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: white; " +
                     "-fx-background-radius: 20; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 5);");

        // Title
        Label titleLabel = new Label("Bảng xếp hạng");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.BLACK);
        titleLabel.setStyle("-fx-padding: 0 0 10 0;");

        // User info panel (will be updated after loading)
        userInfoBox = new HBox(15);
        userInfoBox.setAlignment(Pos.CENTER);
        userInfoBox.setPadding(new Insets(10));
        userInfoBox.setStyle("-fx-background-color: #e3f2fd; " +
                            "-fx-background-radius: 10; " +
                            "-fx-border-radius: 10; " +
                            "-fx-border-color: #1976d2; " +
                            "-fx-border-width: 2;");
        userInfoLabel = new Label("Đang tải thông tin...");
        userInfoLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        userInfoLabel.setTextFill(Color.web("#1976d2"));
        userInfoBox.getChildren().add(userInfoLabel);
        userInfoBox.setVisible(false); // Hide until data is loaded

        // Create TableView
        tableView = createTableView();
        
        Label loadingLabel = new Label("Đang tải...");
        loadingLabel.setFont(Font.font("Arial", 14));
        loadingLabel.setTextFill(Color.GRAY);

        VBox contentBox = new VBox(15);
        contentBox.getChildren().addAll(userInfoBox, loadingLabel, tableView);

        root.getChildren().addAll(titleLabel, contentBox);

        // Close button with hover effect
        Button btnClose = createCloseButton();
        btnClose.setOnAction(e -> close());

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().add(btnClose);
        root.getChildren().add(buttonBox);

        Scene scene = new Scene(root, 650, 550);
        scene.setFill(null); // Transparent scene
        setScene(scene);

        show();

        // Load leaderboard data
        loadLeaderboard(loadingLabel);
    }

    private TableView<LeaderboardData> createTableView() {
        TableView<LeaderboardData> table = new TableView<>();
        table.setPrefHeight(400);
        table.setPrefWidth(590);
        table.setStyle("-fx-background-color: #fafafa; " +
                      "-fx-background-radius: 12; " +
                      "-fx-border-radius: 12; " +
                      "-fx-border-color: #d0d0d0; " +
                      "-fx-border-width: 2;");

        // Rank column
        TableColumn<LeaderboardData, Integer> rankCol = new TableColumn<>("Hạng");
        rankCol.setPrefWidth(80);
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        rankCol.setStyle("-fx-alignment: CENTER;");
        rankCol.setCellFactory(column -> new TableCell<LeaderboardData, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    setFont(Font.font("Arial", FontWeight.BOLD, 14));
                    // Color top 3
                    if (item == 1) {
                        setTextFill(Color.web("#FFD700")); // Gold
                        setStyle("-fx-background-color: #fff9e6;");
                    } else if (item == 2) {
                        setTextFill(Color.web("#C0C0C0")); // Silver
                        setStyle("-fx-background-color: #f5f5f5;");
                    } else if (item == 3) {
                        setTextFill(Color.web("#CD7F32")); // Bronze
                        setStyle("-fx-background-color: #faf5f0;");
                    } else {
                        setTextFill(Color.BLACK);
                        setStyle("");
                    }
                }
            }
        });

        // Username column
        TableColumn<LeaderboardData, String> usernameCol = new TableColumn<>("Tên người chơi");
        usernameCol.setPrefWidth(250);
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        usernameCol.setCellFactory(column -> new TableCell<LeaderboardData, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setFont(Font.font("Arial", FontWeight.NORMAL, 13));
                    // Highlight current user
                    if (item.equals(username)) {
                        setStyle("-fx-background-color: #e3f2fd; -fx-font-weight: bold;");
                        setTextFill(Color.web("#1976d2"));
                    } else {
                        setStyle("");
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });

        // Wins column
        TableColumn<LeaderboardData, Integer> winsCol = new TableColumn<>("Số trận thắng");
        winsCol.setPrefWidth(130);
        winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));
        winsCol.setStyle("-fx-alignment: CENTER;");
        winsCol.setCellFactory(column -> new TableCell<LeaderboardData, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    setFont(Font.font("Arial", FontWeight.NORMAL, 13));
                    setTextFill(Color.BLACK);
                }
            }
        });

        // Win rate column
        TableColumn<LeaderboardData, Double> winRateCol = new TableColumn<>("Tỉ lệ thắng (%)");
        winRateCol.setPrefWidth(130);
        winRateCol.setCellValueFactory(new PropertyValueFactory<>("winRate"));
        winRateCol.setStyle("-fx-alignment: CENTER;");
        winRateCol.setCellFactory(column -> new TableCell<LeaderboardData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.2f%%", item));
                    setFont(Font.font("Arial", FontWeight.NORMAL, 13));
                    // Color based on win rate
                    if (item >= 70) {
                        setTextFill(Color.web("#2e7d32")); // Dark green
                    } else if (item >= 50) {
                        setTextFill(Color.web("#f57c00")); // Orange
                    } else {
                        setTextFill(Color.web("#c62828")); // Red
                    }
                }
            }
        });

        table.getColumns().addAll(rankCol, usernameCol, winsCol, winRateCol);
        table.setItems(dataList);
        
        // Remove default selection highlight
        table.setSelectionModel(null);
        
        // Add hover effect to rows
        table.setRowFactory(tv -> {
            TableRow<LeaderboardData> row = new TableRow<>();
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-background-color: #f0f0f0;");
                }
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("");
                }
            });
            return row;
        });

        return table;
    }

    private Button createCloseButton() {
        Button btn = new Button("Đóng");
        btn.setStyle("-fx-background-color: #2d5016; " +
                     "-fx-background-radius: 15; " +
                     "-fx-text-fill: white; " +
                     "-fx-font-size: 14px; " +
                     "-fx-font-weight: bold; " +
                     "-fx-pref-width: 120; " +
                     "-fx-pref-height: 40; " +
                     "-fx-cursor: hand;");
        
        // Hover effect
        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: #3a6b1f; " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 120; " +
                         "-fx-pref-height: 40; " +
                         "-fx-cursor: hand;");
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), btn);
            scaleUp.setToX(1.05);
            scaleUp.setToY(1.05);
            scaleUp.play();
        });
        
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: #2d5016; " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: white; " +
                         "-fx-font-size: 14px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 120; " +
                         "-fx-pref-height: 40; " +
                         "-fx-cursor: hand;");
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), btn);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.play();
        });
        
        // Click effect
        btn.setOnMousePressed(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(100), btn);
            scaleDown.setToX(0.95);
            scaleDown.setToY(0.95);
            scaleDown.play();
        });
        
        btn.setOnMouseReleased(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(100), btn);
            scaleUp.setToX(1.0);
            scaleUp.setToY(1.0);
            scaleUp.play();
        });

        return btn;
    }

    private void loadLeaderboard(Label loadingLabel) {
        if (client == null) {
            Platform.runLater(() -> {
                loadingLabel.setText("Lỗi: Không kết nối được đến server.");
            });
            return;
        }

        Task<Void> leaderboardTask = new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    // Send GET_LEADERBOARD command
                    String resp = client.sendCommand("GET_LEADERBOARD");
                    if (resp == null || !resp.startsWith("LEADERBOARD_REQUEST_OK")) {
                        Platform.runLater(() -> {
                            loadingLabel.setText("Lỗi: " + (resp != null ? resp : "Không có phản hồi"));
                        });
                        return null;
                    }

                    // Poll for LEADERBOARD messages
                    List<LeaderboardData> entries = new ArrayList<>();
                    boolean leaderboardEnd = false;
                    int pollCount = 0;
                    int maxPolls = 100; // Safety limit

                    while (!leaderboardEnd && pollCount < maxPolls) {
                        String pollResp = client.sendCommand("POLL");
                        if (pollResp == null) break;

                        if (pollResp.equals("NO_EVENT")) {
                            Thread.sleep(100);
                            pollCount++;
                            continue;
                        }

                        if (pollResp.startsWith("LEADERBOARD;")) {
                            // Parse: LEADERBOARD;username;wins;winRate
                            String[] parts = pollResp.split(";");
                            if (parts.length >= 4) {
                                String username = parts[1];
                                int wins = Integer.parseInt(parts[2]);
                                double winRate = Double.parseDouble(parts[3]);
                                entries.add(new LeaderboardData(0, username, wins, winRate)); // rank will be set later
                            }
                        } else if (pollResp.equals("LEADERBOARD_END")) {
                            leaderboardEnd = true;
                            break;
                        } else {
                            pollCount++;
                            continue;
                        }
                        pollCount++;
                    }

                    // Set ranks and find current user's info
                    int currentUserRank = 0;
                    int currentUserWins = 0;
                    double currentUserWinRate = 0.0;
                    
                    for (int i = 0; i < entries.size(); i++) {
                        entries.get(i).setRank(i + 1);
                        if (entries.get(i).getUsername().equals(username)) {
                            currentUserRank = i + 1;
                            currentUserWins = entries.get(i).getWins();
                            currentUserWinRate = entries.get(i).getWinRate();
                        }
                    }

                    // Update UI
                    final List<LeaderboardData> finalEntries = entries;
                    final int finalRank = currentUserRank;
                    final int finalWins = currentUserWins;
                    final double finalWinRate = currentUserWinRate;
                    Platform.runLater(() -> {
                        loadingLabel.setVisible(false);
                        dataList.clear();
                        if (finalEntries.isEmpty()) {
                            dataList.add(new LeaderboardData(0, "Chưa có dữ liệu", 0, 0.0));
                        } else {
                            dataList.addAll(finalEntries);
                        }
                        
                        // Update user info
                        if (finalRank > 0) {
                            userInfoLabel.setText(String.format("Tài khoản: %s | Hạng: #%d | Số trận thắng: %d | Tỉ lệ thắng: %.2f%%", 
                                username, finalRank, finalWins, finalWinRate));
                            userInfoBox.setVisible(true);
                        } else {
                            userInfoLabel.setText("Tài khoản: " + username + " | Chưa có dữ liệu");
                            userInfoBox.setVisible(true);
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        loadingLabel.setText("Lỗi: " + e.getMessage());
                    });
                    e.printStackTrace();
                }
                return null;
            }
        };

        new Thread(leaderboardTask).start();
    }

    // Data class for TableView
    public static class LeaderboardData {
        private int rank;
        private String username;
        private int wins;
        private double winRate;

        public LeaderboardData(int rank, String username, int wins, double winRate) {
            this.rank = rank;
            this.username = username;
            this.wins = wins;
            this.winRate = winRate;
        }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getUsername() { return username; }
        public int getWins() { return wins; }
        public double getWinRate() { return winRate; }
    }
}
