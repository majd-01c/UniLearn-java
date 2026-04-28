package controller.lms;

import entities.Quiz;
import entities.UserAnswer;
import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import services.ServiceQuiz;
import services.ServiceUser;
import services.ServiceUserAnswer;
import util.AppNavigator;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for viewing student quiz results (teacher-side).
 * Shows all student attempts for a specific quiz with scores and pass/fail status.
 */
public class TeacherQuizResultsController implements Initializable {

    @FXML private Label quizTitleLabel;
    @FXML private Label quizInfoLabel;
    @FXML private Label avgScoreLabel;
    @FXML private Label passRateLabel;
    @FXML private Label attemptCountLabel;
    @FXML private VBox resultsContainer;
    @FXML private Label emptyLabel;

    private final ServiceQuiz quizSvc = new ServiceQuiz();
    private final ServiceUserAnswer userAnswerSvc = new ServiceUserAnswer();
    private final ServiceUser userSvc = new ServiceUser();

    private int quizId;
    private dto.lms.TeacherAssignmentRowDto assignment;

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Init handled in setContext
    }

    public void setContext(int quizId, dto.lms.TeacherAssignmentRowDto assignment) {
        this.quizId = quizId;
        this.assignment = assignment;
        loadResults();
    }

    private void loadResults() {
        resultsContainer.getChildren().clear();

        // Load quiz info
        Quiz quiz = quizSvc.getALL().stream()
                .filter(q -> q.getId() == quizId)
                .findFirst().orElse(null);

        if (quiz == null) {
            quizTitleLabel.setText("Quiz not found");
            return;
        }

        quizTitleLabel.setText(quiz.getTitle());
        quizInfoLabel.setText("Passing Score: " + (quiz.getPassingScore() != null ? quiz.getPassingScore() + "%" : "N/A")
                + "  |  Time Limit: " + (quiz.getTimeLimit() != null ? quiz.getTimeLimit() + " min" : "Unlimited"));

        // Load all attempts for this quiz
        List<UserAnswer> attempts = userAnswerSvc.getByQuizId(quizId);

        if (attempts.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            avgScoreLabel.setText("—");
            passRateLabel.setText("—");
            attemptCountLabel.setText("0");
            return;
        }

        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        attemptCountLabel.setText(String.valueOf(attempts.size()));

        // Calculate stats
        int totalScore = 0;
        int completedCount = 0;
        int passedCount = 0;

        // Load all users for name lookup
        var allUsers = userSvc.getALL();

        // Header row
        HBox headerRow = new HBox(12);
        headerRow.setPadding(new Insets(6, 8, 6, 8));
        Label h1 = new Label("STUDENT"); h1.getStyleClass().add("form-label"); h1.setPrefWidth(200);
        Label h2 = new Label("SCORE"); h2.getStyleClass().add("form-label"); h2.setPrefWidth(100);
        Label h3 = new Label("STATUS"); h3.getStyleClass().add("form-label"); h3.setPrefWidth(80);
        Label h4 = new Label("COMPLETED"); h4.getStyleClass().add("form-label"); h4.setPrefWidth(150);
        headerRow.getChildren().addAll(h1, h2, h3, h4);
        resultsContainer.getChildren().add(headerRow);
        resultsContainer.getChildren().add(new Separator());

        for (UserAnswer attempt : attempts) {
            // Find student info
            String studentName = "Unknown";
            if (attempt.getUser() != null) {
                int userId = attempt.getUser().getId();
                User student = allUsers.stream()
                        .filter(u -> u.getId() == userId)
                        .findFirst().orElse(null);
                if (student != null) {
                    studentName = student.getEmail();
                }
            }

            // Score display
            String scoreText = "—";
            if (attempt.getCompletedAt() != null) {
                completedCount++;
                int earned = attempt.getScore() != null ? attempt.getScore() : 0;
                int total = attempt.getTotalPoints() != null ? attempt.getTotalPoints() : 0;
                int pct = total > 0 ? (earned * 100 / total) : 0;
                scoreText = earned + "/" + total + " (" + pct + "%)";
                totalScore += pct;
                if (attempt.getIsPassed() == 1) passedCount++;
            } else {
                scoreText = "In Progress";
            }

            boolean passed = attempt.getIsPassed() == 1;

            // Build row
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8));

            Label nameLabel = new Label(studentName);
            nameLabel.getStyleClass().add("card-text");
            nameLabel.setStyle("-fx-font-weight: bold;");
            nameLabel.setPrefWidth(200);

            Label scoreLabel = new Label(scoreText);
            scoreLabel.getStyleClass().add("card-text");
            scoreLabel.setPrefWidth(100);

            Label statusLabel = new Label(attempt.getCompletedAt() != null ? (passed ? "PASSED" : "FAILED") : "IN PROGRESS");
            statusLabel.getStyleClass().addAll("badge", 
                    attempt.getCompletedAt() != null ? (passed ? "badge-active" : "badge-full") : "badge-draft");
            statusLabel.setPrefWidth(80);

            Label dateLabel = new Label(attempt.getCompletedAt() != null ? DATE_FMT.format(attempt.getCompletedAt()) : "—");
            dateLabel.getStyleClass().add("card-text");
            dateLabel.setPrefWidth(150);

            Button reviewBtn = new Button("Review");
            reviewBtn.getStyleClass().add("secondary-button");
            reviewBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
            reviewBtn.setOnAction(e -> AppNavigator.showTeacherQuizReview(attempt.getId(), quizId, assignment));

            row.getChildren().addAll(nameLabel, scoreLabel, statusLabel, dateLabel, reviewBtn);

            // Anti-cheat badges and actions
            if (attempt.getCheatFlag() == 1) {
                Label cheatBadge = new Label("⚠️ CHEATED");
                cheatBadge.getStyleClass().addAll("badge", "badge-full");
                cheatBadge.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;");
                
                Button zeroBtn = new Button("Mark 0");
                zeroBtn.getStyleClass().add("primary-button");
                // Modern "Cooler" Style: Vibrant red, rounded, with shadow-like effect
                zeroBtn.setStyle("-fx-background-color: #e74c3c; " +
                               "-fx-text-fill: white; " +
                               "-fx-font-weight: 800; " +
                               "-fx-font-size: 11px; " +
                               "-fx-background-radius: 50; " +
                               "-fx-padding: 6 16; " +
                               "-fx-cursor: hand; " +
                               "-fx-effect: dropshadow(three-pass-box, rgba(231, 76, 60, 0.3), 10, 0, 0, 4);");
                zeroBtn.setOnAction(e -> {
                    userAnswerSvc.markScoreZero(attempt.getId());
                    loadResults(); // Refresh
                });
                
                row.getChildren().addAll(cheatBadge, zeroBtn);
            }

            resultsContainer.getChildren().add(row);
        }

        // Update summary stats
        if (completedCount > 0) {
            avgScoreLabel.setText((totalScore / completedCount) + "%");
            passRateLabel.setText((passedCount * 100 / completedCount) + "%");
        } else {
            avgScoreLabel.setText("—");
            passRateLabel.setText("—");
        }
    }

    @FXML
    private void onBack() {
        if (assignment != null) {
            AppNavigator.showTeacherWorkspace(assignment);
        }
    }
}
