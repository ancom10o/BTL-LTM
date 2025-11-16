package com.mycompany.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GameFrame extends Stage {
    private final String username;
    private final String opponent;
    private final AuthClient client;

    private final TextArea taLog = new TextArea();
    private final Button btnLeave = new Button("Leave Match");

    public GameFrame(String username, String opponent, AuthClient client) {
        super();
        this.username = username;
        this.opponent = opponent;
        this.client = client;

        setTitle("Match: " + username + " vs " + opponent);

        // Header
        HBox top = new HBox(10);
        top.setPadding(new Insets(10));
        top.getChildren().add(new Label("Playing: " + username + "  vs  " + opponent));

        // Center placeholder (canvas area)
        Pane arena = new Pane();
        arena.setPrefSize(640, 360);
        arena.setStyle("-fx-background-color: #f5f6fa; -fx-border-color: #ccc; -fx-border-width: 1;");
        Label arenaLabel = new Label("Game Area (placeholder)");
        arenaLabel.setStyle("-fx-font-size: 14px;");
        StackPane arenaContainer = new StackPane(arena, arenaLabel);

        // Right side log
        taLog.setEditable(false);
        taLog.setWrapText(true);
        taLog.setPrefRowCount(20);
        ScrollPane sp = new ScrollPane(taLog);
        sp.setPrefSize(300, 360);

        // Split pane for arena and log
        SplitPane centerSplit = new SplitPane(arenaContainer, sp);
        centerSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        centerSplit.setDividerPositions(0.68);

        // Bottom bar
        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10));
        bottom.getChildren().add(btnLeave);

        VBox root = new VBox(10);
        root.getChildren().addAll(top, centerSplit, bottom);

        Scene scene = new Scene(root, 960, 420);
        setScene(scene);
        centerOnScreen();

        // Events
        btnLeave.setOnAction(e -> doLeave());

        setOnShown(e -> {
            log("Match started with " + opponent);
        });
    }

    private void doLeave() {
        // Optionally tell server later (e.g., SEND/LEAVE). For now just close.
        close();
    }

    private void log(String s) {
        Platform.runLater(() -> taLog.appendText(s + "\n"));
    }
}
