package controller.lms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.UserAnswer;
import entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import service.evaluation.AiQuizGenerationService;
import service.lms.StudentQuizService;
import service.lms.StudentQuizService.QuizResultDetail;
import service.lms.StudentQuizService.QuestionResultDetail;
import services.ServiceUser;
import services.ServiceUserAnswer;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class TeacherQuizReviewController implements Initializable {

    @FXML private Label studentLabel;
    @FXML private Label scoreLabel;
    @FXML private Label statusLabel;
    @FXML private VBox questionsContainer;
    @FXML private ScrollPane scrollPane;

    private final StudentQuizService studentQuizSvc = new StudentQuizService();
    private final ServiceUserAnswer userAnswerSvc = new ServiceUserAnswer();
    private final ServiceUser userSvc = new ServiceUser();
    private final AiQuizGenerationService aiSvc = new AiQuizGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    private int userAnswerId;
    private int quizId;
    private dto.lms.TeacherAssignmentRowDto assignment;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        scrollPane.setFitToWidth(true);
    }

    public void setContext(int userAnswerId, int quizId, dto.lms.TeacherAssignmentRowDto assignment) {
        this.userAnswerId = userAnswerId;
        this.quizId = quizId;
        this.assignment = assignment;
        loadReview();
    }

    private void loadReview() {
        questionsContainer.getChildren().clear();

        UserAnswer attempt = userAnswerSvc.getParFiltreInt(userAnswerId);
        if (attempt == null) return;

        // Student Info
        User student = userSvc.getALL().stream()
                .filter(u -> u.getId() == attempt.getUser().getId())
                .findFirst().orElse(null);
        studentLabel.setText(student != null ? student.getEmail() : "Unknown Student");

        // Score Info
        int earned = attempt.getScore() != null ? attempt.getScore() : 0;
        int total = attempt.getTotalPoints() != null ? attempt.getTotalPoints() : 0;
        int pct = total > 0 ? (earned * 100 / total) : 0;
        scoreLabel.setText(earned + "/" + total + " (" + pct + "%)");

        boolean passed = attempt.getIsPassed() == 1;
        statusLabel.setText(passed ? "PASSED" : "FAILED");
        statusLabel.getStyleClass().setAll("badge", passed ? "badge-active" : "badge-full");

        // Load details using StudentQuizService
        QuizResultDetail details = studentQuizSvc.getQuizResultDetail(userAnswerId);
        if (details == null) return;

        for (QuestionResultDetail qr : details.questionResults) {
            questionsContainer.getChildren().add(createQuestionItem(qr));
        }
    }

    private VBox createQuestionItem(QuestionResultDetail qr) {
        VBox item = new VBox(10);
        item.getStyleClass().add("card");
        item.setPadding(new Insets(15));
        item.setStyle("-fx-border-color: #ddd; -fx-border-radius: 8; -fx-background-color: white;");

        // Question text and points
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label qLabel = new Label(qr.question != null && !qr.question.isEmpty() ? qr.question : "Question Text Missing");
        qLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        qLabel.setWrapText(true);
        HBox.setHgrow(qLabel, Priority.ALWAYS);

        Label pLabel = new Label(qr.pointsEarned + "/" + qr.maxPoints + " pts");
        pLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (qr.isCorrect ? "#27ae60" : "#e74c3c") + ";");
        
        header.getChildren().addAll(qLabel, pLabel);

        // Student Answer
        VBox content = new VBox(5);
        Label ansHeader = new Label("Student Answer:");
        ansHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
        
        Label ansValue = new Label(qr.studentAnswer == null || qr.studentAnswer.isEmpty() ? "(No Answer)" : qr.studentAnswer);
        ansValue.setWrapText(true);
        ansValue.setStyle("-fx-padding: 8; -fx-background-color: #f8f9fa; -fx-background-radius: 4; -fx-text-fill: #34495e; -fx-border-color: #eee; -fx-border-radius: 4;");
        ansValue.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(ansHeader, ansValue);

        // Correct Answer
        if (qr.correctAnswer != null && !qr.correctAnswer.isEmpty()) {
            Label correctHeader = new Label("Correct Answer:");
            correctHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #27ae60; -fx-padding: 5 0 0 0;");
            
            Label correctValue = new Label(qr.correctAnswer);
            correctValue.setWrapText(true);
            correctValue.setStyle("-fx-padding: 8; -fx-background-color: #f0fff4; -fx-background-radius: 4; -fx-text-fill: #27ae60; -fx-border-color: #d4edda; -fx-border-radius: 4;");
            correctValue.setMaxWidth(Double.MAX_VALUE);
            
            content.getChildren().addAll(correctHeader, correctValue);
        }

        // AI Detection Button for Text answers
        if ("TEXT".equals(qr.type) && !qr.studentAnswer.isEmpty()) {
            Button detectBtn = new Button("Check for AI 🤖");
            detectBtn.getStyleClass().add("secondary-button");
            detectBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
            Label resultLabel = new Label();
            resultLabel.setWrapText(true);
            resultLabel.setMaxWidth(Double.MAX_VALUE);
            resultLabel.setStyle("-fx-font-size: 11px; -fx-padding: 5; -fx-background-color: #f8f9fa; -fx-background-radius: 4; -fx-border-color: #eee; -fx-border-radius: 4;");
            resultLabel.setVisible(false); // Hide until result is available
            resultLabel.setManaged(false);

            detectBtn.setOnAction(e -> {
                detectBtn.setDisable(true);
                detectBtn.setText("Analyzing...");
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                resultLabel.setText("Processing AI analysis...");
                resultLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 8; -fx-background-color: #f8f9fa;");
                
                new Thread(() -> {
                    try {
                        String result = aiSvc.detectAiContent(qr.studentAnswer);
                        Platform.runLater(() -> {
                            try {
                                JsonNode root = mapper.readTree(result);
                                int likelihood = root.path("likelihood").asInt(-1);
                                String explanation = root.path("explanation").asText("").trim();
                                
                                if (likelihood == -1) {
                                    resultLabel.setText("Analysis complete, but no score was provided.\nRaw: " + result);
                                    resultLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-background-color: #f8f9fa; -fx-padding: 8;");
                                } else {
                                    resultLabel.setText("AI Likelihood: " + likelihood + "%\n" + explanation);
                                    String color = likelihood > 70 ? "#e74c3c" : (likelihood > 30 ? "#f39c12" : "#27ae60");
                                    resultLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-padding: 8; -fx-background-color: " + (likelihood > 70 ? "#fff5f5" : "#f0fff4") + "; -fx-border-color: " + color + ";");
                                }
                                
                                detectBtn.setText("Check Again");
                                detectBtn.setDisable(false);
                            } catch (Exception ex) {
                                resultLabel.setText("Parsing failed. AI response: " + result);
                                resultLabel.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: #fff5f5; -fx-padding: 8;");
                                detectBtn.setDisable(false);
                                detectBtn.setText("Check for AI 🤖");
                            }
                        });
                    } catch (Exception err) {
                        Platform.runLater(() -> {
                            resultLabel.setText("Analysis error: " + err.getMessage());
                            resultLabel.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: #fff5f5; -fx-padding: 8;");
                            detectBtn.setDisable(false);
                            detectBtn.setText("Check for AI 🤖");
                        });
                    }
                }).start();
            });
            
            VBox aiBox = new VBox(5, detectBtn, resultLabel);
            content.getChildren().add(aiBox);
        }

        item.getChildren().addAll(header, content);
        return item;
    }

    @FXML
    private void onBack() {
        AppNavigator.showTeacherQuizResults(quizId, assignment);
    }
}
