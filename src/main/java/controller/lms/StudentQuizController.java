package controller.lms;

import entities.Answer;
import entities.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Clipboard;
import javafx.animation.Animation;
import javafx.util.Duration;
import service.lms.StudentQuizService;
import service.lms.StudentQuizService.*;
import util.AppNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * JavaFX Controller for student quiz interactions
 * Handles:
 * - Displaying available quizzes
 * - Quiz taking interface with timer
 * - Results display
 */
public class StudentQuizController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(StudentQuizController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /* ===== Quiz List Screen ===== */
    @FXML private javafx.scene.layout.FlowPane quizListFlow;
    @FXML private Label emptyQuizzesLabel;
    @FXML private VBox emptyQuizzesBox;
    @FXML private VBox quizListScreen;
    @FXML private Label totalQuizzesLabel;
    @FXML private Label pendingQuizzesLabel;
    @FXML private Label completedQuizzesLabel;

    /* ===== Quiz Taking Screen ===== */
    @FXML private VBox quizTakingScreen;
    @FXML private Label quizTitleLabel;
    @FXML private Label quizDescriptionLabel;
    @FXML private Label progressLabel;  // e.g., "Question 3 of 10"
    @FXML private ProgressBar quizProgressBar;
    @FXML private Label saveStatusLabel;
    @FXML private javafx.scene.layout.FlowPane questionNavigator;
    @FXML private Label timerLabel;
    @FXML private ScrollPane questionsScrollPane;
    @FXML private VBox questionsContainer;
    @FXML private HBox navigationBox;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Button submitButton;

    /* ===== Results Screen ===== */
    @FXML private ScrollPane resultsScreen;
    @FXML private Label resultsTitleLabel;
    @FXML private Label resultsDescriptionLabel;
    @FXML private Label scoreLabel;  // e.g., "15/20 (75%)"
    @FXML private Label passStatusLabel;
    @FXML private VBox questionReviewBox;
    @FXML private Button backToListButton;

    private final StudentQuizService quizService = new StudentQuizService();
    private final ObservableList<QuizInfo> quizItems = FXCollections.observableArrayList();
    private User currentStudent;
    private int currentQuestionIndex = 0;
    private QuizDetail currentQuiz;
    private List<QuestionDetail> sortedQuestions;
    private QuizResultDetail currentResults;
    private Timeline timerTimeline;
    private Timeline screenshotTimeline;
    private ChangeListener<Boolean> focusListener;
    private final Map<Integer, String> textAnswers = new HashMap<>();
    private final Map<Integer, Integer> selectedChoiceAnswers = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize screens visibility
        showScreen(quizListScreen);

        if (quizListScreen != null) {
            quizListScreen.setMaxWidth(Double.MAX_VALUE);
            quizListScreen.setAlignment(Pos.TOP_LEFT);
        }


        // Set button actions
        if (previousButton != null) {
            previousButton.setOnAction(e -> showPreviousQuestion());
        }
        if (nextButton != null) {
            nextButton.setOnAction(e -> showNextQuestion());
        }
        if (submitButton != null) {
            submitButton.setOnAction(e -> submitAnswersConfirmation());
        }
        if (backToListButton != null) {
            backToListButton.setOnAction(e -> showQuizList());
        }
    }

    /**
     * Initialize controller with student user
     */
    public void setStudent(User student) {
        this.currentStudent = student;
        loadQuizList();
    }

    // ========== QUIZ LIST SCREEN ==========

    /**
     * Load and display available quizzes
     */
    private void loadQuizList() {
        quizItems.clear();

        List<QuizInfo> quizzes = quizService.getAvailableQuizzesForStudent(currentStudent.getId());
        updateQuizSummary(quizzes);

        if (quizzes.isEmpty()) {
            if (quizListFlow != null) {
                quizListFlow.setVisible(false);
                quizListFlow.setManaged(false);
            }
            if (emptyQuizzesBox != null) {
                emptyQuizzesBox.setVisible(true);
                emptyQuizzesBox.setManaged(true);
            }
            emptyQuizzesLabel.setVisible(true);
            emptyQuizzesLabel.setManaged(true);
            return;
        }

        if (emptyQuizzesBox != null) {
            emptyQuizzesBox.setVisible(false);
            emptyQuizzesBox.setManaged(false);
        }
        emptyQuizzesLabel.setVisible(false);
        emptyQuizzesLabel.setManaged(false);
        if (quizListFlow != null) {
            quizListFlow.setVisible(true);
            quizListFlow.setManaged(true);
            quizListFlow.getChildren().clear();
            for (QuizInfo quiz : quizzes) {
                VBox card = createQuizCard(quiz);
                card.setPrefWidth(340);
                card.setMinWidth(300);
                quizListFlow.getChildren().add(card);
            }
        }
    }

    private void updateQuizSummary(List<QuizInfo> quizzes) {
        int total = quizzes == null ? 0 : quizzes.size();
        long completed = quizzes == null ? 0 : quizzes.stream().filter(q -> q.alreadyTaken).count();
        int pending = total - (int) completed;
        if (totalQuizzesLabel != null) {
            totalQuizzesLabel.setText(String.valueOf(total));
        }
        if (pendingQuizzesLabel != null) {
            pendingQuizzesLabel.setText(String.valueOf(pending));
        }
        if (completedQuizzesLabel != null) {
            completedQuizzesLabel.setText(String.valueOf(completed));
        }
    }

    /**
     * Create a visual card for a quiz
     */
    private VBox createQuizCard(QuizInfo quiz) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("lms-card", "quiz-card");
        card.setPadding(new Insets(16));

        // Title
        Label titleLabel = new Label(quiz.title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);

        // Description
        Label descLabel = new Label(quiz.description != null ? quiz.description : "");
        descLabel.getStyleClass().add("card-text");
        descLabel.setWrapText(true);
        descLabel.setTextFill(Color.web("#666"));

        // Quiz info
        HBox infoBox = new HBox(16);
        Label questionsLabel = new Label("📝 " + quiz.questionCount + " questions");
        Label passingLabel = new Label("✓ Pass: " + quiz.passingScore + "%");
        questionsLabel.getStyleClass().add("card-text");
        passingLabel.getStyleClass().add("card-text");
        if (quiz.timeLimit != null) {
            Label timeLabel = new Label("⏱ " + quiz.timeLimit + " min");
            timeLabel.getStyleClass().add("card-text");
            infoBox.getChildren().addAll(questionsLabel, passingLabel, timeLabel);
        } else {
            infoBox.getChildren().addAll(questionsLabel, passingLabel);
        }

        // Status badge
        HBox statusBox = new HBox(10);
        if (quiz.alreadyTaken) {
            Label takenLabel = new Label("Already taken");
            takenLabel.getStyleClass().addAll("badge", "badge-inactive");
            if (quiz.lastScore != null) {
                Label scoreLabel = new Label("Score: " + quiz.lastScore + "%");
                scoreLabel.getStyleClass().add("card-text");
                statusBox.getChildren().addAll(takenLabel, scoreLabel);
            } else {
                statusBox.getChildren().add(takenLabel);
            }
        } else {
            Label newLabel = new Label("Not attempted");
            newLabel.getStyleClass().addAll("badge", "badge-quiz");
            statusBox.getChildren().add(newLabel);
        }

        // Start button
        Button startButton = new Button(quiz.alreadyTaken ? "Review" : "Start Quiz");
        startButton.getStyleClass().add("primary-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        startButton.setOnAction(e -> {
            if (quiz.alreadyTaken) {
                reviewQuiz(quiz.id);
            } else {
                startQuiz(quiz.id);
            }
        });

        card.getChildren().addAll(titleLabel, descLabel, infoBox, statusBox, startButton);
        return card;
    }

    // ========== QUIZ TAKING SCREEN ==========

    /**
     * Start taking a quiz
     */
    private void startQuiz(int quizId) {
        try {
            currentQuiz = quizService.getQuizDetail(quizId, currentStudent.getId());
            if (currentQuiz == null) {
                showAlert("Error", "Failed to load quiz");
                return;
            }

            sortedQuestions = currentQuiz.questions;
            currentQuestionIndex = 0;
            loadExistingAnswers();

            // Show quiz-taking screen
            showScreen(quizTakingScreen);

            // Display quiz info
            quizTitleLabel.setText(currentQuiz.title);
            quizDescriptionLabel.setText(currentQuiz.description != null ? currentQuiz.description : "");
            buildQuestionNavigator();

            // Show first question
            showQuestionAtIndex(0);

            // Start timer if quiz has time limit
            if (currentQuiz.timeLimit != null && currentQuiz.timeLimit > 0) {
                startTimer(currentQuiz.timeLimit * 60);  // Convert to seconds
            }

            // Anti-cheat: setup focus monitoring and screenshot prevention
            setupFocusMonitoring();
            startScreenshotPrevention();

        } catch (Exception e) {
            logger.error("Error starting quiz: " + e.getMessage(), e);
            showAlert("Error", "Failed to start quiz: " + e.getMessage());
        }
    }

    /**
     * Display question at specific index
     */
    private void showQuestionAtIndex(int index) {
        if (index < 0 || index >= sortedQuestions.size()) {
            return;
        }

        currentQuestionIndex = index;
        QuestionDetail question = sortedQuestions.get(index);

        // Update progress
        progressLabel.setText("Question " + (index + 1) + " of " + sortedQuestions.size());
        if (quizProgressBar != null) {
            quizProgressBar.setProgress((index + 1) / (double) sortedQuestions.size());
        }
        updateQuestionNavigator();
        updateSaveStatus("Ready");

        // Update navigation buttons
        previousButton.setDisable(index == 0);
        nextButton.setDisable(index == sortedQuestions.size() - 1);

        // Clear and rebuild questions container
        questionsContainer.getChildren().clear();

        // Display question
        VBox questionBox = createQuestionDisplay(question);
        questionsContainer.getChildren().add(questionBox);
    }

    private void loadExistingAnswers() {
        textAnswers.clear();
        selectedChoiceAnswers.clear();
        if (currentQuiz == null || currentQuiz.existingAnswers == null) {
            return;
        }
        for (Answer answer : currentQuiz.existingAnswers) {
            if (answer == null || answer.getQuestion() == null) {
                continue;
            }
            int questionId = answer.getQuestion().getId();
            if (answer.getChoice() != null) {
                selectedChoiceAnswers.put(questionId, answer.getChoice().getId());
            }
            if (answer.getTextAnswer() != null) {
                textAnswers.put(questionId, answer.getTextAnswer());
            }
        }
    }

    private void buildQuestionNavigator() {
        if (questionNavigator == null || sortedQuestions == null) {
            return;
        }
        questionNavigator.getChildren().clear();
        for (int i = 0; i < sortedQuestions.size(); i++) {
            final int targetIndex = i;
            Button button = new Button(String.valueOf(i + 1));
            button.getStyleClass().add("quiz-nav-chip");
            button.setOnAction(event -> {
                saveCurrentAnswer();
                showQuestionAtIndex(targetIndex);
            });
            questionNavigator.getChildren().add(button);
        }
        updateQuestionNavigator();
    }

    private void updateQuestionNavigator() {
        if (questionNavigator == null || sortedQuestions == null) {
            return;
        }
        for (int i = 0; i < questionNavigator.getChildren().size(); i++) {
            if (questionNavigator.getChildren().get(i) instanceof Button button) {
                button.getStyleClass().removeAll("quiz-nav-chip-active", "quiz-nav-chip-answered");
                if (i == currentQuestionIndex) {
                    button.getStyleClass().add("quiz-nav-chip-active");
                } else if (hasAnswer(sortedQuestions.get(i).id)) {
                    button.getStyleClass().add("quiz-nav-chip-answered");
                }
            }
        }
    }

    private boolean hasAnswer(int questionId) {
        String text = textAnswers.get(questionId);
        return selectedChoiceAnswers.containsKey(questionId) || (text != null && !text.isBlank());
    }

    private void updateSaveStatus(String status) {
        if (saveStatusLabel != null) {
            saveStatusLabel.setText(status);
        }
    }

    /**
     * Create question display based on type
     */
    private VBox createQuestionDisplay(QuestionDetail question) {
        VBox box = new VBox(12);
        box.getStyleClass().addAll("lms-card", "quiz-question-card");

        // Question text
        Label questionLabel = new Label(question.text);
        questionLabel.getStyleClass().add("question-text");
        questionLabel.setWrapText(true);

        // Points indicator
        Label pointsLabel = new Label("Points: " + question.points);
        pointsLabel.getStyleClass().add("points-label");

        HBox header = new HBox(10, questionLabel, pointsLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(questionLabel, Priority.ALWAYS);

        box.getChildren().add(header);

        // Answer input based on question type
        if ("MULTIPLE_CHOICE".equals(question.type) || "TRUE_FALSE".equals(question.type) || "MCQ".equals(question.type)) {
            VBox choicesBox = createChoicesDisplay(question);
            box.getChildren().add(choicesBox);
        } else if ("TEXT".equals(question.type)) {
            TextArea textArea = new TextArea();
            textArea.setWrapText(true);
            textArea.setPrefRowCount(6);
            textArea.setId("question_" + question.id + "_answer");
            textArea.getStyleClass().add("answer-textarea");
            textArea.setText(textAnswers.getOrDefault(question.id, ""));
            
            // Anti-cheat: prevent copy/paste
            textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && (event.getCode() == KeyCode.C || event.getCode() == KeyCode.V)) {
                    event.consume();
                }
            });
            textArea.setContextMenu(new ContextMenu()); // Disable context menu
            
            Label answerLabel = new Label("Your answer");
            answerLabel.getStyleClass().add("form-label");
            box.getChildren().add(answerLabel);
            box.getChildren().add(textArea);
        }

        return box;
    }

    /**
     * Create multiple choice or true/false options display
     */
    private VBox createChoicesDisplay(QuestionDetail question) {
        VBox choicesBox = new VBox(8);
        choicesBox.getStyleClass().add("quiz-choice-list");

        Label choicesLabel = new Label("Choose one answer");
        choicesLabel.getStyleClass().add("form-label");
        choicesBox.getChildren().add(choicesLabel);

        ToggleGroup group = new ToggleGroup();
        Integer selectedChoiceId = selectedChoiceAnswers.get(question.id);
        int optionIndex = 0;

        for (ChoiceDetail choice : question.choices) {
            ToggleButton optionButton = new ToggleButton(choice.text);
            optionButton.setToggleGroup(group);
            optionButton.setId("choice_" + choice.id);
            optionButton.setUserData(choice.id);
            optionButton.getStyleClass().add("quiz-check-button");
            optionButton.setWrapText(true);
            optionButton.setMaxWidth(Double.MAX_VALUE);
            optionButton.setAlignment(Pos.CENTER_LEFT);

            Label marker = new Label(String.valueOf((char) ('A' + optionIndex)));
            marker.getStyleClass().add("quiz-check-marker");
            optionButton.setGraphic(marker);

            if (selectedChoiceId != null && selectedChoiceId == choice.id) {
                optionButton.setSelected(true);
            }
            optionButton.setOnAction(event -> {
                if (group.getSelectedToggle() == null) {
                    optionButton.setSelected(true);
                }
            });
            choicesBox.getChildren().add(optionButton);
            optionIndex++;
        }

        return choicesBox;
    }

    /**
     * Show previous question
     */
    private void showPreviousQuestion() {
        if (currentQuestionIndex > 0) {
            saveCurrentAnswer();
            showQuestionAtIndex(currentQuestionIndex - 1);
        }
    }

    /**
     * Show next question
     */
    private void showNextQuestion() {
        if (currentQuestionIndex < sortedQuestions.size() - 1) {
            saveCurrentAnswer();
            showQuestionAtIndex(currentQuestionIndex + 1);
        }
    }

    /**
     * Save current question's answer
     */
    private void saveCurrentAnswer() {
        if (currentQuiz == null || currentQuestionIndex >= sortedQuestions.size()) {
            return;
        }

        try {
            QuestionDetail question = sortedQuestions.get(currentQuestionIndex);
            String answer = "";
            Integer selectedChoiceId = null;

            if ("MULTIPLE_CHOICE".equals(question.type) || "TRUE_FALSE".equals(question.type) || "MCQ".equals(question.type)) {
                for (ChoiceDetail choice : question.choices) {
                    ToggleButton optionButton = (ToggleButton) questionsContainer.lookup("#choice_" + choice.id);
                    if (optionButton != null && optionButton.isSelected()) {
                        selectedChoiceId = choice.id;
                        answer = choice.text;
                        selectedChoiceAnswers.put(question.id, selectedChoiceId);
                        break;
                    }
                }
                if (selectedChoiceId == null) {
                    selectedChoiceAnswers.remove(question.id);
                }
            } else if ("TEXT".equals(question.type)) {
                TextArea ta = (TextArea) questionsContainer.lookup("#question_" + question.id + "_answer");
                if (ta != null) {
                    answer = ta.getText();
                    textAnswers.put(question.id, answer);
                }
            }

            // Save answer
            quizService.saveAnswer(currentQuiz.userAnswerId, question.id, answer, selectedChoiceId);
            updateQuestionNavigator();
            updateSaveStatus("Saved");
            logger.debug("Answer saved for question: " + question.id);

        } catch (Exception e) {
            updateSaveStatus("Save failed");
            logger.error("Error saving answer: " + e.getMessage(), e);
        }
    }

    /**
     * Show confirmation before submitting
     */
    private void submitAnswersConfirmation() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Submit Quiz");
        alert.setHeaderText("Submit Quiz?");
        alert.setContentText("Are you sure you want to submit your answers? You cannot continue after submission.");

        if (alert.showAndWait().get() == ButtonType.OK) {
            submitAnswers();
        }
    }

    /**
     * Submit quiz and show results
     */
    private void submitAnswers() {
        try {
            // Save last answer before submitting
            saveCurrentAnswer();

            // Stop timer
            if (timerTimeline != null) {
                timerTimeline.stop();
            }

            // Submit quiz
            int teacherId = 1;  // TODO: Get actual teacher ID from quiz creator
            SubmissionResult result = quizService.submitQuiz(currentQuiz.userAnswerId,
                    currentStudent.getId(), teacherId);

            if (result.success) {
                // Remove anti-cheat monitoring
                cleanupFocusMonitoring();
                stopScreenshotPrevention();
                
                // Get and display results
                currentResults = quizService.getQuizResultDetail(currentQuiz.userAnswerId);
                showResultsScreen();
            } else {
                showAlert("Submission Failed", result.message);
            }

        } catch (Exception e) {
            logger.error("Error submitting quiz: " + e.getMessage(), e);
            showAlert("Error", "Failed to submit quiz: " + e.getMessage());
        }
    }

    /**
     * Start quiz timer
     */
    private void startTimer(int seconds) {
        final int[] remaining = {seconds};

        // Update timer label immediately
        int initMin = remaining[0] / 60;
        int initSec = remaining[0] % 60;
        timerLabel.setText(String.format("%02d:%02d", initMin, initSec));

        // Use Timeline with KeyFrame for proper repeating timer
        // PauseTransition does NOT support setCycleCount(INDEFINITE)
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remaining[0]--;

            int minutes = remaining[0] / 60;
            int secs = remaining[0] % 60;
            timerLabel.setText(String.format("%02d:%02d", minutes, secs));

            // Visual warning when time is low
            if (remaining[0] <= 60) {
                timerLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold; -fx-font-size: 16px;");
            } else if (remaining[0] <= 300) {
                timerLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
            }

            if (remaining[0] <= 0) {
                timerTimeline.stop();
                showAlert("Time Elapsed", "Your quiz time has expired. Your answers will be submitted automatically.");
                submitAnswers();
            }
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    // ========== RESULTS SCREEN ==========

    /**
     * Display quiz results
     */
    private void showResultsScreen() {
        if (currentResults == null) {
            return;
        }

        showScreen(resultsScreen);

        // Display results
        resultsTitleLabel.setText(currentResults.title);
        resultsDescriptionLabel.setText(currentResults.description != null ? currentResults.description : "");

        scoreLabel.setText(String.format("%d/%d points (%d%%)",
                currentResults.earnedPoints, currentResults.totalPoints, currentResults.percentage));

        if (currentResults.passed) {
            passStatusLabel.setText("✓ PASSED");
            passStatusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 18; -fx-font-weight: bold;");
        } else {
            passStatusLabel.setText("✗ FAILED");
            passStatusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 18; -fx-font-weight: bold;");
        }

        // Display question review
        questionReviewBox.getChildren().clear();
        for (QuestionResultDetail qr : currentResults.questionResults) {
            VBox reviewItem = createQuestionReviewItem(qr);
            questionReviewBox.getChildren().add(reviewItem);
        }
    }

    /**
     * Create review item for a question
     */
    private VBox createQuestionReviewItem(QuestionResultDetail questionResult) {
        VBox item = new VBox(8);
        item.setPadding(new Insets(12));

        // Color-coded border based on correctness
        String borderColor = questionResult.isCorrect ? "#27ae60" : "#e74c3c";
        item.setStyle("-fx-border-color: " + borderColor + "; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-color: " + (questionResult.isCorrect ? "#f0fff4" : "#fff5f5") + "; -fx-background-radius: 8;");

        // Question header with correctness icon and points
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(questionResult.isCorrect ? "✓" : "✗");
        icon.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + borderColor + ";");
        Label questionLabel = new Label(questionResult.question);
        questionLabel.getStyleClass().add("review-question");
        questionLabel.setWrapText(true);
        HBox.setHgrow(questionLabel, Priority.ALWAYS);
        Label points = new Label(questionResult.pointsEarned + "/" + questionResult.maxPoints + " pts");
        points.setStyle("-fx-font-weight: bold; -fx-text-fill: " + borderColor + ";");
        header.getChildren().addAll(icon, questionLabel, points);

        // Student answer
        Label studentAnswerLabel = new Label("Your answer: " + (questionResult.studentAnswer.isEmpty() ? "(no answer)" : questionResult.studentAnswer));
        studentAnswerLabel.setWrapText(true);
        studentAnswerLabel.setStyle(questionResult.isCorrect ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;");

        VBox answersBox = new VBox(4);
        answersBox.getChildren().add(studentAnswerLabel);

        if (!questionResult.isCorrect && questionResult.correctAnswer != null && !questionResult.correctAnswer.isEmpty()) {
            Label correctAnswerLabel = new Label("Correct answer: " + questionResult.correctAnswer);
            correctAnswerLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: 600;");
            correctAnswerLabel.setWrapText(true);
            answersBox.getChildren().add(correctAnswerLabel);
        }

        if (questionResult.explanation != null && !questionResult.explanation.isEmpty()) {
            Label explanationLabel = new Label("💡 " + questionResult.explanation);
            explanationLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic; -fx-padding: 4 0 0 0;");
            explanationLabel.setWrapText(true);
            answersBox.getChildren().add(explanationLabel);
        }

        item.getChildren().addAll(header, answersBox);
        return item;
    }

    /**
     * Review an already taken quiz directly
     */
    private void reviewQuiz(int quizId) {
        try {
            QuizDetail detail = quizService.getQuizDetail(quizId, currentStudent.getId());
            if (detail != null) {
                currentResults = quizService.getQuizResultDetail(detail.userAnswerId);
                showResultsScreen();
            }
        } catch (Exception e) {
            logger.error("Error reviewing quiz: " + e.getMessage(), e);
            showAlert("Error", "Failed to load quiz results.");
        }
    }

    /**
     * Return to quiz list
     */
    private void showQuizList() {
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        showScreen(quizListScreen);
        loadQuizList();
    }

    // ========== HELPER METHODS ==========

    /**
     * Set up window focus monitoring for anti-cheat
     */
    private void setupFocusMonitoring() {
        if (quizTakingScreen.getScene() == null) return;
        Stage stage = (Stage) quizTakingScreen.getScene().getWindow();
        if (stage == null) return;

        cleanupFocusMonitoring(); // Remove any existing listener

        focusListener = (obs, oldVal, newVal) -> {
            if (!newVal && currentQuiz != null) {
                // Focus lost
                logger.warn("Anti-Cheat: Student switched focus from quiz!");
                quizService.flagCheat(currentQuiz.userAnswerId);
            }
        };
        stage.focusedProperty().addListener(focusListener);
    }

    /**
     * Remove anti-cheat focus monitoring
     */
    private void cleanupFocusMonitoring() {
        if (focusListener != null && quizTakingScreen.getScene() != null) {
            Stage stage = (Stage) quizTakingScreen.getScene().getWindow();
            if (stage != null) {
                stage.focusedProperty().removeListener(focusListener);
            }
            focusListener = null;
        }
    }

    /**
     * Periodically clear clipboard if it contains an image (anti-screenshot)
     */
    private void startScreenshotPrevention() {
        stopScreenshotPrevention(); // Cleanup existing
        
        screenshotTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasImage()) {
                clipboard.clear();
                logger.warn("Anti-Cheat: Cleared clipboard image attempt.");
            }
        }));
        screenshotTimeline.setCycleCount(Animation.INDEFINITE);
        screenshotTimeline.play();
    }

    private void stopScreenshotPrevention() {
        if (screenshotTimeline != null) {
            screenshotTimeline.stop();
            screenshotTimeline = null;
        }
    }

    private void showScreen(javafx.scene.Node screen) {
        // If leaving quiz taking screen, cleanup monitoring
        if (screen != quizTakingScreen) {
            cleanupFocusMonitoring();
            stopScreenshotPrevention();
        }
        if (quizListScreen != null) {
            quizListScreen.setVisible(screen == quizListScreen);
            quizListScreen.setManaged(screen == quizListScreen);
        }
        if (quizTakingScreen != null) {
            quizTakingScreen.setVisible(screen == quizTakingScreen);
            quizTakingScreen.setManaged(screen == quizTakingScreen);
        }
        if (resultsScreen != null) {
            resultsScreen.setVisible(screen == resultsScreen);
            resultsScreen.setManaged(screen == resultsScreen);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
