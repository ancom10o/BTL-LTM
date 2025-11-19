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
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;

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
    
    // Match key để identify match trên server (dùng seed + player1 + player2)
    private final String matchKey;

    // --- GAME STATE ---
    private static final int TOTAL_ROUNDS = 5;

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
    
    // Polling service để nhận RESULT từ server
    private ScheduledService<Void> resultPollingService;
    
    // Điểm số hiện tại
    private int myScore = 0;
    private int opponentScore = 0;
    
    // Lưu kết quả từ server cho các round (round -> ServerRoundResult)
    // Dùng ConcurrentHashMap để thread-safe
    private final java.util.concurrent.ConcurrentHashMap<Integer, ServerRoundResult> serverResults = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Helper method để lấy serverResult cho round hiện tại
    private ServerRoundResult getServerResult(int round) {
        return serverResults.get(round);
    }
    
    // Helper method để set serverResult cho round
    private void setServerResult(int round, ServerRoundResult result) {
        if (result != null) {
            serverResults.put(round, result);
            System.out.println("[GAME] ServerResult stored for round " + round + ", total stored: " + serverResults.size());
        }
    }

    public GameFrame(String username, String opponent, AuthClient client, long seed, long startAtMs) {
        super();
        this.username = username;
        this.opponent = opponent;
        this.client = client;
        this.seed = seed;
        this.startAtMs = startAtMs;
        
        // Khởi tạo question generator với seed
        this.questionGenerator = new QuestionGenerator(seed);
        
        // Tạo matchKey từ seed và player names (đảm bảo cả 2 client có cùng key)
        // Sắp xếp player names theo alphabet để đảm bảo consistency
        String player1, player2;
        if (username.compareToIgnoreCase(opponent) < 0) {
            player1 = username;
            player2 = opponent;
        } else {
            player1 = opponent;
            player2 = username;
        }
        this.matchKey = player1 + "_" + player2 + "_" + seed;
        
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
            // Bắt đầu polling để nhận RESULT từ server
            startResultPolling();
            
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
    
    private void startResultPolling() {
    resultPollingService = new ScheduledService<Void>() {
        @Override
        protected Task<Void> createTask() {
            return new Task<Void>() {
                @Override
                protected Void call() {
                    try {
                        String resp = client.sendCommand("POLL");

                        // Log raw để xem mỗi lần poll về cái gì
                        System.out.println("[POLL-CLIENT][" + username + "] resp = " + resp);

                        if (resp != null && !resp.trim().isEmpty() && !resp.trim().equals("NO_EVENT")) {
                            // Nếu server có trả nhiều message trên nhiều dòng thì tách ra
                            String[] messages = resp.split("\\r?\\n");
                            System.out.println("[POLL-CLIENT][" + username + "] Received " + messages.length + " message(s)");
                            for (int i = 0; i < messages.length; i++) {
                                String trimmed = messages[i].trim();
                                if (!trimmed.isEmpty()) {
                                    final String msg = trimmed; // Final để dùng trong lambda
                                    System.out.println("[POLL-CLIENT][" + username + "] Processing message " + (i+1) + "/" + messages.length + ": " + msg);
                                    Platform.runLater(() -> handleServerMessage(msg));
                                } else {
                                    System.out.println("[POLL-CLIENT][" + username + "] Skipping empty message " + (i+1));
                                }
                            }
                        } else if (resp != null && resp.trim().equals("NO_EVENT")) {
                            // Không log NO_EVENT để giảm noise
                        } else {
                            System.err.println("[POLL-CLIENT][" + username + "] Received null or empty response from server");
                        }
                    } catch (Exception e) {
                        System.err.println("[GAME] Polling error for " + username + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                    return null;
                }
            };
        }
    };
    resultPollingService.setPeriod(javafx.util.Duration.seconds(1.0)); // Poll mỗi 1s
    resultPollingService.start();
}

    
    private void handleServerMessage(String message) {
    if (message == null || message.trim().isEmpty()) {
        System.err.println("[GAME] Received null or empty message from server");
        return;
    }
    
    System.out.println("[GAME] Received message from server: " + message);
    
    if (message.startsWith("RESULT;")) {
        // FORMAT: RESULT;matchKey;round;p1Score;p2Score;p1Correct;p2Correct;p1TimeMs;p2TimeMs
        String[] parts = message.split(";");
        System.out.println("[GAME] Parsing RESULT message, parts.length=" + parts.length + 
                         ", message=" + message);
        
        if (parts.length >= 9) {
            try {
                int round = Integer.parseInt(parts[2]);
                int p1Score = Integer.parseInt(parts[3]);
                int p2Score = Integer.parseInt(parts[4]);
                boolean p1Correct = parts[5].equals("1");
                boolean p2Correct = parts[6].equals("1");
                long p1TimeMs = Long.parseLong(parts[7]);
                long p2TimeMs = Long.parseLong(parts[8]);
                
                System.out.println("[GAME] RESULT parsed: round=" + round + 
                                 ", p1Score=" + p1Score + ", p2Score=" + p2Score);
                
                // Only accept RESULT if round >= currentRound (idempotent: overwrite any previous value for that round)
                if (round < currentRound) {
                    System.out.println("[GAME] Ignoring RESULT for round " + round + " (currentRound=" + currentRound + ")");
                    return;
                }
                
                // Xác định player1 và player2 từ matchKey
                String player1, player2;
                if (username.compareToIgnoreCase(opponent) < 0) {
                    player1 = username;
                    player2 = opponent;
                } else {
                    player1 = opponent;
                    player2 = username;
                }
                
                // Lưu kết quả từ server (overwrites any previous value for this round)
                ServerRoundResult result = new ServerRoundResult(
                    round, player1, player2, 
                    p1Score, p2Score, p1Correct, p2Correct,
                    p1TimeMs, p2TimeMs
                );
                setServerResult(round, result);
                
                System.out.println("[GAME] ServerResult saved for round " + round + 
                                 ", currentRound=" + currentRound + 
                                 ", currentPhase=" + currentPhase +
                                 ", totalResults=" + serverResults.size());
                
                // Hiệu ứng nhấp nháy khi nhận được RESULT
                Platform.runLater(() -> flashNotification("Đã nhận kết quả từ server!"));
                
                // Cập nhật điểm số (theo kết quả từ server)
                // Note: Only update scores if this is for the current round to avoid double-counting
                if (round == currentRound) {
                    if (username.equals(player1)) {
                        myScore += p1Score;
                        opponentScore += p2Score;
                    } else {
                        myScore += p2Score;
                        opponentScore += p1Score;
                    }
                    
                    // Cập nhật UI điểm số
                    Platform.runLater(() -> {
                        lblScore1.setText(username + ": " + myScore);
                        lblScore2.setText(opponent + ": " + opponentScore);
                    });
                }
                
                System.out.println("[GAME] Round " + round + " result received: " + username + "=" + 
                                 (username.equals(player1) ? p1Score : p2Score) + 
                                 ", " + opponent + "=" + 
                                 (opponent.equals(player1) ? p1Score : p2Score));
                
                // ❌ BỎ phần show dialog trực tiếp tại đây
                // Việc hiển thị dialog sẽ do waitForServerResult() xử lý,
                // để tránh gọi showResultDialog() 2 lần.
                
            } catch (NumberFormatException e) {
                System.err.println("[GAME] NumberFormatException parsing RESULT message: " + e.getMessage());
                System.err.println("[GAME] Message was: " + message);
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("[GAME] Error parsing RESULT message: " + e.getMessage());
                System.err.println("[GAME] Message was: " + message);
                e.printStackTrace();
            }
        } else {
            System.err.println("[GAME] RESULT message has invalid length: " + parts.length + 
                             " (expected >= 9), message: " + message);
            // Log từng phần để debug
            for (int i = 0; i < parts.length; i++) {
                System.err.println("[GAME]   parts[" + i + "] = '" + parts[i] + "'");
            }
        }
    } else {
        // Log các message khác để debug
        if (!message.equals("NO_EVENT") && !message.startsWith("HISTORY") && !message.startsWith("LEADERBOARD")) {
            System.out.println("[GAME] Received non-RESULT message: " + message);
        }
    }
}

    
    // Class để lưu kết quả từ server
    private static class ServerRoundResult {
        final int round;
        final String player1, player2;
        final int p1Score, p2Score;
        final boolean p1Correct, p2Correct;
        final long p1TimeMs, p2TimeMs;
        
        ServerRoundResult(int round, String player1, String player2,
                         int p1Score, int p2Score, boolean p1Correct, boolean p2Correct,
                         long p1TimeMs, long p2TimeMs) {
            this.round = round;
            this.player1 = player1;
            this.player2 = player2;
            this.p1Score = p1Score;
            this.p2Score = p2Score;
            this.p1Correct = p1Correct;
            this.p2Correct = p2Correct;
            this.p1TimeMs = p1TimeMs;
            this.p2TimeMs = p2TimeMs;
        }
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
                // Reset per-round state at the start of each new round
                resetRoundState();
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
                    // Gửi tổng điểm lên server và hiển thị bảng kết quả
                    sendFinalScoreToServer();
                    showFinalMatchResult();
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
                // Đợi để đảm bảo đã nhận được RESULT từ server
                disableAnswerButtons();
                // Đợi tối đa 5 giây để nhận RESULT, kiểm tra mỗi 200ms
                waitForServerResult();
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
                // Sau 10s trả lời: gửi đáp án lên server
                // Nếu chưa chọn đáp án, đảm bảo gửi với status WRONG và timeMs = 10000
                if (selectedAnswer == null) {
                    answerEndTime = System.currentTimeMillis(); // Hết thời gian = không trả lời
                    System.out.println("[GAME] No answer selected, will send WRONG with 10s");
                }
                // Gửi đáp án lên server (luôn luôn gửi, kể cả khi không trả lời)
                sendAnswerToServer();
                startPhase(GamePhase.RESULT_PHASE, 5);
            }
            case RESULT_PHASE -> {
                // Do NOT start next round here. Next round will be started from showResultDialog() after the popup closes.
                System.out.println("[GAME] Round " + currentRound + " RESULT_PHASE finished.");
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
            System.err.println("[GAME] Cannot show result dialog: currentQuestion is null");
            return;
        }
        
        // Lấy serverResult cho round hiện tại
        ServerRoundResult serverResult = getServerResult(currentRound);
        if (serverResult == null || serverResult.round != currentRound) {
            System.err.println("[GAME] No serverResult available for round " + currentRound + 
                            ", available rounds: " + serverResults.keySet() + ", skipping dialog");
            return;
        }
        
        // Xác định đáp án đúng để tô màu buttons
        String[] answers = currentQuestion.getAnswers();
        String correctAnswer = answers[currentQuestion.getCorrectIndex()];
        
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
        
        // Hiển thị kết quả từ server
        double p1TimeSeconds = serverResult.p1TimeMs / 1000.0;
        double p2TimeSeconds = serverResult.p2TimeMs / 1000.0;
        
        String p1TimeStr = String.format("%.0f", p1TimeSeconds).replace(".", ",");
        String p2TimeStr = String.format("%.0f", p2TimeSeconds).replace(".", ",");
        
        // Tính điểm tổng hiện tại (sau khi đã cộng điểm round này)
        int p1TotalScore = username.equals(serverResult.player1) ? myScore : opponentScore;
        int p2TotalScore = username.equals(serverResult.player2) ? myScore : opponentScore;
        
        // Tạo text hiển thị
        StringBuilder sb = new StringBuilder();
        sb.append(serverResult.player1).append("(").append(p1TimeStr).append("s): ")
          .append(serverResult.p1Correct ? "Đúng" : "Sai")
          .append(" +").append(serverResult.p1Score).append(" điểm\n");
        sb.append(serverResult.player2).append("(").append(p2TimeStr).append("s): ")
          .append(serverResult.p2Correct ? "Đúng" : "Sai")
          .append(" +").append(serverResult.p2Score).append(" điểm\n\n");
        sb.append("Điểm hiện tại:\n");
        sb.append(serverResult.player1).append(" = ").append(p1TotalScore).append("đ\n");
        sb.append(serverResult.player2).append(" = ").append(p2TotalScore).append("đ");
        
        String resultText = sb.toString();
        String bgColor = "#e8f5e9"; // Xanh nhạt
        String textColor = "#2e7d32"; // Xanh đậm
        
        // Xóa serverResult cho round này sau khi đã hiển thị (giữ lại các round khác)
        serverResults.remove(currentRound);
        System.out.println("[GAME] Removed ServerResult for round " + currentRound + 
                         ", remaining: " + serverResults.keySet());
        
        dialogRoot.setStyle("-fx-background-color: " + bgColor + "; " +
                           "-fx-background-radius: 20; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 5);");
        
        // Label hiển thị kết quả
        Label resultLabel = new Label(resultText);
        resultLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        resultLabel.setTextFill(Color.web(textColor));
        resultLabel.setStyle("-fx-padding: 25;");
        resultLabel.setWrapText(true);
        resultLabel.setAlignment(Pos.CENTER);
        
        dialogRoot.getChildren().add(resultLabel);
        
        // Tăng kích thước dialog
        Scene dialogScene = new Scene(dialogRoot, 500, 250);
        dialogScene.setFill(null); // Transparent scene
        resultDialog.setScene(dialogScene);
        
        // Center dialog relative to game window
        double dialogWidth = 500;
        double dialogHeight = 250;
        double gameX = this.getX();
        double gameY = this.getY();
        double gameWidth = this.getWidth();
        double gameHeight = this.getHeight();
        
        resultDialog.setX(gameX + (gameWidth - dialogWidth) / 2);
        resultDialog.setY(gameY + (gameHeight - dialogHeight) / 2);
        
        // Hiển thị dialog
        resultDialog.show();
        
        // Tự đóng sau 5 giây và start next round if available
        javafx.animation.Timeline closeTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(5),
                e -> {
                    resultDialog.close();
                    // After result dialog is closed, start next round if available
                    if (currentRound < TOTAL_ROUNDS) {
                        startPhase(GamePhase.PREPARE_QUESTION, 5);
                    } else {
                        // Show final match summary
                        System.out.println("[GAME] Match completed after " + TOTAL_ROUNDS + " rounds.");
                        if (phaseTimeline != null) {
                            phaseTimeline.stop();
                        }
                        lblQuestionNumber.setText("Trận đấu kết thúc!");
                        lblQuestion.setText("Đã hoàn thành " + TOTAL_ROUNDS + " câu hỏi.");
                        lblTimer.setText("0");
                        disableAnswerButtons();
                        hidePhaseNotification();
                        // Gửi tổng điểm lên server và hiển thị bảng kết quả
                        sendFinalScoreToServer();
                        showFinalMatchResult();
                    }
                }
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
    
    /**
     * Reset per-round state to ensure each round starts cleanly.
     * Does NOT reset myScore or opponentScore.
     * Does NOT remove serverResult (có thể cần dùng lại nếu retry)
     */
    private void resetRoundState() {
        currentQuestion = null;
        selectedAnswer = null;
        answerStartTime = 0L;
        answerEndTime = 0L;
        // KHÔNG xóa serverResult ở đây - để waitForServerResult có thể dùng
        resetButtonStyles(); // Reset button UI to default
        System.out.println("[GAME] Round state reset for round " + (currentRound + 1) + 
                         ", serverResults: " + serverResults.keySet());
    }
    
    /**
     * Hiển thị dialog lỗi khi không nhận được RESULT từ server
     */
    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi kết nối");
        alert.setHeaderText("Không nhận được dữ liệu từ server");
        alert.setContentText(message);
        alert.initOwner(this);
        alert.showAndWait();
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
        // Dừng polling
        if (resultPollingService != null) {
            resultPollingService.cancel();
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
    
    /**
     * Hiệu ứng nhấp nháy cho thông báo khi nhận được dữ liệu từ server
     */
    private void flashNotification(String message) {
        if (phaseNotificationLabel == null) {
            return;
        }
        
        // Hiển thị thông báo mới
        phaseNotificationLabel.setText(message);
        phaseNotificationLabel.setVisible(true);
        phaseNotificationLabel.setManaged(true);
        
        // Hiệu ứng nhấp nháy (fade in/out)
        phaseNotificationLabel.setOpacity(1.0);
        
        // Tạo animation nhấp nháy: fade out -> fade in -> fade out -> fade in -> giữ nguyên
        javafx.animation.Timeline flashTimeline = new javafx.animation.Timeline(
            // Fade out lần 1
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(0), 
                e -> phaseNotificationLabel.setOpacity(1.0)),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(200), 
                e -> phaseNotificationLabel.setOpacity(0.3)),
            // Fade in lần 1
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(400), 
                e -> phaseNotificationLabel.setOpacity(1.0)),
            // Fade out lần 2
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(600), 
                e -> phaseNotificationLabel.setOpacity(0.3)),
            // Fade in lần 2
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(800), 
                e -> phaseNotificationLabel.setOpacity(1.0))
        );
        
        flashTimeline.setCycleCount(1);
        flashTimeline.setOnFinished(e -> {
            // Sau khi nhấp nháy xong, giữ nguyên text và style
            // Không ẩn label, để người dùng có thể thấy thông báo
            phaseNotificationLabel.setOpacity(1.0);
        });
        
        flashTimeline.play();
    }
    
   private void waitForServerResult() {
    final int targetRound = currentRound; // Capture current round để tránh thay đổi
    
    // 1. Kiểm tra ngay: nếu serverResult đã có sẵn cho round hiện tại thì show luôn
    ServerRoundResult initialResult = getServerResult(targetRound);
    if (initialResult != null && initialResult.round == targetRound) {
        System.out.println("[GAME] ServerResult already available for round " + targetRound + ", showing dialog immediately");
        Platform.runLater(() -> showResultDialog());
        return;
    }

    // 2. Nếu chưa có, đợi tối đa 5 giây với retry
    int maxWaitTime = 5000;      // 5 giây
    int checkInterval = 200;     // Kiểm tra mỗi 200ms
    final int[] elapsed = {0};   // Dùng array để có thể modify trong lambda
    final int[] retryCount = {0}; // Đếm số lần retry
    final int maxRetries = 3;     // Tối đa 3 lần retry

    final javafx.animation.Timeline[] checkTimelineRef = new javafx.animation.Timeline[1];
    checkTimelineRef[0] = new javafx.animation.Timeline(
        new javafx.animation.KeyFrame(
            javafx.util.Duration.millis(checkInterval),
            e -> {
                elapsed[0] += checkInterval;

                // Kiểm tra lại serverResult cho round hiện tại
                ServerRoundResult result = getServerResult(targetRound);
                if (result != null && result.round == targetRound) {
                    System.out.println("[GAME] ServerResult received for round " + targetRound + 
                                     " after " + elapsed[0] + "ms, showing dialog");
                    // DỪNG timeline để không chạy tiếp
                    checkTimelineRef[0].stop();
                    // Gọi dialog trên JavaFX thread
                    Platform.runLater(() -> showResultDialog());
                    return;
                }

                // Nếu hết thời gian đợi mà vẫn chưa có kết quả từ server
                if (elapsed[0] >= maxWaitTime) {
                    checkTimelineRef[0].stop();
                    
                    // Retry: yêu cầu server gửi lại RESULT (nếu chưa retry quá nhiều)
                    if (retryCount[0] < maxRetries) {
                        retryCount[0]++;
                        System.out.println("[GAME] Timeout waiting for ServerResult for round " + targetRound + 
                                         " after " + elapsed[0] + "ms, retrying (" + retryCount[0] + "/" + maxRetries + ")");
                        
                        // Reset elapsed và tiếp tục đợi
                        elapsed[0] = 0;
                        checkTimelineRef[0].play();
                    } else {
                        // Đã retry đủ, hiển thị lỗi
                        System.err.println("[GAME] Failed to receive ServerResult for round " + targetRound + 
                                         " after " + maxRetries + " retries. Possible network issue.");
                        Platform.runLater(() -> {
                            // Hiển thị dialog lỗi
                            showErrorDialog("Không nhận được kết quả từ server cho round " + targetRound + 
                                          ". Vui lòng kiểm tra kết nối mạng.");
                        });
                    }
                }
            }
        )
    );

    checkTimelineRef[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
    checkTimelineRef[0].play();
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
    
    private void sendFinalScoreToServer() {
        // Xác định player1 và player2 theo thứ tự alphabet
        String player1, player2;
        int p1Score, p2Score;
        if (username.compareToIgnoreCase(opponent) < 0) {
            player1 = username;
            player2 = opponent;
            p1Score = myScore;
            p2Score = opponentScore;
        } else {
            player1 = opponent;
            player2 = username;
            p1Score = opponentScore;
            p2Score = myScore;
        }
        
        // Gửi lên server: MATCH_END;matchKey;player1;player2;score1;score2
        try {
            String command = "MATCH_END;" + matchKey + ";" + player1 + ";" + player2 + ";" + p1Score + ";" + p2Score;
            System.out.println("[GAME] Sending final score to server: " + command);
            String response = client.sendCommand(command);
            System.out.println("[GAME] Server response: " + response);
        } catch (Exception e) {
            System.err.println("[GAME] Error sending final score to server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showFinalMatchResult() {
        // Dừng polling
        if (resultPollingService != null) {
            resultPollingService.cancel();
        }
        
        // Dừng timeline nếu có
        if (phaseTimeline != null) {
            phaseTimeline.stop();
        }
        
        // Dừng âm thanh nếu đang phát
        if (questionSoundPlayer != null) {
            questionSoundPlayer.stop();
            questionSoundPlayer.dispose();
            questionSoundPlayer = null;
        }
        
        // Xác định người thắng
        String winnerText;
        Color winnerColor;
        if (myScore > opponentScore) {
            winnerText = "Bạn thắng!";
            winnerColor = Color.web("#00AA00"); // Green
        } else if (opponentScore > myScore) {
            winnerText = opponent + " thắng!";
            winnerColor = Color.web("#CC0000"); // Red
        } else {
            winnerText = "Hòa!";
            winnerColor = Color.web("#FF8800"); // Orange
        }
        
        System.out.println("[GAME] Showing final match result: " + username + "=" + myScore + 
                         ", " + opponent + "=" + opponentScore);
        
        // Tạo dialog kết quả cuối trận
        Stage resultDialog = new Stage();
        resultDialog.initModality(Modality.APPLICATION_MODAL);
        resultDialog.initOwner(this);
        resultDialog.initStyle(StageStyle.TRANSPARENT);
        resultDialog.setResizable(false);
        resultDialog.setAlwaysOnTop(true);
        
        // Main container
        VBox dialogRoot = new VBox(25);
        dialogRoot.setPadding(new Insets(40, 50, 40, 50));
        dialogRoot.setAlignment(Pos.CENTER);
        dialogRoot.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f5f5f5 100%); " +
                           "-fx-background-radius: 30; " +
                           "-fx-border-radius: 30; " +
                           "-fx-border-color: #d0d0d0; " +
                           "-fx-border-width: 2; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 20, 0, 0, 8);");
        
        // Title
        Label titleLabel = new Label("Kết thúc trận đấu!");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#2d5016"));
        
        // Separator line
        Separator separator1 = new Separator();
        separator1.setPrefWidth(350);
        separator1.setStyle("-fx-background-color: #d0d0d0;");
        
        // Results container
        VBox resultsContainer = new VBox(15);
        resultsContainer.setAlignment(Pos.CENTER);
        resultsContainer.setPadding(new Insets(10));
        
        // Winner label
        Label winnerLabel = new Label(winnerText);
        winnerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        winnerLabel.setTextFill(winnerColor);
        
        // Score labels
        Label scoreLabel1 = new Label(username + ": " + myScore + " điểm");
        scoreLabel1.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        scoreLabel1.setTextFill(Color.BLACK);
        
        Label scoreLabel2 = new Label(opponent + ": " + opponentScore + " điểm");
        scoreLabel2.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        scoreLabel2.setTextFill(Color.BLACK);
        
        resultsContainer.getChildren().addAll(winnerLabel, scoreLabel1, scoreLabel2);
        
        // Separator line
        Separator separator2 = new Separator();
        separator2.setPrefWidth(350);
        separator2.setStyle("-fx-background-color: #d0d0d0;");
        
        // Exit button
        Button btnExit = new Button("Về lobby");
        btnExit.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        btnExit.setStyle("-fx-background-color: linear-gradient(to bottom, #2d5016 0%, #1a3509 100%); " +
                        "-fx-background-radius: 20; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-pref-width: 180; " +
                        "-fx-pref-height: 45; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);");
        
        // Hover effect
        btnExit.setOnMouseEntered(e -> btnExit.setStyle("-fx-background-color: linear-gradient(to bottom, #3a6520 0%, #2d5016 100%); " +
                                                       "-fx-background-radius: 20; " +
                                                       "-fx-text-fill: white; " +
                                                       "-fx-font-size: 16px; " +
                                                       "-fx-font-weight: bold; " +
                                                       "-fx-pref-width: 180; " +
                                                       "-fx-pref-height: 45; " +
                                                       "-fx-cursor: hand; " +
                                                       "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 3);"));
        btnExit.setOnMouseExited(e -> btnExit.setStyle("-fx-background-color: linear-gradient(to bottom, #2d5016 0%, #1a3509 100%); " +
                                                      "-fx-background-radius: 20; " +
                                                      "-fx-text-fill: white; " +
                                                      "-fx-font-size: 16px; " +
                                                      "-fx-font-weight: bold; " +
                                                      "-fx-pref-width: 180; " +
                                                      "-fx-pref-height: 45; " +
                                                      "-fx-cursor: hand; " +
                                                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);"));
        
        btnExit.setOnAction(e -> {
            resultDialog.close();
            close(); // Close GameFrame
            // Show lobby again
            Platform.runLater(() -> {
                for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                    if (window instanceof Stage) {
                        Stage stage = (Stage) window;
                        if (stage.getTitle() != null && stage.getTitle().equals("Lobby")) {
                            stage.show();
                            break;
                        }
                    }
                }
            });
        });
        
        dialogRoot.getChildren().addAll(titleLabel, separator1, resultsContainer, separator2, btnExit);
        
        // Create a StackPane with semi-transparent background overlay
        StackPane dialogStackPane = new StackPane();
        dialogStackPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        dialogStackPane.getChildren().add(dialogRoot);
        dialogStackPane.setAlignment(Pos.CENTER);
        
        Scene dialogScene = new Scene(dialogStackPane);
        dialogScene.setFill(Color.TRANSPARENT);
        resultDialog.setScene(dialogScene);
        
        // Set dialog size
        if (this.isShowing()) {
            double gameWidth = this.getWidth();
            double gameHeight = this.getHeight();
            double dialogWidth = Math.max(400, gameWidth * 0.6);
            double dialogHeight = Math.max(300, gameHeight * 0.6);
            
            dialogScene.setRoot(dialogStackPane);
            resultDialog.setWidth(dialogWidth);
            resultDialog.setHeight(dialogHeight);
            
            // Center dialog relative to game window
            double gameX = this.getX();
            double gameY = this.getY();
            resultDialog.setX(gameX + (gameWidth - dialogWidth) / 2);
            resultDialog.setY(gameY + (gameHeight - dialogHeight) / 2);
        } else {
            // Fallback size if window not showing
            dialogScene.setRoot(dialogStackPane);
            resultDialog.setWidth(500);
            resultDialog.setHeight(400);
            resultDialog.centerOnScreen();
        }
        
        resultDialog.show();
    }
    
    private void sendAnswerToServer() {
        if (currentQuestion == null) {
            System.err.println("[GAME] Cannot send answer: currentQuestion is null");
            return;
        }
        
        // Tính thời gian trả lời
        long answerTimeMs = 0;
        if (selectedAnswer == null) {
            // Không trả lời: thời gian = 10 giây (tối đa)
            answerTimeMs = 10000;
            System.out.println("[GAME] No answer selected, using 10s as time");
        } else if (answerStartTime > 0 && answerEndTime > 0) {
            // Đã trả lời: tính từ start đến end
            answerTimeMs = answerEndTime - answerStartTime;
            if (answerTimeMs > 10000) {
                answerTimeMs = 10000;
            }
        } else if (answerStartTime > 0) {
            // Đã bắt đầu nhưng chưa có endTime (hết thời gian)
            answerTimeMs = 10000;
            System.out.println("[GAME] Answer phase ended without selection, using 10s");
        }
        
        // Xác định đáp án đúng/sai
        String[] answers = currentQuestion.getAnswers();
        String correctAnswer = answers[currentQuestion.getCorrectIndex()];
        boolean isCorrect = selectedAnswer != null && selectedAnswer.equals(correctAnswer);
        String status = isCorrect ? "CORRECT" : "WRONG";
        
        // Gửi lên server: ANSWER_RESULT;matchKey;username;round;status;timeMs
        try {
            String command = "ANSWER_RESULT;" + matchKey + ";" + username + ";" + 
                           currentRound + ";" + status + ";" + answerTimeMs;
            System.out.println("[GAME] Sending answer to server: " + command);
            String response = client.sendCommand(command);
            System.out.println("[GAME] Server response: " + response);
        } catch (Exception e) {
            System.err.println("[GAME] Error sending answer to server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
