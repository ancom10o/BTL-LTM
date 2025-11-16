package com.mycompany.client;

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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.InputStream;

public class GameFrame extends Stage {
    private final String username;
    private final String opponent;
    private final AuthClient client;

    // Score labels
    private Label lblScore1;
    private Label lblScore2;
    
    // Clock
    private final Label lblTimer = new Label("10");
    private ImageView clockImageView;
    
    // Question
    private final Label lblQuestionNumber = new Label("Câu 1:");
    private final Label lblQuestion = new Label("Đây là âm thanh của con vật nào ?");
    
    // Answer buttons
    private final Button btnAnswer1 = new Button("Sư tử");
    private final Button btnAnswer2 = new Button("Khỉ");
    private final Button btnAnswer3 = new Button("Chó");
    private final Button btnAnswer4 = new Button("Trâu");

    public GameFrame(String username, String opponent, AuthClient client) {
        super();
        this.username = username;
        this.opponent = opponent;
        this.client = client;

        setTitle("Match: " + username + " vs " + opponent);
        setResizable(true);
        setOnCloseRequest(this::handleClose);
        
        // Initialize score labels
        lblScore1 = new Label(username + ": 0");
        lblScore2 = new Label(opponent + ": 0");

        // Load background image
        InputStream bgStream = getClass().getResourceAsStream("/images/background/match_background.jpg");
        
        // Window size (70% of original)
        double windowWidth = 840;  // 1200 * 0.7
        double windowHeight = 560; // 800 * 0.7
        
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

        // Main content
        VBox mainContent = buildMainContent();
        root.getChildren().add(mainContent);
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

        // Events
        setupAnswerButtons();
        
        setOnShown(e -> {
            // Start game logic here later
        });
    }

    private VBox buildMainContent() {
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_CENTER);

        // Top bar: Back button, Score, Clock
        HBox topBar = buildTopBar();
        
        // Question area
        VBox questionArea = buildQuestionArea();
        
        // Answer buttons area
        GridPane answerGrid = buildAnswerGrid();
        
        mainContent.getChildren().addAll(topBar, questionArea, answerGrid);
        
        return mainContent;
    }

    private HBox buildTopBar() {
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(10));

        // Back button (out.png) - taller with hover effect, positioned higher
        VBox btnBackContainer = new VBox();
        btnBackContainer.setAlignment(Pos.TOP_LEFT);
        btnBackContainer.setPadding(new Insets(10, 0, 0, 0)); // Move up 10px
        
        Button btnBack = new Button();
        InputStream outStream = getClass().getResourceAsStream("/images/background/out.png");
        ImageView outImageView;
        if (outStream != null) {
            Image outImage = new Image(outStream);
            outImageView = new ImageView(outImage);
            outImageView.setFitWidth(70); // Taller
            outImageView.setFitHeight(80); // Taller
            outImageView.setPreserveRatio(true);
            btnBack.setGraphic(outImageView);
            
            // Hover effect - scale to 1.1
            final ImageView finalOutImageView = outImageView;
            btnBack.setOnMouseEntered(e -> {
                finalOutImageView.setFitWidth(77); // 70 * 1.1
                finalOutImageView.setFitHeight(88); // 80 * 1.1
            });
            btnBack.setOnMouseExited(e -> {
                finalOutImageView.setFitWidth(70);
                finalOutImageView.setFitHeight(80);
            });
        } else {
            btnBack.setText("←");
            btnBack.setStyle("-fx-font-size: 35px;");
            outImageView = null;
        }
        btnBack.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        btnBack.setOnAction(e -> showExitConfirmDialog());
        btnBackContainer.getChildren().add(btnBack);

        // Score area
        HBox scoreArea = new HBox(15);
        scoreArea.setAlignment(Pos.CENTER);
        Label lblScoreLabel = new Label("Điểm:");
        lblScoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        lblScoreLabel.setTextFill(Color.BLACK);
        lblScore1.setFont(Font.font("Arial", 14));
        lblScore1.setTextFill(Color.BLACK);
        lblScore2.setFont(Font.font("Arial", 14));
        lblScore2.setTextFill(Color.BLACK);
        scoreArea.getChildren().addAll(lblScoreLabel, lblScore1, lblScore2);

        // Clock area - timer centered on clock image
        StackPane clockArea = new StackPane();
        clockArea.setAlignment(Pos.CENTER);
        
        InputStream clockStream = getClass().getResourceAsStream("/images/background/Dongho_match_ background.png");
        if (clockStream != null) {
            Image clockImage = new Image(clockStream);
            clockImageView = new ImageView(clockImage);
            clockImageView.setFitWidth(150); // 1.5x larger (100 * 1.5)
            clockImageView.setFitHeight(150);
            clockImageView.setPreserveRatio(true);
            clockArea.getChildren().add(clockImageView);
        }
        
        // Timer label centered on clock - smaller font to fit better
        lblTimer.setFont(Font.font("Arial", FontWeight.BOLD, 36)); // Reduced font size to fit better
        lblTimer.setTextFill(Color.RED);
        lblTimer.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
        clockArea.getChildren().add(lblTimer);

        // Spacer to push elements apart
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        topBar.getChildren().addAll(btnBackContainer, spacer1, scoreArea, spacer2, clockArea);

        return topBar;
    }

    private VBox buildQuestionArea() {
        VBox questionArea = new VBox(15);
        questionArea.setAlignment(Pos.CENTER);
        questionArea.setPadding(new Insets(30, 40, 20, 40)); // More balanced padding

        // Question number
        lblQuestionNumber.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        lblQuestionNumber.setTextFill(Color.BLACK);
        lblQuestionNumber.setStyle("-fx-background-color: rgba(220, 220, 220, 0.9); " +
                                  "-fx-background-radius: 12; " +
                                  "-fx-padding: 12 25;");

        // Question text
        lblQuestion.setFont(Font.font("Arial", 18));
        lblQuestion.setTextFill(Color.BLACK);
        lblQuestion.setWrapText(true);
        lblQuestion.setMaxWidth(600); // Limit width for better readability
        lblQuestion.setStyle("-fx-background-color: rgba(220, 220, 220, 0.9); " +
                            "-fx-background-radius: 12; " +
                            "-fx-padding: 18 30;");

        questionArea.getChildren().addAll(lblQuestionNumber, lblQuestion);

        return questionArea;
    }

    private GridPane buildAnswerGrid() {
        GridPane answerGrid = new GridPane();
        answerGrid.setHgap(25);
        answerGrid.setVgap(25);
        answerGrid.setAlignment(Pos.CENTER);
        answerGrid.setPadding(new Insets(30, 40, 40, 40)); // More balanced padding

        // Button style
        String buttonStyle = "-fx-background-color: rgba(220, 220, 220, 0.9); " +
                           "-fx-background-radius: 15; " +
                           "-fx-text-fill: black; " +
                           "-fx-font-size: 18px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-pref-width: 220; " +
                           "-fx-pref-height: 70; " +
                           "-fx-cursor: hand;";
        
        String buttonHoverStyle = "-fx-background-color: rgba(240, 240, 240, 0.95); " +
                                 "-fx-background-radius: 15; " +
                                 "-fx-text-fill: black; " +
                                 "-fx-font-size: 18px; " +
                                 "-fx-font-weight: bold; " +
                                 "-fx-pref-width: 220; " +
                                 "-fx-pref-height: 70; " +
                                 "-fx-cursor: hand;";

        btnAnswer1.setStyle(buttonStyle);
        btnAnswer1.setOnMouseEntered(e -> btnAnswer1.setStyle(buttonHoverStyle));
        btnAnswer1.setOnMouseExited(e -> btnAnswer1.setStyle(buttonStyle));

        btnAnswer2.setStyle(buttonStyle);
        btnAnswer2.setOnMouseEntered(e -> btnAnswer2.setStyle(buttonHoverStyle));
        btnAnswer2.setOnMouseExited(e -> btnAnswer2.setStyle(buttonStyle));

        btnAnswer3.setStyle(buttonStyle);
        btnAnswer3.setOnMouseEntered(e -> btnAnswer3.setStyle(buttonHoverStyle));
        btnAnswer3.setOnMouseExited(e -> btnAnswer3.setStyle(buttonStyle));

        btnAnswer4.setStyle(buttonStyle);
        btnAnswer4.setOnMouseEntered(e -> btnAnswer4.setStyle(buttonHoverStyle));
        btnAnswer4.setOnMouseExited(e -> btnAnswer4.setStyle(buttonStyle));

        // 2x2 grid
        answerGrid.add(btnAnswer1, 0, 0); // Sư tử
        answerGrid.add(btnAnswer2, 1, 0); // Khỉ
        answerGrid.add(btnAnswer3, 0, 1); // Chó
        answerGrid.add(btnAnswer4, 1, 1); // Trâu

        return answerGrid;
    }

    private void setupAnswerButtons() {
        // Answer button actions - logic will be added later
        btnAnswer1.setOnAction(e -> handleAnswer("Sư tử"));
        btnAnswer2.setOnAction(e -> handleAnswer("Khỉ"));
        btnAnswer3.setOnAction(e -> handleAnswer("Chó"));
        btnAnswer4.setOnAction(e -> handleAnswer("Trâu"));
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

    private void handleAnswer(String answer) {
        // Logic will be added later
        System.out.println("Selected answer: " + answer);
    }

    private void showExitConfirmDialog() {
        // Create dialog with scale effect
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.setTitle("Xác nhận");
        dialog.setResizable(false);

        VBox dialogRoot = new VBox(20);
        dialogRoot.setPadding(new Insets(30));
        dialogRoot.setStyle("-fx-background-color: white; " +
                           "-fx-background-radius: 15; " +
                           "-fx-border-radius: 15;");
        dialogRoot.setAlignment(Pos.CENTER);

        // Apply scale effect (1.1) using transforms
        javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(1.1, 1.1);
        dialogRoot.getTransforms().add(scale);

        Label questionLabel = new Label("Bạn có muốn thoát trận đấu không?");
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        questionLabel.setWrapText(true);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button btnYes = new Button("Có");
        btnYes.setStyle("-fx-background-color: #2d5016; " +
                       "-fx-background-radius: 10; " +
                       "-fx-text-fill: white; " +
                       "-fx-font-size: 14px; " +
                       "-fx-font-weight: bold; " +
                       "-fx-pref-width: 100; " +
                       "-fx-pref-height: 35; " +
                       "-fx-cursor: hand;");
        btnYes.setOnAction(e -> {
            dialog.close();
            doLeave();
        });

        Button btnNo = new Button("Không");
        btnNo.setStyle("-fx-background-color: #666; " +
                      "-fx-background-radius: 10; " +
                      "-fx-text-fill: white; " +
                      "-fx-font-size: 14px; " +
                      "-fx-font-weight: bold; " +
                      "-fx-pref-width: 100; " +
                      "-fx-pref-height: 35; " +
                      "-fx-cursor: hand;");
        btnNo.setOnAction(e -> dialog.close());

        buttonBox.getChildren().addAll(btnYes, btnNo);
        dialogRoot.getChildren().addAll(questionLabel, buttonBox);

        Scene dialogScene = new Scene(dialogRoot, 350, 200);
        dialog.setScene(dialogScene);
        
        // Center dialog relative to game window
        double dialogWidth = 350;
        double dialogHeight = 200;
        double gameX = this.getX();
        double gameY = this.getY();
        double gameWidth = this.getWidth();
        double gameHeight = this.getHeight();
        
        dialog.setX(gameX + (gameWidth - dialogWidth) / 2);
        dialog.setY(gameY + (gameHeight - dialogHeight) / 2);
        
        dialog.showAndWait();
    }

    private void doLeave() {
        // Optionally tell server later (e.g., SEND/LEAVE). For now just close.
        close();
    }

    private void handleClose(WindowEvent e) {
        // Optionally show confirmation on window close
        // For now just close
    }
}
