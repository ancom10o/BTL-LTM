package com.mycompany.client;

import javafx.application.Application;
import javafx.stage.Stage;

public class ClientMain extends Application {
    @Override
    public void start(Stage primaryStage) {
        new AuthFrame().show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
