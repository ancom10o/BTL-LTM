package com.mycompany.client;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class PracticeFrame extends Stage {
    
    // Reference to lobby window
    private Stage lobbyWindow = null;
    
    // Sound data structure
    private static class SoundData {
        String id;
        String displayName;
        String category;
        String audioFile;
        
        SoundData(String id, String displayName, String category, String audioFile) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
            this.audioFile = audioFile;
        }
    }
    
    // Question data
    private static class Question {
        SoundData sound;
        String questionText;
        List<String> options;
        String correctAnswer;
        
        Question(SoundData sound, String questionText, List<String> options, String correctAnswer) {
            this.sound = sound;
            this.questionText = questionText;
            this.options = options;
            this.correctAnswer = correctAnswer;
        }
    }
    
    private final List<SoundData> allSounds = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int correctAnswers = 0;
    private long questionStartTime = 0;
    private long answerTime = 0;
    
    // UI Components
    private Label lblTimer;
    private Label lblQuestionNumber;
    private Label lblQuestion;
    private Label lblPhase;
    private Label lblScore;
    private Button btnAnswer1;
    private Button btnAnswer2;
    private Button btnAnswer3;
    private Button btnAnswer4;
    private ImageView clockImageView;
    
    // Game state
    private enum GamePhase {
        PREPARATION,    // P1: 5s chuẩn bị
        READING,        // P2: 5s đọc câu hỏi
        ANSWERING,      // P3: 10s phát âm thanh và trả lời
        RESULT          // P4: Hiện kết quả
    }
    
    private GamePhase currentPhase = GamePhase.PREPARATION;
    private Timeline phaseTimer;
    private MediaPlayer mediaPlayer;
    private String selectedAnswer = null;
    private boolean answerSubmitted = false;
    
    public PracticeFrame() {
        this(null);
    }
    
    public PracticeFrame(Stage lobbyWindow) {
        super();
        this.lobbyWindow = lobbyWindow;
        setTitle("Chế độ luyện tập");
        setResizable(true);
        setOnCloseRequest(this::handleClose);
        setOnHidden(e -> showLobbyAgain()); // Show lobby when practice is hidden
        
        // Load sounds from CSV
        loadSoundsFromCSV();
        
        // Generate 10 random questions
        generateQuestions();
        
        // Initialize UI
        initializeUI();
        
        // Start first question and stop background music
        setOnShown(e -> {
            SoundManager.getInstance().stopBackgroundMusic();
            startQuestion(0);
        });
    }
    
    private void showLobbyAgain() {
        // Show lobby again when practice is closed and resume background music
        javafx.application.Platform.runLater(() -> {
            if (lobbyWindow != null && lobbyWindow.isShowing() == false) {
                // Use saved reference if available
                lobbyWindow.show();
                lobbyWindow.toFront();
            } else {
                // Fallback: Find all stages and show the lobby if it exists
                for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                    if (window instanceof Stage) {
                        Stage stage = (Stage) window;
                        if (stage.getTitle() != null && stage.getTitle().equals("Lobby")) {
                            stage.show();
                            stage.toFront();
                            break;
                        }
                    }
                }
            }
            // Resume background music when returning to lobby
            if (SoundManager.getInstance().isSoundEnabled()) {
                SoundManager.getInstance().playBackgroundMusic("/sounds/notification/back_ground.wav");
            }
        });
    }
    
    private void loadSoundsFromCSV() {
        try {
            InputStream csvStream = getClass().getResourceAsStream("/Sound.csv");
            if (csvStream == null) {
                System.err.println("Cannot find Sound.csv");
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, "UTF-8"));
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String id = parts[0].trim();
                    String displayName = parts[1].trim();
                    String category = parts[2].trim();
                    String audioFile = parts[3].trim();
                    
                    allSounds.add(new SoundData(id, displayName, category, audioFile));
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void generateQuestions() {
        Random random = new Random();
        String[] categories = {"ANIMAL", "INSTRUMENT", "VEHICLE"};
        String[] questionTexts = {
            "Đây là âm thanh của con vật gì?",
            "Đây là âm thanh của nhạc cụ nào?",
            "Đây là âm thanh của phương tiện nào?"
        };
        
        Map<String, String> categoryToQuestion = new HashMap<>();
        categoryToQuestion.put("ANIMAL", questionTexts[0]);
        categoryToQuestion.put("INSTRUMENT", questionTexts[1]);
        categoryToQuestion.put("VEHICLE", questionTexts[2]);
        
        for (int i = 0; i < 10; i++) {
            // Random category
            String category = categories[random.nextInt(categories.length)];
            
            // Get all sounds of this category
            List<SoundData> categorySounds = allSounds.stream()
                .filter(s -> s.category.equals(category))
                .collect(Collectors.toList());
            
            if (categorySounds.isEmpty()) {
                i--; // Retry this question
                continue;
            }
            
            // Random sound from category (correct answer)
            SoundData correctSound = categorySounds.get(random.nextInt(categorySounds.size()));
            
            // Generate 3 wrong answers from same category
            List<SoundData> wrongOptions = categorySounds.stream()
                .filter(s -> !s.id.equals(correctSound.id))
                .collect(Collectors.toList());
            Collections.shuffle(wrongOptions);
            
            // Ensure we have exactly 4 options
            List<String> options = new ArrayList<>();
            options.add(correctSound.displayName); // Add correct answer
            
            // Add wrong answers from same category
            int wrongCount = Math.min(3, wrongOptions.size());
            for (int j = 0; j < wrongCount; j++) {
                options.add(wrongOptions.get(j).displayName);
            }
            
            // If we don't have enough options (less than 4), fill with more from same category (with repetition if needed)
            while (options.size() < 4 && wrongOptions.size() > 0) {
                int randomIndex = random.nextInt(wrongOptions.size());
                String option = wrongOptions.get(randomIndex).displayName;
                if (!options.contains(option)) {
                    options.add(option);
                } else if (wrongOptions.size() == 1) {
                    // If only one option left and we still need more, add it anyway
                    options.add(option);
                }
            }
            
            // Shuffle options to randomize position
            Collections.shuffle(options);
            
            // Ensure we have exactly 4 options
            while (options.size() < 4) {
                // Fallback: if somehow we don't have 4, add from all sounds
                SoundData fallback = allSounds.get(random.nextInt(allSounds.size()));
                if (!options.contains(fallback.displayName)) {
                    options.add(fallback.displayName);
                }
            }
            
            // Trim to exactly 4
            if (options.size() > 4) {
                options = options.subList(0, 4);
            }
            
            questions.add(new Question(
                correctSound,
                categoryToQuestion.get(category),
                options,
                correctSound.displayName
            ));
        }
    }
    
    private void initializeUI() {
        // Load background image
        InputStream bgStream = getClass().getResourceAsStream("/images/background/match_background.jpg");
        
        // Window size
        double windowWidth = 840;
        double windowHeight = 560;
        
        // Main container with background
        StackPane root = new StackPane();
        
        ImageView backgroundImageView;
        Image bgImage;
        if (bgStream != null) {
            bgImage = new Image(bgStream);
            backgroundImageView = new ImageView(bgImage);
            backgroundImageView.setPreserveRatio(true);
            backgroundImageView.setSmooth(true);
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
        
        // Bind background image size
        final ImageView finalImageView = backgroundImageView;
        final Image finalImage = bgImage;
        if (finalImageView != null && finalImage != null) {
            scene.widthProperty().addListener((obs, oldVal, newVal) -> updateImageSize(finalImageView, finalImage, scene));
            scene.heightProperty().addListener((obs, oldVal, newVal) -> updateImageSize(finalImageView, finalImage, scene));
            updateImageSize(finalImageView, finalImage, scene);
        }
        
        centerOnScreen();
    }
    
    private VBox buildMainContent() {
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.setAlignment(Pos.TOP_CENTER);
        
        // Top bar
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
        
        // Back button
        VBox btnBackContainer = new VBox();
        btnBackContainer.setAlignment(Pos.TOP_LEFT);
        btnBackContainer.setPadding(new Insets(10, 0, 0, 0));
        
        Button btnBack = new Button();
        InputStream outStream = getClass().getResourceAsStream("/images/background/out.png");
        ImageView outImageView;
        if (outStream != null) {
            Image outImage = new Image(outStream);
            outImageView = new ImageView(outImage);
            outImageView.setFitWidth(70);
            outImageView.setFitHeight(80);
            outImageView.setPreserveRatio(true);
            btnBack.setGraphic(outImageView);
            
            final ImageView finalOutImageView = outImageView;
            btnBack.setOnMouseEntered(e -> {
                finalOutImageView.setFitWidth(77);
                finalOutImageView.setFitHeight(88);
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
        lblScore = new Label("0/0");
        lblScore.setFont(Font.font("Arial", 14));
        lblScore.setTextFill(Color.BLACK);
        scoreArea.getChildren().addAll(lblScoreLabel, lblScore);
        
        // Clock area
        StackPane clockArea = new StackPane();
        clockArea.setAlignment(Pos.CENTER);
        
        InputStream clockStream = getClass().getResourceAsStream("/images/background/Dongho_match_ background.png");
        if (clockStream != null) {
            Image clockImage = new Image(clockStream);
            clockImageView = new ImageView(clockImage);
            clockImageView.setFitWidth(150);
            clockImageView.setFitHeight(150);
            clockImageView.setPreserveRatio(true);
            clockArea.getChildren().add(clockImageView);
        }
        
        lblTimer = new Label("5");
        lblTimer.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        lblTimer.setTextFill(Color.RED);
        lblTimer.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
        clockArea.getChildren().add(lblTimer);
        
        // Spacers
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
        questionArea.setPadding(new Insets(30, 40, 20, 40));
        
        // Phase label
        lblPhase = new Label("Chuẩn bị...");
        lblPhase.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        lblPhase.setTextFill(Color.BLUE);
        
        // Question number
        lblQuestionNumber = new Label("Câu 1:");
        lblQuestionNumber.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        lblQuestionNumber.setTextFill(Color.BLACK);
        lblQuestionNumber.setStyle("-fx-background-color: rgba(220, 220, 220, 0.9); " +
                                  "-fx-background-radius: 12; " +
                                  "-fx-padding: 12 25;");
        
        // Question text
        lblQuestion = new Label("");
        lblQuestion.setFont(Font.font("Arial", 18));
        lblQuestion.setTextFill(Color.BLACK);
        lblQuestion.setWrapText(true);
        lblQuestion.setMaxWidth(600);
        lblQuestion.setStyle("-fx-background-color: rgba(220, 220, 220, 0.9); " +
                            "-fx-background-radius: 12; " +
                            "-fx-padding: 18 30;");
        
        questionArea.getChildren().addAll(lblPhase, lblQuestionNumber, lblQuestion);
        
        return questionArea;
    }
    
    private GridPane buildAnswerGrid() {
        GridPane answerGrid = new GridPane();
        answerGrid.setHgap(25);
        answerGrid.setVgap(25);
        answerGrid.setAlignment(Pos.CENTER);
        answerGrid.setPadding(new Insets(30, 40, 40, 40));
        
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
        
        btnAnswer1 = new Button("");
        btnAnswer1.setStyle(buttonStyle);
        btnAnswer1.setOnMouseEntered(e -> btnAnswer1.setStyle(buttonHoverStyle));
        btnAnswer1.setOnMouseExited(e -> btnAnswer1.setStyle(buttonStyle));
        btnAnswer1.setOnAction(e -> handleAnswer(btnAnswer1.getText()));
        
        btnAnswer2 = new Button("");
        btnAnswer2.setStyle(buttonStyle);
        btnAnswer2.setOnMouseEntered(e -> btnAnswer2.setStyle(buttonHoverStyle));
        btnAnswer2.setOnMouseExited(e -> btnAnswer2.setStyle(buttonStyle));
        btnAnswer2.setOnAction(e -> handleAnswer(btnAnswer2.getText()));
        
        btnAnswer3 = new Button("");
        btnAnswer3.setStyle(buttonStyle);
        btnAnswer3.setOnMouseEntered(e -> btnAnswer3.setStyle(buttonHoverStyle));
        btnAnswer3.setOnMouseExited(e -> btnAnswer3.setStyle(buttonStyle));
        btnAnswer3.setOnAction(e -> handleAnswer(btnAnswer3.getText()));
        
        btnAnswer4 = new Button("");
        btnAnswer4.setStyle(buttonStyle);
        btnAnswer4.setOnMouseEntered(e -> btnAnswer4.setStyle(buttonHoverStyle));
        btnAnswer4.setOnMouseExited(e -> btnAnswer4.setStyle(buttonStyle));
        btnAnswer4.setOnAction(e -> handleAnswer(btnAnswer4.getText()));
        
        answerGrid.add(btnAnswer1, 0, 0);
        answerGrid.add(btnAnswer2, 1, 0);
        answerGrid.add(btnAnswer3, 0, 1);
        answerGrid.add(btnAnswer4, 1, 1);
        
        return answerGrid;
    }
    
    private void startQuestion(int questionIndex) {
        if (questionIndex >= questions.size()) {
            showFinalResults();
            return;
        }
        
        currentQuestionIndex = questionIndex;
        Question question = questions.get(questionIndex);
        answerSubmitted = false;
        selectedAnswer = null;
        
        // Update question display
        lblQuestionNumber.setText("Câu " + (questionIndex + 1) + ":");
        lblQuestion.setText(question.questionText);
        
        // Update answer buttons
        List<String> options = question.options;
        btnAnswer1.setText(options.size() > 0 ? options.get(0) : "");
        btnAnswer2.setText(options.size() > 1 ? options.get(1) : "");
        btnAnswer3.setText(options.size() > 2 ? options.get(2) : "");
        btnAnswer4.setText(options.size() > 3 ? options.get(3) : "");
        
        // Disable buttons initially
        setButtonsEnabled(false);
        
        // Start phases - only show preparation phase for the first question
        if (questionIndex == 0) {
            startPhase1(); // First question: show preparation phase
        } else {
            startPhase2(); // Subsequent questions: skip preparation, go directly to reading phase
        }
    }
    
    private void startPhase1() {
        // P1: 5s chuẩn bị
        currentPhase = GamePhase.PREPARATION;
        lblPhase.setText("Chuẩn bị...");
        lblPhase.setTextFill(Color.BLUE);
        lblTimer.setText("5");
        lblTimer.setTextFill(Color.BLUE);
        
        // Clear question and answers
        lblQuestion.setText("");
        btnAnswer1.setText("");
        btnAnswer2.setText("");
        btnAnswer3.setText("");
        btnAnswer4.setText("");
        
        int[] countdown = {5};
        phaseTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown[0]--;
            if (countdown[0] > 0) {
                lblTimer.setText(String.valueOf(countdown[0]));
            } else {
                phaseTimer.stop();
                startPhase2();
            }
        }));
        phaseTimer.setCycleCount(5);
        phaseTimer.play();
    }
    
    private void startPhase2() {
        // P2: 5s đọc câu hỏi
        currentPhase = GamePhase.READING;
        lblPhase.setText("Đọc câu hỏi...");
        lblPhase.setTextFill(Color.ORANGE);
        lblTimer.setText("5");
        lblTimer.setTextFill(Color.ORANGE);
        
        Question question = questions.get(currentQuestionIndex);
        lblQuestion.setText(question.questionText);
        
        // Display answer options in Phase 2
        List<String> options = question.options;
        btnAnswer1.setText(options.size() > 0 ? options.get(0) : "");
        btnAnswer2.setText(options.size() > 1 ? options.get(1) : "");
        btnAnswer3.setText(options.size() > 2 ? options.get(2) : "");
        btnAnswer4.setText(options.size() > 3 ? options.get(3) : "");
        
        // Keep buttons disabled in Phase 2
        setButtonsEnabled(false);
        
        int[] countdown = {5};
        phaseTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown[0]--;
            if (countdown[0] > 0) {
                lblTimer.setText(String.valueOf(countdown[0]));
            } else {
                phaseTimer.stop();
                startPhase3();
            }
        }));
        phaseTimer.setCycleCount(5);
        phaseTimer.play();
    }
    
    private void startPhase3() {
        // P3: 10s phát âm thanh và trả lời
        currentPhase = GamePhase.ANSWERING;
        lblPhase.setText("Trả lời...");
        lblPhase.setTextFill(Color.GREEN);
        lblTimer.setText("10");
        lblTimer.setTextFill(Color.RED);
        
        Question question = questions.get(currentQuestionIndex);
        questionStartTime = System.currentTimeMillis();
        
        // Enable buttons
        setButtonsEnabled(true);
        
        // Play sound
        playSound(question.sound.audioFile);
        
        int[] countdown = {10};
        phaseTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown[0]--;
            if (countdown[0] > 0) {
                lblTimer.setText(String.valueOf(countdown[0]));
            } else {
                phaseTimer.stop();
                // Auto submit if no answer
                if (!answerSubmitted) {
                    handleAnswer(null);
                }
            }
        }));
        phaseTimer.setCycleCount(10);
        phaseTimer.play();
    }
    
    private void playSound(String audioFile) {
        // Stop previous sound if any
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        
        // Use SoundManager to play sound (will check if sound is enabled)
        mediaPlayer = SoundManager.getInstance().playSound(audioFile, 0.7);
    }
    
    private void handleAnswer(String answer) {
        if (answerSubmitted) return;
        
        answerSubmitted = true;
        selectedAnswer = answer;
        answerTime = System.currentTimeMillis() - questionStartTime;
        
        // Stop timer and sound
        if (phaseTimer != null) {
            phaseTimer.stop();
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        
        // Don't disable buttons - keep them visible but non-clickable
        // We'll handle this in Phase 4
        
        // Move to result phase
        startPhase4();
    }
    
    private void startPhase4() {
        // P4: Hiện kết quả
        currentPhase = GamePhase.RESULT;
        
        Question question = questions.get(currentQuestionIndex);
        boolean isCorrect = question.correctAnswer.equals(selectedAnswer);
        
        if (isCorrect) {
            correctAnswers++;
            lblPhase.setText("✓ Đúng!");
            lblPhase.setTextFill(Color.GREEN);
        } else {
            lblPhase.setText("✗ Sai!");
            lblPhase.setTextFill(Color.RED);
        }
        
        // Update score
        lblScore.setText(correctAnswers + "/" + (currentQuestionIndex + 1));
        
        // Show result details
        String resultText = String.format(
            "Đáp án: %s\nThời gian: %.1f giây\nTổng số câu đúng: %d/%d",
            question.correctAnswer,
            answerTime / 1000.0,
            correctAnswers,
            currentQuestionIndex + 1
        );
        
        lblQuestion.setText(resultText);
        
        // Highlight correct answer
        highlightAnswer(question.correctAnswer, isCorrect);
        
        // Countdown timer for result phase (3 seconds)
        int[] countdown = {3};
        lblTimer.setText(String.valueOf(countdown[0]));
        lblTimer.setTextFill(Color.GRAY);
        
        Timeline resultTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown[0]--;
            if (countdown[0] > 0) {
                lblTimer.setText(String.valueOf(countdown[0]));
            } else {
                resetAnswerButtons();
                startQuestion(currentQuestionIndex + 1);
            }
        }));
        resultTimer.setCycleCount(3);
        resultTimer.play();
    }
    
    private void highlightAnswer(String correctAnswer, boolean isCorrect) {
        String greenStyle = "-fx-background-color: rgba(0, 255, 0, 1.0); " +
                          "-fx-background-radius: 15; " +
                          "-fx-text-fill: black; " +
                          "-fx-font-size: 18px; " +
                          "-fx-font-weight: bold; " +
                          "-fx-pref-width: 220; " +
                          "-fx-pref-height: 70; " +
                          "-fx-opacity: 1.0;";
        
        String redStyle = "-fx-background-color: rgba(255, 0, 0, 1.0); " +
                         "-fx-background-radius: 15; " +
                         "-fx-text-fill: black; " +
                         "-fx-font-size: 18px; " +
                         "-fx-font-weight: bold; " +
                         "-fx-pref-width: 220; " +
                         "-fx-pref-height: 70; " +
                         "-fx-opacity: 1.0;";
        
        String defaultStyle = "-fx-background-color: rgba(220, 220, 220, 0.9); " +
                            "-fx-background-radius: 15; " +
                            "-fx-text-fill: black; " +
                            "-fx-font-size: 18px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-pref-width: 220; " +
                            "-fx-pref-height: 70; " +
                            "-fx-opacity: 1.0;";
        
        // First, set all buttons to default style
        btnAnswer1.setStyle(defaultStyle);
        btnAnswer2.setStyle(defaultStyle);
        btnAnswer3.setStyle(defaultStyle);
        btnAnswer4.setStyle(defaultStyle);
        
        // Then, highlight correct answer in green (always)
        if (btnAnswer1.getText().equals(correctAnswer)) {
            btnAnswer1.setStyle(greenStyle);
        }
        if (btnAnswer2.getText().equals(correctAnswer)) {
            btnAnswer2.setStyle(greenStyle);
        }
        if (btnAnswer3.getText().equals(correctAnswer)) {
            btnAnswer3.setStyle(greenStyle);
        }
        if (btnAnswer4.getText().equals(correctAnswer)) {
            btnAnswer4.setStyle(greenStyle);
        }
        
        // Then, if answered incorrectly, highlight selected wrong answer in red
        // (but only if it's not the correct answer)
        if (!isCorrect && selectedAnswer != null && !selectedAnswer.equals(correctAnswer)) {
            if (btnAnswer1.getText().equals(selectedAnswer)) {
                btnAnswer1.setStyle(redStyle);
            }
            if (btnAnswer2.getText().equals(selectedAnswer)) {
                btnAnswer2.setStyle(redStyle);
            }
            if (btnAnswer3.getText().equals(selectedAnswer)) {
                btnAnswer3.setStyle(redStyle);
            }
            if (btnAnswer4.getText().equals(selectedAnswer)) {
                btnAnswer4.setStyle(redStyle);
            }
        }
        
        // Disable buttons to prevent further clicks
        setButtonsEnabled(false);
    }
    
    private void resetAnswerButtons() {
        String buttonStyle = "-fx-background-color: rgba(220, 220, 220, 0.9); " +
                           "-fx-background-radius: 15; " +
                           "-fx-text-fill: black; " +
                           "-fx-font-size: 18px; " +
                           "-fx-font-weight: bold; " +
                           "-fx-pref-width: 220; " +
                           "-fx-pref-height: 70; " +
                           "-fx-opacity: 1.0;";
        
        btnAnswer1.setStyle(buttonStyle);
        btnAnswer2.setStyle(buttonStyle);
        btnAnswer3.setStyle(buttonStyle);
        btnAnswer4.setStyle(buttonStyle);
        
        // Re-enable buttons for next question
        setButtonsEnabled(true);
    }
    
    private void setButtonsEnabled(boolean enabled) {
        btnAnswer1.setDisable(!enabled);
        btnAnswer2.setDisable(!enabled);
        btnAnswer3.setDisable(!enabled);
        btnAnswer4.setDisable(!enabled);
    }
    
    private void showFinalResults() {
        // Stop any timers
        if (phaseTimer != null) {
            phaseTimer.stop();
            phaseTimer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        
        // Calculate percentage
        double percentage = (correctAnswers * 100.0) / 10.0;
        
        System.out.println("Showing final results: " + correctAnswers + "/10 = " + percentage + "%");
        
        // Create result dialog - no title bar, just a rounded frame
        Stage resultDialog = new Stage();
        resultDialog.initModality(Modality.APPLICATION_MODAL);
        resultDialog.initOwner(this);
        resultDialog.initStyle(javafx.stage.StageStyle.TRANSPARENT); // Remove title bar and close button
        resultDialog.setResizable(false);
        resultDialog.setAlwaysOnTop(true);
        
        // Main container with beautiful rounded corners and shadow (no title bar)
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
        Label titleLabel = new Label("Kết thúc luyện tập!");
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
        
        // Number of correct answers
        Label correctLabel = new Label("Bạn đã làm được: " + correctAnswers + " câu");
        correctLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        correctLabel.setTextFill(Color.BLACK);
        
        // Percentage
        Color percentageColor;
        if (percentage >= 80) {
            percentageColor = Color.web("#00AA00"); // Green
        } else if (percentage >= 60) {
            percentageColor = Color.web("#FF8800"); // Orange
        } else if (percentage >= 40) {
            percentageColor = Color.web("#FF6600"); // Dark Orange
        } else {
            percentageColor = Color.web("#CC0000"); // Red
        }
        
        Label percentageLabel = new Label("Tỉ lệ đúng: " + String.format("%.1f", percentage) + "%");
        percentageLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        percentageLabel.setTextFill(percentageColor);
        
        resultsContainer.getChildren().addAll(correctLabel, percentageLabel);
        
        // Separator line
        Separator separator2 = new Separator();
        separator2.setPrefWidth(350);
        separator2.setStyle("-fx-background-color: #d0d0d0;");
        
        // Exit button with beautiful style
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
            close(); // Close PracticeFrame to return to lobby
            // Show lobby again - find and show the lobby window
            javafx.application.Platform.runLater(() -> {
                // Find all stages and show the lobby if it exists
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
        dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        resultDialog.setScene(dialogScene);
        
        // Set dialog size to 60% of practice window
        if (this.isShowing()) {
            double practiceWidth = this.getWidth();
            double practiceHeight = this.getHeight();
            double dialogWidth = practiceWidth * 0.6;
            double dialogHeight = practiceHeight * 0.6;
            
            // Ensure minimum size
            dialogWidth = Math.max(dialogWidth, 400);
            dialogHeight = Math.max(dialogHeight, 300);
            
            resultDialog.setWidth(dialogWidth);
            resultDialog.setHeight(dialogHeight);
            
            // Center dialog on practice window
            resultDialog.setX(this.getX() + (practiceWidth - dialogWidth) / 2);
            resultDialog.setY(this.getY() + (practiceHeight - dialogHeight) / 2);
        } else {
            resultDialog.setWidth(500);
            resultDialog.setHeight(350);
            resultDialog.centerOnScreen();
        }
        
        // Ensure dialog is visible and on top
        resultDialog.setAlwaysOnTop(true);
        resultDialog.toFront();
        
        // Show dialog - use Platform.runLater to ensure it's on JavaFX thread
        javafx.application.Platform.runLater(() -> {
            try {
                resultDialog.showAndWait();
            } catch (Exception ex) {
                System.err.println("Error showing result dialog: " + ex.getMessage());
                ex.printStackTrace();
                // Fallback: just close practice frame
                close();
            }
        });
    }
    
    private void updateImageSize(ImageView imageView, Image image, Scene scene) {
        double sceneWidth = scene.getWidth();
        double sceneHeight = scene.getHeight();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        
        double scaleX = sceneWidth / imageWidth;
        double scaleY = sceneHeight / imageHeight;
        double scale = Math.max(scaleX, scaleY);
        
        imageView.setFitWidth(imageWidth * scale);
        imageView.setFitHeight(imageHeight * scale);
    }
    
    private void showExitConfirmDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT); // Remove title bar and close button
        dialog.setResizable(false);
        
        VBox dialogRoot = new VBox(20);
        dialogRoot.setPadding(new Insets(30));
        dialogRoot.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f5f5f5 100%); " +
                           "-fx-background-radius: 30; " +
                           "-fx-border-radius: 30; " +
                           "-fx-border-color: #d0d0d0; " +
                           "-fx-border-width: 2; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 20, 0, 0, 8);");
        dialogRoot.setAlignment(Pos.CENTER);
        
        Label questionLabel = new Label("Bạn có muốn thoát luyện tập không?");
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
            if (phaseTimer != null) {
                phaseTimer.stop();
                phaseTimer = null;
            }
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            // Show lobby first, then close practice
            showLobbyAgain();
            close();
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
        
        // Create a StackPane with semi-transparent background overlay
        StackPane dialogStackPane = new StackPane();
        dialogStackPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        dialogStackPane.getChildren().add(dialogRoot);
        dialogStackPane.setAlignment(Pos.CENTER);
        
        Scene dialogScene = new Scene(dialogStackPane);
        dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(dialogScene);
        
        // Set dialog size to 30% of practice window
        if (this.isShowing()) {
            double practiceWidth = this.getWidth();
            double practiceHeight = this.getHeight();
            double dialogWidth = practiceWidth * 0.3;
            double dialogHeight = practiceHeight * 0.3;
            
            // Ensure minimum size
            dialogWidth = Math.max(dialogWidth, 250);
            dialogHeight = Math.max(dialogHeight, 150);
            
            dialog.setWidth(dialogWidth);
            dialog.setHeight(dialogHeight);
            
            // Center dialog on practice window
            dialog.setX(this.getX() + (practiceWidth - dialogWidth) / 2);
            dialog.setY(this.getY() + (practiceHeight - dialogHeight) / 2);
        } else {
            dialog.setWidth(350);
            dialog.setHeight(200);
            dialog.centerOnScreen();
        }
        
        dialog.showAndWait();
    }
    
    private void handleClose(WindowEvent e) {
        if (phaseTimer != null) {
            phaseTimer.stop();
            phaseTimer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        // Show lobby when window is closed (e.g., by clicking X button)
        // Don't consume the event, let it close normally
        showLobbyAgain();
    }
}
