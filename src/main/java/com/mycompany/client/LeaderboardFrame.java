package com.mycompany.client;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class LeaderboardFrame extends Stage {

    public LeaderboardFrame() {
        super();
        setTitle("Leaderboard");

        Label label = new Label("Bảng xếp hạng\nChức năng đang phát triển...");
        label.setStyle("-fx-font-size: 18px;");
        label.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(label);

        Scene scene = new Scene(root, 600, 400);
        setScene(scene);
        centerOnScreen();
    }
}
