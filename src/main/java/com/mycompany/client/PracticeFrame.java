package com.mycompany.client;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class PracticeFrame extends Stage {

    public PracticeFrame() {
        super();
        setTitle("Practice Mode");

        Label label = new Label("Chế độ luyện tập\nChức năng đang phát triển...");
        label.setStyle("-fx-font-size: 18px;");
        label.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(label);

        Scene scene = new Scene(root, 600, 400);
        setScene(scene);
        centerOnScreen();
    }
}
