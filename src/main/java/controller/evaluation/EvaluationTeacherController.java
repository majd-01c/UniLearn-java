package controller.evaluation;

import entities.Assessment;
import entities.Grade;
import entities.Schedule;
import entities.Reclamation; // Added import for Reclamation
import evaluation.AssessmentType;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import security.UserSession;
import service.evaluation.EvaluationService;
import service.evaluation.ai.GroqAiService;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

public class EvaluationTeacherController {

    @FXML
    private Label teacherNameLabel;
    @FXML
    private TextField classNameField;

    @FXML
    private TextField assessmentTitleField;
    @FXML
    private ComboBox<String> assessmentTypeBox;
    @FXML
    private TextField assessmentMaxScoreField;
    @FXML
    private DatePicker assessmentDatePicker;
    @FXML
    private TextArea assessmentDescriptionArea;
    @FXML
    private ComboBox<String> assessmentCourseBox;
    @FXML
    private ComboBox<String> assessmentClassBox;
    @FXML
    private ComboBox<String> assessmentContenuBox;

    @FXML
    private ComboBox<String> assessmentsSortBox;
    @FXML
    private VBox assessmentsCardsBox;

    @FXML
    private Label selectedAssessmentLabel;
    @FXML
    private ComboBox<String> gradesSortBox;
    @FXML
    private VBox gradesCardsBox;

    @FXML
    private ComboBox<String> gradeStudentBox;
    @FXML
    private TextField gradeScoreInputField;
    @FXML
    private TextField gradeCommentInputField;

    @FXML
    private ComboBox<String> scheduleSortBox;
    @FXML
    private VBox scheduleCardsBox;

    // FXML fields for Student Inquiries tab
    @FXML
    private Label selectedInquiryLabel;
    @FXML
    private TextArea inquiryResponseArea;
    @FXML
    private ComboBox<String> inquiryStatusBox;
    @FXML
    private VBox inquiriesCardsBox;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private final GroqAiService aiService = new GroqAiService();
    private final DecimalFormat df = new DecimalFormat("0.00");
    private int teacherId;
    private Integer selectedAssessmentId;
    private Integer selectedInquiryId; // Field to hold selected inquiry ID

    @FXML
    public void initialize() {
        teacherId = UserSession.getCurrentUserId().orElse(1);
        String teacherName = service.resolveUserDisplayName(teacherId);
        teacherNameLabel.setText(teacherName == null ? "Teacher" : teacherName);

        assessmentTypeBox.getItems().setAll("CC", "EXAM", "OTHER");
        assessmentTypeBox.getSelectionModel().select("CC");
        assessmentMaxScoreField.setText("20");

        assessmentsSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        assessmentsSortBox.getSelectionModel().select("NEW");
        gradesSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        gradesSortBox.getSelectionModel().select("NEW");
        scheduleSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        scheduleSortBox.getSelectionModel().select("NEW");

        // Initialize inquiry status box
        if (inquiryStatusBox != null) {
            inquiryStatusBox.getItems().setAll("Pending", "Processing", "Resolved", "Rejected");
            inquiryStatusBox.getSelectionModel().select("Pending");
        }

        selectedAssessmentLabel.setText("Selected assessment: none");
        if (selectedInquiryLabel != null) {
            selectedInquiryLabel.setText("Select a student complaint from the list below to respond.");
        }

        installInputValidation();
        refreshTeacherSelectors();
        refreshTeacherSpace();

        // AI Correction Hook for Assessment Title
        assessmentTitleField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String original = assessmentTitleField.getText();
                if (original != null && original.length() > 3) {
                    new Thread(() -> {
                        String corrected = aiService.correctSpellingAndGrammar(original);
                        if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                            javafx.application.Platform.runLater(() -> {
                                if (assessmentTitleField.getText().equals(original)) {
                                    assessmentTitleField.setText(corrected);
                                    showFeedback("✅ AI refined assessment title and filtered content.", false);
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        // AI Correction Hook for Assessment Description
        assessmentDescriptionArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                String original = assessmentDescriptionArea.getText();
                if (original != null && original.length() > 10) {
                    new Thread(() -> {
                        String corrected = aiService.correctSpellingAndGrammar(original);
                        if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                            javafx.application.Platform.runLater(() -> {
                                if (assessmentDescriptionArea.getText().equals(original)) {
                                    assessmentDescriptionArea.setText(corrected);
                                    showFeedback("✅ AI refined assessment description and filtered content.", false);
                                }
                            });
                        }
                    }).start();
                }
            }
        });
        
        // AI Correction Hook for Grade Comments
        gradeCommentInputField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String original = gradeCommentInputField.getText();
                if (original != null && original.length() > 5) {
                    new Thread(() -> {
                        String corrected = aiService.correctSpellingAndGrammar(original);
                        if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                            javafx.application.Platform.runLater(() -> {
                                if (gradeCommentInputField.getText().equals(original)) {
                                    gradeCommentInputField.setText(corrected);
                                    showFeedback("✅ AI refined student feedback and filtered content.", false);
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        // AI Correction Hook for Inquiry Response
        if (inquiryResponseArea != null) {
            inquiryResponseArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    String original = inquiryResponseArea.getText();
                    if (original != null && original.length() > 10) {
                        new Thread(() -> {
                            String corrected = aiService.correctSpellingAndGrammar(original);
                            if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                                javafx.application.Platform.runLater(() -> {
                                    if (inquiryResponseArea.getText().equals(original)) {
                                        inquiryResponseArea.setText(corrected);
                                        showFeedback("✅ AI refined inquiry response and filtered content.", false);
                                    }
                                });
                            }
                        }).start();
                    }
                }
            });
        }
    }

    @FXML
    private void onRefreshTeacherSpace() {
        refreshTeacherSpace();
    }

    @FXML
    private void onSortAssessmentsChanged() {
        refreshAssessments();
    }

    @FXML
    private void onSortGradesChanged() {
        if (selectedAssessmentId != null) {
            onLoadAssessmentGrades();
        }
    }

    @FXML
    private void onSortScheduleChanged() {
        refreshTeacherSchedule();
    }

    @FXML
    private void onCreateAssessment() {
        try {
            String selectedCourseName = assessmentCourseBox == null ? null : assessmentCourseBox.getValue();
            Integer courseId = service.findCourseIdByName(requireNotBlank(selectedCourseName, "Course"));
            if (courseId == null) {
                throw new IllegalArgumentException("Course name not found.");
            }

            String selectedClassName = assessmentClassBox == null ? null : assessmentClassBox.getValue();
            Integer classeId = optionalNameToId(selectedClassName, "Class name", service::findClasseIdByName);

            String selectedContenuTitle = assessmentContenuBox == null ? null : assessmentContenuBox.getValue();
            Integer contenuId = optionalNameToId(selectedContenuTitle, "Content title", service::findContenuIdByTitle);

            String title = requireNotBlank(assessmentTitleField.getText(), "Assessment title");
            String description = requireNotBlank(assessmentDescriptionArea.getText(), "Assessment description");
            if (title.length() < 3) {
                throw new IllegalArgumentException("Assessment title must have at least 3 characters.");
            }
            if (description.length() < 10) {
                throw new IllegalArgumentException("Assessment description must have at least 10 characters.");
            }
            if (assessmentDatePicker.getValue() == null) {
                throw new IllegalArgumentException("Assessment date is required.");
            }
            if (assessmentTypeBox.getValue() == null || assessmentTypeBox.getValue().isBlank()) {
                throw new IllegalArgumentException("Assessment type is required.");
            }

            double maxScore = requireDoubleInRange(assessmentMaxScoreField.getText(), "Max score", 1.0, 100.0);

            service.createAssessment(
                    teacherId,
                    courseId,
                    classeId,
                    contenuId,
                    AssessmentType.fromValue(assessmentTypeBox.getValue()),
                    title,
                    description,
                    assessmentDatePicker.getValue(),
                    maxScore
            );
            assessmentTitleField.clear();
            assessmentDescriptionArea.clear();
            if (assessmentClassBox != null) {
                assessmentClassBox.getSelectionModel().clearSelection();
            }
            if (assessmentContenuBox != null) {
                assessmentContenuBox.getSelectionModel().clearSelection();
            }
            refreshAssessments();
            showFeedback("Assessment created.", false);
        } catch (Exception e) {
            showFeedback("Create assessment failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDeleteSelectedAssessment() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            if (!confirmDeletion("Delete this assessment? This action cannot be undone.")) {
                showFeedback("Deletion cancelled.", false);
                return;
            }
            service.deleteAssessment(selectedAssessmentId);
            selectedAssessmentId = null;
            selectedAssessmentLabel.setText("Selected assessment: none");
            gradesCardsBox.getChildren().clear();
            refreshAssessments();
            showFeedback("Assessment deleted.", false);
        } catch (Exception e) {
            showFeedback("Delete assessment failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onLoadAssessmentGrades() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            List<Grade> grades = new ArrayList<>(service.getGradesByAssessment(selectedAssessmentId));
            sortGrades(grades, selectedSort(gradesSortBox));
            renderGradesCards(grades);
            showFeedback("Grades loaded.", false);
        } catch (Exception e) {
            showFeedback("Load grades failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onSaveGrade() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            String selectedStudentName = gradeStudentBox == null ? null : gradeStudentBox.getValue();
            Integer studentId = service.findUserIdByName(requireNotBlank(selectedStudentName, "Student"));
            if (studentId == null) {
                throw new IllegalArgumentException("Student name not found.");
            }
            double score = requireDoubleInRange(gradeScoreInputField.getText(), "Grade score", 0.0, 100.0);
            String comment = requireNotBlank(gradeCommentInputField.getText(), "Grade comment");

            service.saveGrade(selectedAssessmentId, studentId, teacherId, score, comment);
            onLoadAssessmentGrades();
            showFeedback("Grade saved.", false);
        } catch (Exception e) {
            showFeedback("Save grade failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onUpdateInquiryStatus() {
        try {
            if (selectedInquiryId == null) {
                throw new IllegalArgumentException("Please select an inquiry from the list first.");
            }
            String status = inquiryStatusBox.getValue();
            String response = requireNotBlank(inquiryResponseArea.getText(), "Official Response");
            
            service.updateReclamationStatus(selectedInquiryId, status, response);
            
            inquiryResponseArea.clear();
            selectedInquiryId = null;
            selectedInquiryLabel.setText("Select a student complaint from the list below to respond.");
            refreshInquiries();
            showFeedback("Inquiry response submitted successfully.", false);
        } catch (Exception e) {
            showFeedback("Update inquiry failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDownloadTeacherSchedule() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save My Academic Schedule");
            fileChooser.setInitialFileName("Teacher_Schedule_" + teacherId + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fileChooser.showSaveDialog(feedbackLabel.getScene().getWindow());

            if (file != null) {
                service.downloadTeacherSchedule(teacherId, file);
                showFeedback("Schedule downloaded: " + file.getName(), false);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            }
        } catch (Exception e) {
            showFeedback("Download schedule failed: " + e.getMessage(), true);
        }
    }

    private void refreshTeacherSpace() {
        refreshTeacherSelectors();
        refreshTeacherStudents();
        refreshAssessments();
        refreshTeacherSchedule();
        refreshInquiries(); // Call refreshInquiries here
    }

    private void refreshTeacherSelectors() {
        if (assessmentCourseBox != null) {
            setComboValues(assessmentCourseBox, service.getTeacherCourseNames(teacherId), true);
        }
        if (assessmentClassBox != null) {
            setComboValues(assessmentClassBox, service.getTeacherClassNames(teacherId), false);
        }
        if (assessmentContenuBox != null) {
            setComboValues(assessmentContenuBox, service.getContenuTitles(), false);
        }
    }

    private void refreshTeacherStudents() {
        if (gradeStudentBox == null) {
            return;
        }
        String classFilter = classNameField == null ? null : classNameField.getText();
        setComboValues(gradeStudentBox, service.getStudentNamesForTeacher(teacherId, classFilter), false);
    }

    private void setComboValues(ComboBox<String> comboBox, List<String> values, boolean selectFirstWhenEmptySelection) {
        String previous = comboBox.getValue();
        comboBox.getItems().setAll(values);
        if (previous != null && comboBox.getItems().contains(previous)) {
            comboBox.getSelectionModel().select(previous);
            return;
        }
        if (selectFirstWhenEmptySelection && !comboBox.getItems().isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }

    private void refreshAssessments() {
        List<Assessment> assessments = new ArrayList<>(service.getAssessmentsByTeacher(teacherId));
        sortAssessments(assessments, selectedSort(assessmentsSortBox));

        assessmentsCardsBox.getChildren().clear();
        if (assessments.isEmpty()) {
            assessmentsCardsBox.getChildren().add(emptyCard("No assessments available"));
            return;
        }
        for (Assessment assessment : assessments) {
            assessmentsCardsBox.getChildren().add(createAssessmentCard(assessment));
        }
    }

    private void refreshTeacherSchedule() {
        refreshTeacherStudents();
        List<Schedule> schedules = new ArrayList<>(service.getScheduleByTeacher(teacherId));
        String classFilter = classNameField.getText() == null ? "" : classNameField.getText().trim().toLowerCase(Locale.ROOT);
        if (!classFilter.isBlank()) {
            schedules.removeIf(s -> {
                String className = s.getClasse() == null ? null : service.resolveClasseName(s.getClasse().getId());
                return className == null || !className.trim().toLowerCase(Locale.ROOT).contains(classFilter);
            });
        }
        sortSchedules(schedules, selectedSort(scheduleSortBox));

        scheduleCardsBox.getChildren().clear();
        if (schedules.isEmpty()) {
            scheduleCardsBox.getChildren().add(emptyCard("No schedule entries available"));
            return;
        }

        Map<String, List<Schedule>> byDay = new LinkedHashMap<>();
        String[] daysOrder = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (String d : daysOrder) byDay.put(d, new ArrayList<>());

        for (Schedule s : schedules) {
            String day = s.getDayOfWeek() == null ? "Monday" : s.getDayOfWeek().trim();
            String normalized = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
            if (byDay.containsKey(normalized)) byDay.get(normalized).add(s);
        }

        for (Map.Entry<String, List<Schedule>> entry : byDay.entrySet()) {
            VBox dayCard = new VBox(10);
            dayCard.getStyleClass().add("eval-data-card");
            dayCard.setMinWidth(240);
            dayCard.setPadding(new Insets(12));

            Label dayLabel = new Label(entry.getKey().toUpperCase());
            dayLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #7ec4ff; -fx-font-size: 14px; -fx-border-color: transparent transparent rgba(56,139,255,0.5) transparent; -fx-border-width: 0 0 2 0; -fx-padding: 0 0 4 0;");
            dayCard.getChildren().add(dayLabel);

            if (entry.getValue().isEmpty()) {
                Label empty = new Label("No classes");
                empty.setStyle("-fx-text-fill: rgba(150,190,255,0.4); -fx-font-style: italic;");
                dayCard.getChildren().add(empty);
            } else {
                for (Schedule s : entry.getValue()) {
                    VBox item = new VBox(2);
                    item.setPadding(new Insets(8));
                    item.setStyle("-fx-background-color: rgba(30,60,120,0.35); -fx-background-radius: 10; -fx-border-color: rgba(56,139,255,0.2); -fx-border-radius: 10; -fx-border-width: 1;");
                    String course = s.getCourse() == null ? "Course" : service.resolveCourseTitle(s.getCourse().getId());
                    String classe = s.getClasse() == null ? "Class" : service.resolveClasseName(s.getClasse().getId());
                    Label title = new Label(course + " (" + classe + ")");
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #d0e8ff; -fx-font-size: 11px;");
                    Label time = new Label("🕒 " + s.getStartTime() + " - " + s.getEndTime());
                    time.setStyle("-fx-text-fill: #90c8ff; -fx-font-size: 10px;");
                    item.getChildren().addAll(title, time);
                    dayCard.getChildren().add(item);
                }
            }
            scheduleCardsBox.getChildren().add(dayCard);
        }
    }

    // New method to refresh and display inquiries
    private void refreshInquiries() {
        if (inquiriesCardsBox == null) return;
        
        List<Reclamation> reclamations = service.getAllReclamations(); // Assuming a method to get all reclamations
        inquiriesCardsBox.getChildren().clear();
        
        if (reclamations.isEmpty()) {
            inquiriesCardsBox.getChildren().add(emptyCard("No student inquiries found."));
            return;
        }
        
        for (Reclamation r : reclamations) {
            inquiriesCardsBox.getChildren().add(createInquiryCard(r));
        }
    }

    // New method to create an inquiry card
    private VBox createInquiryCard(Reclamation r) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("eval-data-card");
        
        String studentName = r.getUser() == null ? "Unknown" : service.resolveUserDisplayName(r.getUser().getId());
        String courseName = r.getCourse() == null ? "General" : service.resolveCourseTitle(r.getCourse().getId());
        
        Label studentLabel = new Label(studentName + " | " + courseName);
        studentLabel.getStyleClass().add("eval-data-card-title");
        
        Label subjectLabel = new Label("Subject: " + safe(r.getSubject()));
        subjectLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d0e8ff;");
        
        Label descLabel = new Label(safe(r.getDescription()));
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
        
        Label statusLabel = new Label("Status: " + safe(r.getStatus()).toUpperCase());
        String statusColor = switch(safe(r.getStatus()).toLowerCase()) {
            case "resolved" -> "#10b981";
            case "rejected" -> "#ef4444";
            case "processing" -> "#f59e0b";
            default -> "#6366f1";
        };
        statusLabel.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold;");
        
        Button selectBtn = new Button("Respond to Inquiry");
        selectBtn.getStyleClass().add("eval-ghost-btn");
        selectBtn.setOnAction(e -> {
            selectedInquiryId = r.getId();
            selectedInquiryLabel.setText("Responding to: " + studentName + " (" + safe(r.getSubject()) + ")");
            inquiryResponseArea.setText(r.getAdminResponse() == null ? "" : r.getAdminResponse());
            if (r.getStatus() != null) {
                // Try to map status to the closest ComboBox item
                String norm = r.getStatus().substring(0,1).toUpperCase() + r.getStatus().substring(1).toLowerCase();
                if (inquiryStatusBox.getItems().contains(norm)) {
                    inquiryStatusBox.getSelectionModel().select(norm);
                }
            }
        });
        
        card.getChildren().addAll(studentLabel, subjectLabel, descLabel, statusLabel, selectBtn);
        return card;
    }

    private VBox createAssessmentCard(Assessment assessment) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("eval-data-card");

        Label titleLabel = new Label(safe(assessment.getTitle()));
        titleLabel.getStyleClass().add("eval-data-card-title");

        String courseName = assessment.getCourse() == null ? null : service.resolveCourseTitle(assessment.getCourse().getId());
        String classeName = assessment.getClasse() == null ? null : service.resolveClasseName(assessment.getClasse().getId());

        Label typeLabel = new Label("Type: " + safe(assessment.getType()));
        Label courseLabel = new Label("Course: " + safe(courseName) + " | Class: " + safe(classeName));
        Label dateLabel = new Label("Date: " + formatDate(assessment.getDate()) + " | Max Score: " + df.format(assessment.getMaxScore()));
        Label descriptionLabel = new Label(safe(assessment.getDescription()));
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");

        Button viewButton = new Button("Select Assessment");
        viewButton.getStyleClass().add("eval-ghost-btn");
        viewButton.setOnAction(event -> {
            selectedAssessmentId = assessment.getId();
            selectedAssessmentLabel.setText("Selected: " + safe(assessment.getTitle()));
            onLoadAssessmentGrades();
        });

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("eval-danger-btn");
        deleteButton.setOnAction(event -> {
            selectedAssessmentId = assessment.getId();
            onDeleteSelectedAssessment();
        });

        HBox actions = new HBox(8, viewButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(titleLabel, typeLabel, courseLabel, dateLabel, descriptionLabel, actions);
        return card;
    }

    private void renderGradesCards(List<Grade> grades) {
        gradesCardsBox.getChildren().clear();
        if (grades.isEmpty()) {
            gradesCardsBox.getChildren().add(emptyCard("No grades recorded for this assessment"));
            return;
        }
        for (Grade grade : grades) {
            gradesCardsBox.getChildren().add(createGradeCard(grade));
        }
    }

    private VBox createGradeCard(Grade grade) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("eval-data-card");

        String studentName = grade.getUserByStudentId() == null ? null : service.resolveUserDisplayName(grade.getUserByStudentId().getId());
        Label studentLabel = new Label(safe(studentName));
        studentLabel.getStyleClass().add("eval-data-card-title");

        Label scoreLabel = new Label("Grade Score: " + df.format(grade.getScore()));
        scoreLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0ea5e9;");
        Label commentLabel = new Label("Feedback: " + safe(grade.getComment()));
        commentLabel.setWrapText(true);

        card.getChildren().addAll(studentLabel, scoreLabel, commentLabel);
        return card;
    }

    private VBox emptyCard(String message) {
        VBox card = new VBox(8);
        card.getStyleClass().add("eval-empty-card");
        card.getChildren().add(new Label(message));
        return card;
    }

    private void loadForEdit(Assessment assessment) {
        assessmentTitleField.setText(safe(assessment.getTitle()));
        assessmentTypeBox.getSelectionModel().select(safe(assessment.getType()));
        assessmentMaxScoreField.setText(df.format(assessment.getMaxScore()));
        assessmentDescriptionArea.setText(safe(assessment.getDescription()));
        assessmentDatePicker.setValue(assessment.getDate() == null ? null : assessment.getDate().toLocalDateTime().toLocalDate());
        selectComboValue(assessmentCourseBox, assessment.getCourse() == null ? null : service.resolveCourseTitle(assessment.getCourse().getId()));
        selectComboValue(assessmentClassBox, assessment.getClasse() == null ? null : service.resolveClasseName(assessment.getClasse().getId()));
        selectComboValue(assessmentContenuBox, assessment.getContenu() == null ? null : service.resolveContenuTitle(assessment.getContenu().getId()));
        selectedAssessmentId = assessment.getId();
        selectedAssessmentLabel.setText("Selected: " + safe(assessment.getTitle()));
    }

    private void selectComboValue(ComboBox<String> comboBox, String value) {
        if (comboBox == null) return;
        if (value == null || value.isBlank()) {
            comboBox.getSelectionModel().clearSelection();
            return;
        }
        if (!comboBox.getItems().contains(value)) comboBox.getItems().add(value);
        comboBox.getSelectionModel().select(value);
    }

    private void sortAssessments(List<Assessment> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(a -> safeForSort(a.getTitle())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Assessment::getDate, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Assessment::getDate, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortGrades(List<Grade> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(g -> safeForSort(g.getUserByStudentId() == null ? null : service.resolveUserDisplayName(g.getUserByStudentId().getId()))));
            return;
        }
        rows.sort(Comparator.comparing(Grade::getScore, Comparator.nullsLast(Double::compareTo)).reversed());
    }

    private void sortSchedules(List<Schedule> rows, String mode) {
        rows.sort(Comparator.comparing(Schedule::getStartTime, Comparator.nullsLast(java.sql.Time::compareTo)));
    }

    private String selectedSort(ComboBox<String> comboBox) {
        return comboBox.getValue() == null ? "NEW" : comboBox.getValue().trim().toUpperCase(Locale.ROOT);
    }

    private void installInputValidation() {
        setLengthField(classNameField, 120);
        setLengthField(assessmentTitleField, 150);
        setLengthField(gradeCommentInputField, 220);
        setDecimalField(assessmentMaxScoreField);
        setDecimalField(gradeScoreInputField);
    }

    private void setLengthField(TextField field, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.length() <= maxLength ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setDecimalField(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,3}([.]\\d{0,2})?") ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private double requireDoubleInRange(String rawValue, String fieldName, double minInclusive, double maxInclusive) {
        String normalized = requireNotBlank(rawValue, fieldName);
        try {
            double parsed = Double.parseDouble(normalized);
            if (parsed < minInclusive || parsed > maxInclusive) {
                throw new IllegalArgumentException(fieldName + " must be between " + minInclusive + " and " + maxInclusive + ".");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private Integer optionalNameToId(String rawValue, String fieldName, java.util.function.Function<String, Integer> resolver) {
        if (rawValue == null || rawValue.trim().isEmpty()) return null;
        Integer id = resolver.apply(rawValue.trim());
        if (id == null) throw new IllegalArgumentException(fieldName + " not found.");
        return id;
    }

    private String requireNotBlank(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return rawValue.trim();
    }

    private String formatDate(Timestamp value) {
        return value == null ? "-" : value.toLocalDateTime().toLocalDate().toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeForSort(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showFeedback(String text, boolean isError) {
        feedbackLabel.setText(text);
        feedbackLabel.getStyleClass().removeAll("eval-feedback", "eval-feedback-error");
        if (isError) {
            feedbackLabel.getStyleClass().add("eval-feedback-error");
        } else {
            feedbackLabel.getStyleClass().add("eval-feedback");
        }
    }

    private boolean confirmDeletion(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Please confirm deletion");
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}
