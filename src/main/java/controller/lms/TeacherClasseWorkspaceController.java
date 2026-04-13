package controller.lms;

import dto.lms.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import service.lms.*;
import validation.LmsValidator;
import util.AppNavigator;

import java.net.URL;
import java.util.*;

/**
 * Teacher Class Workspace — integrated view matching the web app.
 *
 * Layout:
 *   LEFT:  Class Details card + My Module card
 *   RIGHT: Module Courses (expandable chapters with inline content)
 *          + Enrolled Students list
 *
 * Teachers create courses and content INLINE here via dialogs,
 * not through separate CRUD table pages.
 */
public class TeacherClasseWorkspaceController implements Initializable {

    // Top bar
    @FXML private Label breadcrumb, levelBadge, specialtyBadge, statusBadge;

    // Left panel
    @FXML private Label programLabel, studentsLabel, durationLabel;
    @FXML private VBox moduleInfoCard;
    @FXML private Label moduleInfoLabel, moduleDurationLabel, visibleCountLabel;

    // Module creation
    @FXML private VBox createModulePane;
    @FXML private TextField moduleNameField;
    @FXML private ComboBox<String> modulePeriodField;
    @FXML private Spinner<Integer> moduleDurationField;
    @FXML private Label createErrorLabel;

    // Workspace
    @FXML private VBox workspacePane, coursesContainer;
    @FXML private Label courseCountBadge;

    // Enrolled students
    @FXML private VBox studentsContainer;
    @FXML private Label noStudentsLabel;

    // Services
    private final TeacherAssignmentService taSvc = new TeacherAssignmentService();
    private final ClassDeliveryService cdSvc = new ClassDeliveryService();
    private final CourseService courseSvc = new CourseService();
    private final ContenuService contenuSvc = new ContenuService();
    private final EnrollmentService enrollSvc = new EnrollmentService();
    private final ClasseService classeSvc = new ClasseService();

    private TeacherAssignmentRowDto tc;
    private Integer classeModuleId; // cached for the current teacher's ClasseModule

    @Override
    public void initialize(URL u, ResourceBundle r) {
        modulePeriodField.setItems(FXCollections.observableArrayList("HOUR", "DAY", "WEEK", "MONTH"));
        modulePeriodField.setValue("WEEK");
        moduleDurationField.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
    }

    public void setTeacherClasse(TeacherAssignmentRowDto tc) {
        this.tc = tc;

        // Top bar
        breadcrumb.setText(tc.getClasseName());
        levelBadge.setText(tc.getClasseLevel() != null ? tc.getClasseLevel() : "");
        specialtyBadge.setText(tc.getClasseSpecialty() != null ? tc.getClasseSpecialty() : "");
        statusBadge.setText("ACTIVE");

        // Left panel — class details
        programLabel.setText("📁 Program: " + tc.getClasseProgram());
        long studentCount = classeSvc.countActiveStudents(tc.getClasseId());
        studentsLabel.setText("👥 Students: " + studentCount);
        durationLabel.setText("📅 Assigned Module: " + (tc.getModuleName() != null ? tc.getModuleName() : "—"));

        if ("Yes".equals(tc.getHasCreatedModule()) && tc.getModuleId() != null) {
            showWorkspace();
        } else {
            showCreateModule();
        }
    }

    // ==================== Module Creation ====================

    private void showCreateModule() {
        createModulePane.setVisible(true);
        createModulePane.setManaged(true);
        workspacePane.setVisible(false);
        workspacePane.setManaged(false);
        moduleInfoCard.setVisible(false);
        moduleInfoCard.setManaged(false);
    }

    @FXML private void onCreateModule() {
        createErrorLabel.setVisible(false);
        createErrorLabel.setManaged(false);
        List<String> errs = LmsValidator.validateModuleForm(
                moduleNameField.getText(), modulePeriodField.getValue(), moduleDurationField.getValue());
        if (!errs.isEmpty()) {
            createErrorLabel.setText(String.join("\n", errs));
            createErrorLabel.setVisible(true);
            createErrorLabel.setManaged(true);
            return;
        }
        try {
            taSvc.createModuleForAssignment(tc.getId(),
                    moduleNameField.getText(), modulePeriodField.getValue(), moduleDurationField.getValue());
            tc = taSvc.getTeachersForClasseDto(tc.getClasseId()).stream()
                    .filter(t -> t.getId().equals(tc.getId()))
                    .findFirst().orElseThrow();
            showWorkspace();
        } catch (SecurityException se) {
            createErrorLabel.setText(se.getMessage());
            createErrorLabel.setVisible(true); createErrorLabel.setManaged(true);
        } catch (Exception e) {
            createErrorLabel.setText(e.getMessage());
            createErrorLabel.setVisible(true); createErrorLabel.setManaged(true);
        }
    }

    // ==================== Workspace ====================

    private void showWorkspace() {
        createModulePane.setVisible(false);
        createModulePane.setManaged(false);
        workspacePane.setVisible(true);
        workspacePane.setManaged(true);
        moduleInfoCard.setVisible(true);
        moduleInfoCard.setManaged(true);

        // Module info
        moduleInfoLabel.setText(tc.getModuleName());
        moduleDurationLabel.setText("Duration: " + tc.getModuleDurationLabel());

        // Resolve classeModuleId
        var classeModules = cdSvc.getModulesForClasseDto(tc.getClasseId());
        var cm = classeModules.stream()
                .filter(m -> m.getModuleId().equals(tc.getModuleId()))
                .findFirst().orElse(null);
        classeModuleId = cm != null ? cm.getClasseModuleId() : null;

        loadCourses();
        loadStudents();
    }

    // ==================== Course Management ====================

    @FXML private void onAddCourse() {
        // Dialog: create a new course (chapitre) inline
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add Course");
        dialog.setHeaderText("Create a new course chapter");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Course title (e.g. ch1, Chapter 1...)");
        titleField.getStyleClass().add("lms-input");

        VBox content = new VBox(8,
                new Label("Course Title:"),
                titleField
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? titleField.getText() : null);

        dialog.showAndWait().ifPresent(title -> {
            if (title == null || title.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Course title cannot be empty.").showAndWait();
                return;
            }
            try {
                // 1. Create the course record
                var course = courseSvc.createCourse(title);
                // 2. Link it to this teacher's class module
                if (classeModuleId != null) {
                    cdSvc.addCourseToClasseModule(classeModuleId, course.getId());
                }
                loadCourses();
            } catch (SecurityException se) {
                new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            }
        });
    }

    private void loadCourses() {
        coursesContainer.getChildren().clear();
        if (classeModuleId == null) return;

        List<CourseRowDto> courses = cdSvc.getCoursesForClasseModuleDto(classeModuleId);
        courseCountBadge.setText(courses.size() + " COURSES");

        // Update visible count
        long visibleCount = courses.stream().filter(c -> !c.isHidden()).count();
        visibleCountLabel.setText("Courses visible: " + visibleCount + "/" + courses.size());

        for (CourseRowDto cc : courses) {
            coursesContainer.getChildren().add(buildCourseCard(cc));
        }
    }

    private VBox buildCourseCard(CourseRowDto cc) {
        VBox card = new VBox(0);
        card.getStyleClass().add("lms-card");
        card.setPadding(new Insets(0));

        // === Course Header (collapsible toggle) ===
        HBox header = new HBox(8);
        header.setPadding(new Insets(12));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-cursor: hand;");

        Label visBadge = new Label(cc.isHidden() ? "HIDDEN" : "VISIBLE");
        visBadge.getStyleClass().addAll("badge", cc.isHidden() ? "badge-inactive" : "badge-active");

        Label title = new Label(cc.getTitle());
        title.getStyleClass().add("card-title");

        // Count content items
        List<ContenuRowDto> contents = cdSvc.getContenuForClasseCourseDto(cc.getClasseCourseId());
        Label itemCount = new Label("(" + contents.size() + " items)");
        itemCount.getStyleClass().add("card-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label arrow = new Label("▼");
        arrow.getStyleClass().add("card-text");

        header.getChildren().addAll(visBadge, title, itemCount, spacer, arrow);

        // === Expandable Content Body ===
        VBox body = new VBox(8);
        body.setPadding(new Insets(0, 12, 12, 12));
        body.setVisible(false);
        body.setManaged(false);

        // Toggle expand/collapse
        header.setOnMouseClicked(e -> {
            boolean show = !body.isVisible();
            body.setVisible(show);
            body.setManaged(show);
            arrow.setText(show ? "▲" : "▼");
        });

        // --- Action buttons row ---
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button addContentBtn = new Button("+ Add Content");
        addContentBtn.getStyleClass().add("ghost-button");
        addContentBtn.setOnAction(e -> showAddContentDialog(cc, "COURS"));

        Button addQuizBtn = new Button("+ Add Quiz");
        addQuizBtn.getStyleClass().add("ghost-button");
        addQuizBtn.setOnAction(e -> showAddContentDialog(cc, "QUIZ"));

        Button hideBtn = new Button(cc.isHidden() ? "Show" : "Hide");
        hideBtn.getStyleClass().add("ghost-button");
        hideBtn.setOnAction(e -> {
            try {
                cdSvc.toggleCourseVisibility(cc.getClasseCourseId());
                loadCourses();
            } catch (SecurityException se) {
                new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> {
            new Alert(Alert.AlertType.CONFIRMATION, "Delete this course and all its content?",
                    ButtonType.YES, ButtonType.NO).showAndWait().ifPresent(b -> {
                if (b == ButtonType.YES) {
                    try {
                        cdSvc.deleteCourseFromClasse(cc.getClasseCourseId());
                        loadCourses();
                    } catch (SecurityException se) {
                        new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    }
                }
            });
        });

        actions.getChildren().addAll(addContentBtn, addQuizBtn, hideBtn, actionSpacer, deleteBtn);
        body.getChildren().add(actions);
        body.getChildren().add(new Separator());

        // --- Content items ---
        for (ContenuRowDto cx : contents) {
            body.getChildren().add(buildContentRow(cx));
        }

        if (contents.isEmpty()) {
            Label empty = new Label("No content yet. Use the buttons above to add content.");
            empty.getStyleClass().add("card-text");
            empty.setStyle("-fx-text-fill: #888;");
            body.getChildren().add(empty);
        }

        card.getChildren().addAll(header, body);
        return card;
    }

    private HBox buildContentRow(ContenuRowDto cx) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 8));
        row.setStyle("-fx-border-color: transparent transparent -fx-base transparent; -fx-border-width: 0 0 1 0;");

        // Icon
        Label icon = new Label(cx.getType().equalsIgnoreCase("QUIZ") ? "❓" : "📄");

        Label title = new Label(cx.getTitle());
        title.getStyleClass().add("card-text");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.SOMETIMES);

        Label typeBadge = new Label(cx.getType());
        typeBadge.getStyleClass().addAll("badge", "badge-" + cx.getType().toLowerCase());

        Label visBadge = new Label(cx.isHidden() ? "HIDDEN" : "VISIBLE");
        visBadge.getStyleClass().addAll("badge", cx.isHidden() ? "badge-inactive" : "badge-active");

        // Toggle visibility
        Button togBtn = new Button(cx.isHidden() ? "👁" : "🙈");
        togBtn.getStyleClass().add("icon-button");
        togBtn.setTooltip(new Tooltip(cx.isHidden() ? "Show to students" : "Hide from students"));
        togBtn.setOnAction(e -> {
            try {
                cdSvc.toggleContenuVisibility(cx.getClasseContenuId());
                loadCourses();
            } catch (SecurityException se) {
                new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        // Delete
        Button delBtn = new Button("🗑");
        delBtn.getStyleClass().add("icon-button");
        delBtn.setStyle("-fx-text-fill: #e74c3c;");
        delBtn.setTooltip(new Tooltip("Remove content"));
        delBtn.setOnAction(e -> {
            try {
                cdSvc.deleteContenuFromClasse(cx.getClasseContenuId());
                loadCourses();
            } catch (SecurityException se) {
                new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        row.getChildren().addAll(icon, title, typeBadge, visBadge, togBtn, delBtn);
        return row;
    }

    // ==================== Add Content Dialog ====================

    private void showAddContentDialog(CourseRowDto cc, String defaultType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Content");
        dialog.setHeaderText("Create new content for: " + cc.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Content title");
        titleField.getStyleClass().add("lms-input");

        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(
                "COURS", "QUIZ", "TD", "TP", "EXAM", "VIDEO", "RESOURCE"));
        typeBox.setValue(defaultType);

        // --- File Upload ---
        Label fileLabel = new Label("No file selected.");
        Button fileBtn = new Button("Choose File");
        fileBtn.getStyleClass().add("ghost-button");
        final java.io.File[] selectedFile = new java.io.File[1];
        fileBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select Content File");
            java.io.File f = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                selectedFile[0] = f;
                fileLabel.setText(f.getName());
            }
        });
        HBox fileBox = new HBox(8, fileBtn, fileLabel);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8,
                new Label("Title:"), titleField,
                new Label("Type:"), typeBox,
                new Label("Attachment (Optional):"), fileBox
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String t = titleField.getText();
            String type = typeBox.getValue();
            if (t == null || t.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Content title cannot be empty.").showAndWait();
                return;
            }
            try {
                String storedName = null;
                String fileType = null;
                Integer fileSize = null;
                
                if (selectedFile[0] != null) {
                    FileUploadService fus = new FileUploadService();
                    storedName = fus.saveFile(selectedFile[0], selectedFile[0].getName());
                    
                    String name = selectedFile[0].getName();
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot > 0 && lastDot < name.length() - 1) {
                        fileType = name.substring(lastDot + 1).toLowerCase();
                    } else {
                        fileType = "unknown";
                    }
                    fileSize = (int) selectedFile[0].length();
                }
                
                // 1. Create the contenu record
                var contenu = contenuSvc.createContenu(t.trim(), type, true, storedName, fileType, fileSize);
                // 2. Link it to this ClasseCourse
                cdSvc.addContenuToClasseCourse(cc.getClasseCourseId(), contenu.getId());
                loadCourses();
            } catch (SecurityException se) {
                new Alert(Alert.AlertType.WARNING, se.getMessage()).showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            }
        });
    }

    // ==================== Enrolled Students ====================

    private void loadStudents() {
        studentsContainer.getChildren().clear();
        List<StudentEnrollmentRowDto> students = enrollSvc.getStudentsForClasseDto(tc.getClasseId());

        if (students.isEmpty()) {
            noStudentsLabel.setVisible(true);
            noStudentsLabel.setManaged(true);
            return;
        }
        noStudentsLabel.setVisible(false);
        noStudentsLabel.setManaged(false);

        // Header row
        HBox headerRow = new HBox(12);
        headerRow.setPadding(new Insets(6, 8, 6, 8));
        Label h1 = new Label("STUDENT"); h1.getStyleClass().add("form-label"); h1.setPrefWidth(200);
        Label h2 = new Label("ENROLLED AT"); h2.getStyleClass().add("form-label"); h2.setPrefWidth(150);
        Label h3 = new Label("STATUS"); h3.getStyleClass().add("form-label"); h3.setPrefWidth(80);
        headerRow.getChildren().addAll(h1, h2, h3);
        studentsContainer.getChildren().add(headerRow);
        studentsContainer.getChildren().add(new Separator());

        for (StudentEnrollmentRowDto s : students) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8));

            // Avatar circle
            Label avatar = new Label("S");
            avatar.setStyle("-fx-background-color: #26c6da; -fx-text-fill: white; -fx-background-radius: 50; " +
                    "-fx-min-width: 32; -fx-min-height: 32; -fx-alignment: center; -fx-font-weight: bold;");

            VBox nameBox = new VBox(2);
            Label email = new Label(s.getEmail());
            email.getStyleClass().add("card-text");
            email.setStyle("-fx-font-weight: bold;");
            nameBox.getChildren().add(email);
            nameBox.setPrefWidth(180);

            Label enrolledAt = new Label(s.getEnrolledAt());
            enrolledAt.getStyleClass().add("card-text");
            enrolledAt.setPrefWidth(150);

            Label statusLabel = new Label(s.getActive());
            statusLabel.getStyleClass().addAll("badge", "Yes".equals(s.getActive()) ? "badge-active" : "badge-inactive");

            row.getChildren().addAll(avatar, nameBox, enrolledAt, statusLabel);
            studentsContainer.getChildren().add(row);
        }

        // Update left panel student count
        long activeCount = students.stream().filter(s -> "Yes".equals(s.getActive())).count();
        studentsLabel.setText("👥 Students: " + activeCount + "/" + students.size());
    }

    @FXML private void onBack() {
        AppNavigator.showTeacherClasses();
    }
}
