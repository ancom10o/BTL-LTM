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
import javafx.stage.StageStyle;
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

    private final long seed;
    private final long startAtMs;

    // --- GAME STATE ---
    private static final int TOTAL_ROUNDS = 10;

    private enum GamePhase {
        PREPARE_MATCH,      // 15s chuẩn bị chung
        PREPARE_QUESTION,   // 5s: "Chuẩn bị cho câu X..."
        SHOW_QUESTION,      // 5s: hiện câu hỏi + đáp án, chưa cho bấm
        ANSWER_PHASE,       // 10s: cho bấm đáp án (chưa gửi server)
        RESULT_PHASE        // 5s: hiển thị kết quả
    }

    private GamePhase currentPhase = GamePhase.PREPARE_MATCH;
    private int currentRound = 0;
    
    // Lưu thông tin câu hỏi và đáp án hiện tại
    private QuestionGenerator.Question currentQuestion = null;
    private String selectedAnswer = null;
    private long answerStartTime = 0;
    private long answerEndTime = 0; // Thời điểm khi chọn đáp án

    // Đếm ngược cho phase hiện tại (tính bằng giây)
    private int phaseTimeLeft = 0;

    // Timeline tick mỗi 1 giây để cập nhật lblTimer và chuyển phase
    private javafx.animation.Timeline phaseTimeline;

    // Question generator dùng seed để đảm bảo cả 2 client có cùng câu hỏi
    private QuestionGenerator questionGenerator;
    
    // Phase notification label (hiển thị ở giữa màn hình)
    private Label phaseNotificationLabel;
    
    // MediaPlayer cho âm thanh câu hỏi
    private javafx.scene.media.MediaPlayer questionSoundPlayer;

    public GameFrame(String username, String opponent, AuthClient client, long seed, long startAtMs) {
        super();
        this.username = username;
        this.opponent = opponent;
        this.client = client;
        this.seed = seed;
        this.startAtMs = startAtMs;
        
        // Khởi tạo question generator với seed
        this.questionGenerator = new QuestionGenerator(seed);
        
        // Tắt background music khi vào match
        SoundManager.getInstance().stopBackgroundMusic();

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
        
        // Phase notification overlay (hiển thị bên dưới phần điểm)
        phaseNotificationLabel = new Label();
        phaseNotificationLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // 32 * 0.75 = 24
        phaseNotificationLabel.setTextFill(Color.WHITE);
        phaseNotificationLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); " +
                                      "-fx-background-radius: 11; " + // 15 * 0.75 = 11.25 ≈ 11
                                      "-fx-padding: 15 30; " + // 20 * 0.75 = 15, 40 * 0.75 = 30
                                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);"); // Giảm effect
        phaseNotificationLabel.setVisible(false);
        phaseNotificationLabel.setManaged(false);
        root.getChildren().add(phaseNotificationLabel);
        // Đặt ở phía trên, bên dưới phần điểm (khoảng 120px từ top)
        StackPane.setAlignment(phaseNotificationLabel, Pos.TOP_CENTER);
        StackPane.setMargin(phaseNotificationLabel, new Insets(120, 0, 0, 0));

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
            // Đợi đến startAtMs để đồng bộ với server
            long now = System.currentTimeMillis();
            long delayMs = startAtMs - now;
            if (delayMs < 0) delayMs = 0;
            
            System.out.println("[GAME] " + username + " scheduling match start in " + delayMs + " ms (seed=" + seed + ")");
            
            javafx.animation.Timeline syncTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(delayMs),
                    ev -> startPhase(GamePhase.PREPARE_MATCH, 15)
                )
            );
            syncTimeline.setCycleCount(1);
            syncTimeline.play();
        });
    }

    private void startPhase(GamePhase phase, int durationSeconds) {
        currentPhase = phase;
        phaseTimeLeft = durationSeconds;

        // Dừng timeline cũ nếu có
        if (phaseTimeline != null) {
            phaseTimeline.stop();
        }

        // Hiển thị thông báo phase ở giữa màn hình
        showPhaseNotification(phase);
        
        // Cập nhật UI đầu phase
        switch (phase) {
            case PREPARE_MATCH -> {
                lblQuestionNumber.setText("Chuẩn bị vào trận...");
                lblQuestion.setText("");
                disableAnswerButtons();
            }
            case PREPARE_QUESTION -> {
                currentRound++;
                if (currentRound > TOTAL_ROUNDS) {
                    // Hết vòng thì dừng timeline và hiển thị kết thúc
                    System.out.println("[GAME] Done " + TOTAL_ROUNDS + " rounds (local only).");
                    if (phaseTimeline != null) {
                        phaseTimeline.stop();
                    }
                    lblQuestionNumber.setText("Trận đấu kết thúc!");
                    lblQuestion.setText("Đã hoàn thành " + TOTAL_ROUNDS + " câu hỏi.");
                    lblTimer.setText("0");
                    disableAnswerButtons();
                    hidePhaseNotification();
                    return;
                }
                lblQuestionNumber.setText("Chuẩn bị cho câu " + currentRound + "...");
                lblQuestion.setText("");
                disableAnswerButtons();
            }
            case SHOW_QUESTION -> {
                lblQuestionNumber.setText("Câu " + currentRound + ":");
                
                // Generate question cho round hiện tại
                // Dùng seed + round để đảm bảo cả 2 client có cùng câu hỏi
                currentQuestion = questionGenerator.generateQuestion(currentRound);
                
                lblQuestion.setText(currentQuestion.getQuestionText());
                
                // Cập nhật text cho 4 buttons
                String[] answers = currentQuestion.getAnswers();
                btnAnswer1.setText(answers[0]);
                btnAnswer2.setText(answers[1]);
                btnAnswer3.setText(answers[2]);
                btnAnswer4.setText(answers[3]);
                
                // Reset button styles về mặc định
                resetButtonStyles();
                
                // Reset selected answer
                selectedAnswer = null;
                
                // Giữ nguyên 4 button, chỉ chưa cho bấm
                disableAnswerButtons();
            }
            case ANSWER_PHASE -> {
                lblQuestionNumber.setText("Câu " + currentRound + ":");
                // Cho phép bấm đáp án trong 10s, chưa gửi server
                answerStartTime = System.currentTimeMillis();
                enableAnswerButtons();
                
                // Phát âm thanh câu hỏi
                playQuestionSound();
            }
            case RESULT_PHASE -> {
                // Hiển thị kết quả trong dialog mới
                showResultDialog();
                disableAnswerButtons();
            }
        }

        // Cập nhật lblTimer lần đầu
        lblTimer.setText(String.valueOf(phaseTimeLeft));

        // Tạo timeline đếm ngược mỗi 1s
        phaseTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                    phaseTimeLeft--;
                    if (phaseTimeLeft < 0) phaseTimeLeft = 0; // Đảm bảo không âm
                    lblTimer.setText(String.valueOf(phaseTimeLeft));
                })
        );
        phaseTimeline.setCycleCount(durationSeconds);
        phaseTimeline.setOnFinished(e -> {
            // Đảm bảo timer về 0 và gọi onPhaseFinished khi timeline kết thúc
            phaseTimeLeft = 0;
            lblTimer.setText("0");
            onPhaseFinished();
        });
        phaseTimeline.play();
    }

    private void onPhaseFinished() {
        if (phaseTimeline != null) {
            phaseTimeline.stop();
        }

        switch (currentPhase) {
            case PREPARE_MATCH -> {
                // Sau 15s chuẩn bị trận -> chuẩn bị câu 1
                startPhase(GamePhase.PREPARE_QUESTION, 5);
            }
            case PREPARE_QUESTION -> {
                // Sau 5s chuẩn bị câu -> hiện câu + đáp án
                startPhase(GamePhase.SHOW_QUESTION, 5);
            }
            case SHOW_QUESTION -> {
                // Sau 5s show câu -> cho phép trả lời 10s
                startPhase(GamePhase.ANSWER_PHASE, 10);
            }
            case ANSWER_PHASE -> {
                // Sau 10s trả lời: chuyển sang hiển thị kết quả
                // Nếu chưa chọn đáp án, lưu thời điểm kết thúc phase
                if (selectedAnswer == null) {
                    answerEndTime = System.currentTimeMillis(); // Hết thời gian = không trả lời
                }
                startPhase(GamePhase.RESULT_PHASE, 5);
            }
            case RESULT_PHASE -> {
                // Sau 5s hiển thị kết quả -> chuẩn bị câu tiếp theo
                System.out.println("[GAME] Round " + currentRound + " finished locally.");
                startPhase(GamePhase.PREPARE_QUESTION, 5);
            }
        }
    }

    private void enableAnswerButtons() {
        btnAnswer1.setDisable(false);
        btnAnswer2.setDisable(false);
        btnAnswer3.setDisable(false);
        btnAnswer4.setDisable(false);
    }

    private void disableAnswerButtons() {
        btnAnswer1.setDisable(true);
        btnAnswer2.setDisable(true);
        btnAnswer3.setDisable(true);
        btnAnswer4.setDisable(true);
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
        // Answer button actions - sẽ cập nhật text động trong SHOW_QUESTION phase
        btnAnswer1.setOnAction(e -> handleAnswer(btnAnswer1.getText()));
        btnAnswer2.setOnAction(e -> handleAnswer(btnAnswer2.getText()));
        btnAnswer3.setOnAction(e -> handleAnswer(btnAnswer3.getText()));
        btnAnswer4.setOnAction(e -> handleAnswer(btnAnswer4.getText()));
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
        // Chỉ cho phép trả lời trong ANSWER_PHASE
        if (currentPhase != GamePhase.ANSWER_PHASE) {
            System.out.println("[GAME] " + username + " không thể trả lời ở phase: " + currentPhase);
            return;
        }
        
        // Lưu đáp án đã chọn (chỉ lưu lần đầu)
        if (selectedAnswer == null) {
            selectedAnswer = answer;
            answerEndTime = System.currentTimeMillis(); // Lưu thời điểm chọn đáp án
            long answerTime = answerEndTime - answerStartTime;
            System.out.println("[GAME] " + username + " chọn: " + answer +
                    " (round " + currentRound + ", time=" + (answerTime / 1000.0) + "s)");
        }
        
        // Disable buttons sau khi chọn để tránh chọn lại
        disableAnswerButtons();
    }
    
    private void showResultDialog() {
        if (currentQuestion == null) {
            return;
        }
        
        // Tính thời gian trả lời (từ khi bắt đầu ANSWER_PHASE đến khi chọn đáp án hoặc hết phase)
        long answerTimeMs = 0;
        if (answerStartTime > 0 && answerEndTime > 0) {
            // Tính từ start đến end (có thể là khi chọn đáp án hoặc khi hết phase)
            answerTimeMs = answerEndTime - answerStartTime;
        }
        double answerTimeSeconds = answerTimeMs / 1000.0;
        
        // Xác định đáp án đúng
        String[] answers = currentQuestion.getAnswers();
        String correctAnswer = answers[currentQuestion.getCorrectIndex()];
        boolean isCorrect = selectedAnswer != null && selectedAnswer.equals(correctAnswer);
        
        // Format: "test1(4,3s): Đúng" hoặc "test1(4,3s): Sai"
        String resultText;
        if (selectedAnswer == null) {
            // Không trả lời = sai
            String timeStr = String.format("%.1f", answerTimeSeconds).replace(".", ",");
            resultText = username + "(" + timeStr + "s): Sai";
        } else {
            // Format thời gian: 4,3s (dùng dấu phẩy)
            String timeStr = String.format("%.1f", answerTimeSeconds).replace(".", ",");
            resultText = username + "(" + timeStr + "s): " + (isCorrect ? "Đúng" : "Sai");
        }
        
        // Tô màu buttons trong game frame
        // Đáp án đúng: màu xanh
        Button correctButton = getButtonByText(correctAnswer);
        if (correctButton != null) {
            correctButton.setStyle("-fx-background-color: #4caf50; " + // Xanh lá
                                 "-fx-background-radius: 15; " +
                                 "-fx-text-fill: white; " +
                                 "-fx-font-size: 18px; " +
                                 "-fx-font-weight: bold; " +
                                 "-fx-pref-width: 220; " +
                                 "-fx-pref-height: 70; " +
                                 "-fx-cursor: default;");
        }
        
        // Đáp án đã chọn: xanh nếu đúng, đỏ nếu sai
        if (selectedAnswer != null) {
            Button selectedButton = getButtonByText(selectedAnswer);
            if (selectedButton != null && !selectedAnswer.equals(correctAnswer)) {
                selectedButton.setStyle("-fx-background-color: #f44336; " + // Đỏ
                                       "-fx-background-radius: 15; " +
                                       "-fx-text-fill: white; " +
                                       "-fx-font-size: 18px; " +
                                       "-fx-font-weight: bold; " +
                                       "-fx-pref-width: 220; " +
                                       "-fx-pref-height: 70; " +
                                       "-fx-cursor: default;");
            }
        }
        
        // Tạo dialog mới để hiển thị kết quả
        Stage resultDialog = new Stage();
        resultDialog.initModality(Modality.APPLICATION_MODAL);
        resultDialog.initOwner(this);
        resultDialog.initStyle(StageStyle.TRANSPARENT); // Không có title bar
        resultDialog.setResizable(false);
        
        VBox dialogRoot = new VBox(20);
        dialogRoot.setPadding(new Insets(30));
        dialogRoot.setAlignment(Pos.CENTER);
        
        // Màu nền dựa trên kết quả
        String bgColor = isCorrect ? "#e8f5e9" : "#ffebee"; // Xanh nhạt nếu đúng, đỏ nhạt nếu sai
        String textColor = isCorrect ? "#2e7d32" : "#c62828"; // Xanh đậm nếu đúng, đỏ đậm nếu sai
        
        dialogRoot.setStyle("-fx-background-color: " + bgColor + "; " +
                           "-fx-background-radius: 20; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 5);");
        
        // Label hiển thị kết quả
        Label resultLabel = new Label(resultText);
        resultLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        resultLabel.setTextFill(Color.web(textColor));
        resultLabel.setStyle("-fx-padding: 20;");
        
        dialogRoot.getChildren().add(resultLabel);
        
        Scene dialogScene = new Scene(dialogRoot, 400, 150);
        dialogScene.setFill(null); // Transparent scene
        resultDialog.setScene(dialogScene);
        
        // Center dialog relative to game window
        double dialogWidth = 400;
        double dialogHeight = 150;
        double gameX = this.getX();
        double gameY = this.getY();
        double gameWidth = this.getWidth();
        double gameHeight = this.getHeight();
        
        resultDialog.setX(gameX + (gameWidth - dialogWidth) / 2);
        resultDialog.setY(gameY + (gameHeight - dialogHeight) / 2);
        
        // Hiển thị dialog
        resultDialog.show();
        
        // Tự đóng sau 5 giây
        javafx.animation.Timeline closeTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(5),
                e -> resultDialog.close()
            )
        );
        closeTimeline.setCycleCount(1);
        closeTimeline.play();
    }
    
    private Button getButtonByText(String text) {
        if (btnAnswer1.getText().equals(text)) return btnAnswer1;
        if (btnAnswer2.getText().equals(text)) return btnAnswer2;
        if (btnAnswer3.getText().equals(text)) return btnAnswer3;
        if (btnAnswer4.getText().equals(text)) return btnAnswer4;
        return null;
    }
    
    private void resetButtonStyles() {
        String buttonStyle = "-fx-background-color: rgba(220, 220, 220, 0.9); " +
                           "-fx-background-radius: 15; " +
                           "-fx-text-fill: black; " +
                           "-fx-font-size: 18px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-pref-width: 220; " +
                           "-fx-pref-height: 70; " +
                           "-fx-cursor: hand;";
        
        btnAnswer1.setStyle(buttonStyle);
        btnAnswer2.setStyle(buttonStyle);
        btnAnswer3.setStyle(buttonStyle);
        btnAnswer4.setStyle(buttonStyle);
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
        if (phaseTimeline != null) {
            phaseTimeline.stop();
        }
        // Dừng âm thanh câu hỏi nếu đang phát
        if (questionSoundPlayer != null) {
            questionSoundPlayer.stop();
            questionSoundPlayer.dispose();
            questionSoundPlayer = null;
        }
        close();
    }

    private void handleClose(WindowEvent e) {
        if (phaseTimeline != null) {
            phaseTimeline.stop();
        }
        // Dừng âm thanh câu hỏi nếu đang phát
        if (questionSoundPlayer != null) {
            questionSoundPlayer.stop();
            questionSoundPlayer.dispose();
            questionSoundPlayer = null;
        }
    }
    
    private void showPhaseNotification(GamePhase phase) {
        String phaseText = "";
        switch (phase) {
            case PREPARE_MATCH -> phaseText = "Chuẩn bị vào trận...";
            case PREPARE_QUESTION -> phaseText = "Chuẩn bị cho câu " + (currentRound + 1) + "...";
            case SHOW_QUESTION -> phaseText = "Câu hỏi " + currentRound;
            case ANSWER_PHASE -> phaseText = "Trả lời câu hỏi";
            case RESULT_PHASE -> phaseText = "Kết quả";
        }
        
        phaseNotificationLabel.setText(phaseText);
        phaseNotificationLabel.setVisible(true);
        phaseNotificationLabel.setManaged(true);
        
        // Tự ẩn sau 2 giây
        javafx.animation.Timeline hideTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(2),
                e -> hidePhaseNotification()
            )
        );
        hideTimeline.setCycleCount(1);
        hideTimeline.play();
    }
    
    private void hidePhaseNotification() {
        phaseNotificationLabel.setVisible(false);
        phaseNotificationLabel.setManaged(false);
    }
    
    private void playQuestionSound() {
        // Dừng âm thanh cũ nếu có
        if (questionSoundPlayer != null) {
            questionSoundPlayer.stop();
            questionSoundPlayer.dispose();
            questionSoundPlayer = null;
        }
        
        if (currentQuestion == null || currentQuestion.getSound() == null) {
            return;
        }
        
        // Lấy đường dẫn file âm thanh từ SoundEntry
        String audioFile = currentQuestion.getSound().getAudioFile();
        if (audioFile == null || audioFile.isEmpty()) {
            return;
        }
        
        // Phát âm thanh qua SoundManager
        questionSoundPlayer = SoundManager.getInstance().playSound(audioFile, 0.7);
    }
}
